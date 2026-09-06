package cn.vonce.sql.cache;

import java.util.Set;

/**
 * 基于 Redis 的分布式查询缓存实现（兼容 Redis）。
 * <p>核心不依赖任何 Redis 客户端，由调用方通过 {@link RedisOps} 注入自己的 Redis 封装，
 * 通过 {@link CacheSerializer} 控制值的序列化方式（默认 JDK，可换 JSON）。
 * 跨节点一致：任意节点的写操作都会 {@link #evictByTable(String, String, Object, String)}，即时失效其他节点的缓存。</p>
 * <p><b>使用注意</b>：
 * <ul>
 *   <li>默认 {@link JdkCacheSerializer} 要求被缓存的实体（及其所有嵌套对象）实现
 *       {@link java.io.Serializable}，否则写入时抛 {@link java.io.NotSerializableException}；
 *       若实体不便实现 Serializable，请注入 JSON 序列化器（或自定义 {@link CacheSerializer}）。</li>
 *   <li>{@link #clear()} 故意抛出 {@link UnsupportedOperationException}：Redis 全局清空风险极高，
 *       可能误删其它业务数据，请改用 SCAN + DEL 或 FLUSHDB 等可控方式。</li>
 * </ul>
 *
 * @author Jovi
 * @version 1.1
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
            // 失效索引键含 schema 与 dataSource，与本地实现保持一致，避免跨 schema / 跨数据源同表名被过度失效
            String indexKey = "flexsql:tbl:" + table + "@" + (key.getSchema() == null ? "" : key.getSchema())
                    + "@" + (tenantId == null ? "" : tenantId) + "@" + (key.getDataSource() == null ? "" : key.getDataSource());
            redisOps.sadd(indexKey, storeKey);
        }
    }

    @Override
    public void evictByTable(String table, String schema, Object tenantId, String dataSource) {
        if (table == null) {
            return;
        }
        String indexKey = "flexsql:tbl:" + table + "@" + (schema == null ? "" : schema)
                + "@" + (tenantId == null ? "" : tenantId) + "@" + (dataSource == null ? "" : dataSource);
        Set<String> members = redisOps.smembers(indexKey);
        if (members != null) {
            for (String member : members) {
                redisOps.delete(member);
                redisOps.srem(indexKey, member);
            }
        }
        redisOps.delete(indexKey);
    }

    /**
     * 单 key 失效。仅删除 Redis 中的 store key；表反向索引里的成员残留一个失效引用，
     * 下次 evictByTable 会尝试 DEL 一个不存在的 key（Redis DEL 0 条不报错，作为可接受代价，
     * 保证单 key 失效路径保持单次 RTT）。
     */
    @Override
    public void evict(QueryCacheKey key) {
        if (key == null) {
            return;
        }
        redisOps.delete(key.toStoreKey());
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
