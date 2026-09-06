package cn.vonce.sql.cache.caffeine;

import cn.vonce.sql.cache.QueryCache;
import cn.vonce.sql.cache.QueryCacheConfig;
import cn.vonce.sql.cache.QueryCacheFactory;
import cn.vonce.sql.cache.QueryCacheKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Caffeine 的本地查询缓存实现（可选模块，核心零依赖）。
 * <p>与内置 {@link cn.vonce.sql.cache.SimpleQueryCache} 语义一致：本地进程内缓存，
 * <b>仅限单机部署</b>；分布式部署请使用 {@link cn.vonce.sql.cache.RedisQueryCache}
 * （本类是进程内缓存，多节点各自一份，写节点无法失效其它节点，会产生跨节点脏读）。</p>
 *
 * <p>相比 SimpleQueryCache 的差异：
 * <ul>
 *   <li>使用 Caffeine 的 W-TinyLFU 淘汰策略，命中率更高、并发性能更好；</li>
 *   <li>过期/淘汰由 Caffeine 内部维护（无额外清扫线程），容量与 TTL 均按需生效；</li>
 *   <li>写入 TTL 同样带 ±10% 抖动，避免大量 key 同时过期造成缓存雪崩。</li>
 * </ul></p>
 *
 * <p>两种用法（二选一）：
 * <pre>{@code
 * // 1) 显式指定实现（推荐，最直观）
 * SqlBeanServices.setCacheConfig(QueryCacheConfig.custom(new CaffeineQueryCache(1000, 600)));
 *
 * // 2) 注册为 LOCAL 模式的实现，之后 local(...) 产出 Caffeine
 * CaffeineQueryCache.install();
 * SqlBeanServices.setCacheConfig(QueryCacheConfig.local(1000, 600, 0));
 * }</pre></p>
 *
 * <p>失效索引键形如 {@code table@schema@tenant@dataSource}，与核心其它实现保持一致，
 * 因此不同动态 schema / 不同数据源下同名表互不影响（仅多清、绝不少清）。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public class CaffeineQueryCache implements QueryCache {

    /**
     * 本地缓存工厂：注册给 {@link QueryCacheConfig#setLocalCacheFactory(QueryCacheFactory)} 后，
     * {@link QueryCacheConfig#local(long, long, long)} 即产出 Caffeine 实现。
     */
    public static final QueryCacheFactory FACTORY = CaffeineQueryCache::new;

    private final Cache<QueryCacheKey, Object> cache;
    private final ConcurrentHashMap<String, Set<QueryCacheKey>> index = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<QueryCacheKey, String> compositeOf = new ConcurrentHashMap<>();

    /** 无过期时间的本地缓存（仅受容量限制）。 */
    public CaffeineQueryCache(long maximumSize) {
        this(maximumSize, 0L, 0L);
    }

    /** 仅写入后过期。 */
    public CaffeineQueryCache(long maximumSize, long expireAfterWriteSeconds) {
        this(maximumSize, expireAfterWriteSeconds, 0L);
    }

    /**
     * @param maximumSize              容量上限（条目数），&lt;=0 时按 1 处理
     * @param expireAfterWriteSeconds  写入后过期秒数，0 表示不按写入过期
     * @param expireAfterAccessSeconds 访问后过期秒数，0 表示不按访问过期
     */
    public CaffeineQueryCache(long maximumSize, long expireAfterWriteSeconds, long expireAfterAccessSeconds) {
        long ttlWriteNanos = toNanos(expireAfterWriteSeconds);
        long ttlAccessNanos = toNanos(expireAfterAccessSeconds);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Caffeine<QueryCacheKey, Object> builder = (Caffeine) Caffeine.newBuilder();
        builder = builder.maximumSize(Math.max(1, maximumSize));
        if (ttlWriteNanos > 0 || ttlAccessNanos > 0) {
            builder = builder.expireAfter(new JitterExpiry(ttlWriteNanos, ttlAccessNanos));
        }
        this.cache = builder.removalListener(this::onRemoved).build();
    }

    /**
     * 注册为 {@link cn.vonce.sql.cache.CacheMode#LOCAL} 模式的实现。
     * 调用后 {@link QueryCacheConfig#local(long, long, long)} 将产出本实现。
     */
    public static void install() {
        QueryCacheConfig.setLocalCacheFactory(FACTORY);
    }

    /** 注销本地缓存工厂，恢复为内置 SimpleQueryCache。 */
    public static void uninstall() {
        QueryCacheConfig.setLocalCacheFactory(null);
    }

    private void onRemoved(QueryCacheKey key, Object value, RemovalCause cause) {
        unindex(key);
    }

    @Override
    public Object get(QueryCacheKey key) {
        if (key == null) {
            return null;
        }
        return cache.getIfPresent(key);
    }

    @Override
    public void put(QueryCacheKey key, Object value, String table, Object tenantId) {
        if (key == null) {
            return;
        }
        String composite = compositeKey(table, key.getSchema(), tenantId, key.getDataSource());
        compositeOf.put(key, composite);
        index.computeIfAbsent(composite, k -> ConcurrentHashMap.newKeySet()).add(key);
        cache.put(key, value);
    }

    @Override
    public void evictByTable(String table, String schema, Object tenantId, String dataSource) {
        if (table == null) {
            return;
        }
        Set<QueryCacheKey> keys = index.get(compositeKey(table, schema, tenantId, dataSource));
        if (keys != null && !keys.isEmpty()) {
            cache.invalidateAll(new ArrayList<>(keys));
        }
    }

    @Override
    public void clear() {
        cache.invalidateAll();
        index.clear();
        compositeOf.clear();
    }

    /** 当前缓存中的条目估算数（便于监控与测试）。 */
    public long estimatedSize() {
        return cache.estimatedSize();
    }

    /** 强制执行一次待处理的淘汰/过期（便于测试与运维）。 */
    public void cleanUp() {
        cache.cleanUp();
    }

    private void unindex(QueryCacheKey key) {
        if (key == null) {
            return;
        }
        String composite = compositeOf.remove(key);
        if (composite == null) {
            return;
        }
        Set<QueryCacheKey> keys = index.get(composite);
        if (keys != null) {
            keys.remove(key);
            if (keys.isEmpty()) {
                index.remove(composite, keys);
            }
        }
    }

    private static String compositeKey(String table, String schema, Object tenantId, String dataSource) {
        return (table == null ? "" : table) + "@"
                + (schema == null ? "" : schema) + "@"
                + (tenantId == null ? "" : tenantId) + "@"
                + (dataSource == null ? "" : dataSource);
    }

    private static long toNanos(long seconds) {
        return seconds > 0 ? TimeUnit.SECONDS.toNanos(seconds) : 0L;
    }

    /**
     * 可变过期策略：写入 TTL 带抖动防雪崩；访问 TTL 生效时取「剩余写入时间」与「访问 TTL」的较小值，
     * 保证写入 TTL 始终作为硬上限（与 SimpleQueryCache 语义一致）。
     */
    private static final class JitterExpiry implements Expiry<QueryCacheKey, Object> {

        private final long ttlWriteNanos;
        private final long ttlAccessNanos;

        JitterExpiry(long ttlWriteNanos, long ttlAccessNanos) {
            this.ttlWriteNanos = ttlWriteNanos;
            this.ttlAccessNanos = ttlAccessNanos;
        }

        @Override
        public long expireAfterCreate(QueryCacheKey key, Object value, long currentTime) {
            long write = ttlWriteNanos > 0 ? withJitter(ttlWriteNanos) : Long.MAX_VALUE;
            return Math.min(write, ttlAccessNanos > 0 ? ttlAccessNanos : Long.MAX_VALUE);
        }

        @Override
        public long expireAfterUpdate(QueryCacheKey key, Object value, long currentTime, long currentDuration) {
            return expireAfterCreate(key, value, currentTime);
        }

        @Override
        public long expireAfterRead(QueryCacheKey key, Object value, long currentTime, long currentDuration) {
            long remaining = currentDuration > 0 ? currentDuration : Long.MAX_VALUE;
            return Math.min(remaining, ttlAccessNanos > 0 ? ttlAccessNanos : Long.MAX_VALUE);
        }

        private static long withJitter(long nanos) {
            long span = nanos / 10;
            if (span <= 0) {
                return nanos;
            }
            return nanos + ThreadLocalRandom.current().nextLong(0, span);
        }
    }
}
