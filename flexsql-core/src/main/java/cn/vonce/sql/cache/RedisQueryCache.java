package cn.vonce.sql.cache;

import java.util.Set;

/**
 * 基于 Redis 的分布式查询缓存实现（兼容 Redis）。
 * <p>核心不依赖任何 Redis 客户端，由调用方通过 {@link RedisOps} 注入自己的 Redis 封装，
 * 通过 {@link CacheSerializer} 控制值的序列化方式（默认 JDK，可换 JSON）。
 * 跨节点一致：任意节点的写操作都会 {@link #evictByTable(String, Object)}，即时失效其他节点的缓存。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public class RedisQueryCache implements QueryCache {

    private final RedisOps redisOps;
    private final CacheSerializer serializer;
    private final long ttlMillis;

    public RedisQueryCache(RedisOps redisOps, CacheSerializer serializer, long expireAfterWriteSeconds) {
        this.redisOps = redisOps;
        this.serializer = serializer == null ? new JdkCacheSerializer() : serializer;
        this.ttlMillis = expireAfterWriteSeconds * 1000L;
    }

    public RedisQueryCache(RedisOps redisOps, long expireAfterWriteSeconds) {
        this(redisOps, new JdkCacheSerializer(), expireAfterWriteSeconds);
    }

    @Override
    public Object get(QueryCacheKey key) {
        byte[] bytes = redisOps.get(key.toStoreKey());
        return serializer.deserialize(bytes);
    }

    @Override
    public void put(QueryCacheKey key, Object value, String table, Object tenantId) {
        String storeKey = key.toStoreKey();
        redisOps.set(storeKey, serializer.serialize(value), ttlMillis);
        if (table != null) {
            String indexKey = "flexsql:tbl:" + table + "@" + (tenantId == null ? "" : tenantId);
            redisOps.sadd(indexKey, storeKey);
        }
    }

    @Override
    public void evictByTable(String table, Object tenantId) {
        if (table == null) {
            return;
        }
        String indexKey = "flexsql:tbl:" + table + "@" + (tenantId == null ? "" : tenantId);
        Set<String> members = redisOps.smembers(indexKey);
        if (members != null) {
            for (String member : members) {
                redisOps.delete(member);
                redisOps.srem(indexKey, member);
            }
        }
        redisOps.delete(indexKey);
    }

    @Override
    public void clear() {
        // Redis 全局清空风险高，交由调用方按业务前缀处理；此处不实现。
        throw new UnsupportedOperationException("Redis 缓存清空请使用 Redis 的 SCAN+DEL 或 FLUSHDB，避免误清其它数据");
    }

    @Override
    public boolean isDistributed() {
        return true;
    }
}
