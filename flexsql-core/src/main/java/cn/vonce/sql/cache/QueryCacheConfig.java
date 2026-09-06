package cn.vonce.sql.cache;

/**
 * 查询缓存全局激活配置（取代原先 per-service 的 SqlBeanConfig 缓存开关）。
 * <p>三种模式互斥二选一，默认 {@link CacheMode#OFF}（不开启）：
 * <ul>
 *   <li>{@link #off()}：关闭，caching() 原样返回 service。</li>
 *   <li>{@link #local(long, long, long)}：单节点本地缓存（内置 SimpleQueryCache）。仅限单机部署。</li>
 *   <li>{@link #redis(RedisOps, CacheSerializer, long)} / {@link #redis(RedisOps, long)}：分布式 Redis 缓存，用于多节点部署。</li>
 * </ul>
 * 由后端（Spring/Solon）在启动时通过 {@code SqlBeanServices.setCacheConfig(...)} 注入；
 * 核心不依赖任何具体缓存库，也不强制用户选用某一种实现。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public final class QueryCacheConfig {

    private final CacheMode mode;
    private final QueryCache cache;

    private QueryCacheConfig(CacheMode mode, QueryCache cache) {
        this.mode = mode;
        this.cache = cache;
    }

    /** 关闭缓存（默认）。 */
    public static QueryCacheConfig off() {
        return new QueryCacheConfig(CacheMode.OFF, null);
    }

    /** 本地缓存模式（仅限单机）。maxSize 容量上限；expireAfterWrite/AcessSeconds 为 TTL（秒）。 */
    public static QueryCacheConfig local(long maximumSize, long expireAfterWriteSeconds, long expireAfterAccessSeconds) {
        return new QueryCacheConfig(CacheMode.LOCAL, new SimpleQueryCache(maximumSize, expireAfterWriteSeconds, expireAfterAccessSeconds));
    }

    /** 分布式 Redis 缓存（自定义序列化器）。 */
    public static QueryCacheConfig redis(RedisOps redisOps, CacheSerializer serializer, long expireAfterWriteSeconds) {
        return new QueryCacheConfig(CacheMode.REDIS, new RedisQueryCache(redisOps, serializer, expireAfterWriteSeconds));
    }

    /** 分布式 Redis 缓存（默认 JDK 序列化器，要求实体可序列化）。 */
    public static QueryCacheConfig redis(RedisOps redisOps, long expireAfterWriteSeconds) {
        return new QueryCacheConfig(CacheMode.REDIS, new RedisQueryCache(redisOps, expireAfterWriteSeconds));
    }

    public CacheMode getMode() {
        return mode;
    }

    public QueryCache getCache() {
        return cache;
    }
}
