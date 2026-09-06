package cn.vonce.sql.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 零依赖的本地查询缓存默认实现（不依赖 Caffeine）。
 * <p>使用带访问顺序的 {@link LinkedHashMap} 实现容量上限（LRU 淘汰）+ TTL 过期定时清理。
 * 当前作为 FlexSQL 查询缓存的内置默认后端；若后续运行时存在 Caffeine，可新增对应实现并在工厂中优先选用。</p>
 * <p>失效索引键形如 {@code table@schema@tenant@dataSource}，因此不同动态 schema / 不同数据源下同名表互不影响
 * （仅多清、绝不少清）。</p>
 *
 * @author Jovi
 * @version 1.1
 */
public class SimpleQueryCache implements QueryCache {

    private final long maximumSize;
    private final long ttlWriteMillis;
    private final long ttlAccessMillis;
    private final Map<QueryCacheKey, Entry> store;
    private final Map<String, Set<QueryCacheKey>> index = new ConcurrentHashMap<>();
    private final Map<QueryCacheKey, String> compositeOf = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sweeper;

    @SuppressWarnings("serial")
    public SimpleQueryCache(long maximumSize, long expireAfterWriteSeconds) {
        this(maximumSize, expireAfterWriteSeconds, 0L);
    }

    @SuppressWarnings("serial")
    public SimpleQueryCache(long maximumSize, long expireAfterWriteSeconds, long expireAfterAccessSeconds) {
        this.maximumSize = Math.max(1, maximumSize);
        this.ttlWriteMillis = expireAfterWriteSeconds * 1000L;
        this.ttlAccessMillis = Math.max(0, expireAfterAccessSeconds) * 1000L;
        this.store = Collections.synchronizedMap(new LinkedHashMap<QueryCacheKey, Entry>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<QueryCacheKey, Entry> eldest) {
                return size() > SimpleQueryCache.this.maximumSize;
            }
        });
        this.sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "flexsql-query-cache-sweeper");
            t.setDaemon(true);
            return t;
        });
        if (ttlWriteMillis > 0 || ttlAccessMillis > 0) {
            this.sweeper.scheduleAtFixedRate(this::sweep, 30, 30, TimeUnit.SECONDS);
        }
    }

    private static final class Entry {
        final Object value;
        final long expireAt;
        volatile long lastAccess;

        Entry(Object value, long ttlWriteMillis, long ttlAccessMillis) {
            this.value = value;
            long now = System.currentTimeMillis();
            long jitter = 0L;
            // 雪崩抖动：写入过期时间附加 ±10% 随机抖动，避免大量 key 在同一时刻集中过期冲击 DB
            if (ttlWriteMillis > 0) {
                long span = ttlWriteMillis / 10;
                if (span > 0) {
                    jitter = ThreadLocalRandom.current().nextLong(0, span);
                }
            }
            this.expireAt = ttlWriteMillis > 0 ? now + ttlWriteMillis + jitter : Long.MAX_VALUE;
            this.lastAccess = now;
        }

        boolean expired(long now, long ttlAccessMillis) {
            if (now >= expireAt) {
                return true;
            }
            return ttlAccessMillis > 0 && (now - lastAccess) > ttlAccessMillis;
        }
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        for (Map.Entry<QueryCacheKey, Entry> e : new ArrayList<>(store.entrySet())) {
            if (e.getValue().expired(now, ttlAccessMillis)) {
                remove(e.getKey());
            }
        }
    }

    @Override
    public Object get(QueryCacheKey key) {
        Entry e = store.get(key);
        if (e == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (e.expired(now, ttlAccessMillis)) {
            remove(key);
            return null;
        }
        e.lastAccess = now;
        return e.value;
    }

    @Override
    public void put(QueryCacheKey key, Object value, String table, Object tenantId) {
        String schema = key.getSchema();
        String dataSource = key.getDataSource();
        String composite = compositeKey(table, schema, tenantId, dataSource);
        compositeOf.put(key, composite);
        index.computeIfAbsent(composite, k -> ConcurrentHashMap.newKeySet()).add(key);
        store.put(key, new Entry(value, ttlWriteMillis, ttlAccessMillis));
    }

    @Override
    public void evictByTable(String table, String schema, Object tenantId, String dataSource) {
        if (table == null) {
            return;
        }
        String composite = compositeKey(table, schema, tenantId, dataSource);
        Set<QueryCacheKey> set = index.get(composite);
        if (set != null) {
            for (QueryCacheKey key : new ArrayList<>(set)) {
                remove(key);
            }
        }
    }

    /**
     * 单 key 失效。复用 {@link #remove(QueryCacheKey)}：从 store、composite 索引
     * 两个数据结构同时清理，确保 {@link IndexedQueryCache} 按主键反向失效后无悬空引用。
     */
    @Override
    public void evict(QueryCacheKey key) {
        if (key == null) {
            return;
        }
        remove(key);
    }

    private static String compositeKey(String table, String schema, Object tenantId, String dataSource) {
        return (table == null ? "" : table) + "@"
                + (schema == null ? "" : schema) + "@"
                + (tenantId == null ? "" : tenantId) + "@"
                + (dataSource == null ? "" : dataSource);
    }

    private void remove(QueryCacheKey key) {
        store.remove(key);
        String composite = compositeOf.remove(key);
        if (composite != null) {
            Set<QueryCacheKey> set = index.get(composite);
            if (set != null) {
                set.remove(key);
                if (set.isEmpty()) {
                    index.remove(composite, set);
                }
            }
        }
    }

    @Override
    public void clear() {
        store.clear();
        index.clear();
        compositeOf.clear();
    }
}
