package cn.vonce.sql.cache;

import cn.vonce.sql.config.CacheMode;

/**
 * 查询缓存全局激活配置（取代原先 per-service 的 SqlBeanConfig 缓存开关）。
 * <p>模式互斥、默认 {@link CacheMode#OFF}（不开启）：
 * <ul>
 *   <li>{@link #off()}：关闭，caching() 原样返回 service。</li>
 *   <li>{@link #local(long, long, long)}：单节点本地缓存。默认内置 SimpleQueryCache（零依赖）；
 *       可通过 {@link #setLocalCacheFactory(QueryCacheFactory)} 换成 Caffeine 等其它本地实现。</li>
 *   <li>{@link #redis(RedisOps, CacheSerializer, long)} / {@link #redis(RedisOps, long)}：分布式 Redis 缓存，用于多节点部署。</li>
 *   <li>{@link #custom(QueryCache)}：直接指定任意 {@link QueryCache} 实现（如 Caffeine 模块）。</li>
 * </ul>
 * 由后端（Spring/Solon）在启动时通过 {@code SqlBeanServices.setCacheConfig(...)} 注入；
 * 核心不依赖任何具体缓存库，也不强制用户选用某一种实现。</p>
 *
 * @author Jovi
 * @version 1.1
 */
public final class QueryCacheConfig {

    /** 可插拔的本地缓存实现工厂；null 表示使用内置 SimpleQueryCache。 */
    private static volatile QueryCacheFactory localCacheFactory = null;

    private final CacheMode mode;
    private final QueryCache cache;

    private QueryCacheConfig(CacheMode mode, QueryCache cache) {
        this.mode = mode;
        this.cache = cache;
    }

    /**
     * 注册本地缓存实现工厂（可选）。
     * <p>注册后 {@link #local(long, long, long)} 将产出该工厂创建的实例；
     * 传 null 恢复为内置 {@link SimpleQueryCache}。
     * 典型用法：可选模块（如 flexsql-caffeine）在启动时调用一次。</p>
     */
    public static void setLocalCacheFactory(QueryCacheFactory factory) {
        localCacheFactory = factory;
    }

    /** 当前注册的本地缓存实现工厂，未注册返回 null。 */
    public static QueryCacheFactory getLocalCacheFactory() {
        return localCacheFactory;
    }

    /** 关闭缓存（默认）。 */
    public static QueryCacheConfig off() {
        return new QueryCacheConfig(CacheMode.OFF, null);
    }

    /**
     * 本地缓存模式（仅限单机）。maxSize 容量上限；expireAfterWrite/AcessSeconds 为 TTL（秒）。
     * <p>若已通过 {@link #setLocalCacheFactory(QueryCacheFactory)} 注册实现（如 Caffeine），则使用注册的实现。</p>
     */
    public static QueryCacheConfig local(long maximumSize, long expireAfterWriteSeconds, long expireAfterAccessSeconds) {
        QueryCacheFactory factory = localCacheFactory;
        QueryCache cache = (factory != null)
                ? factory.create(maximumSize, expireAfterWriteSeconds, expireAfterAccessSeconds)
                : new SimpleQueryCache(maximumSize, expireAfterWriteSeconds, expireAfterAccessSeconds);
        return new QueryCacheConfig(CacheMode.LOCAL, cache);
    }

    /** 分布式 Redis 缓存（自定义序列化器）。 */
    public static QueryCacheConfig redis(RedisOps redisOps, CacheSerializer serializer, long expireAfterWriteSeconds) {
        return new QueryCacheConfig(CacheMode.REDIS, new RedisQueryCache(redisOps, serializer, expireAfterWriteSeconds));
    }

    /** 分布式 Redis 缓存（默认 JDK 序列化器，要求实体可序列化）。 */
    public static QueryCacheConfig redis(RedisOps redisOps, long expireAfterWriteSeconds) {
        return new QueryCacheConfig(CacheMode.REDIS, new RedisQueryCache(redisOps, expireAfterWriteSeconds));
    }

    /**
     * 自定义缓存实现（如 Caffeine 模块提供的 {@code CaffeineQueryCache}）。
     * 是否为分布式由实现自身的 {@link QueryCache#isDistributed()} 决定。
     *
     * @param cache 缓存实现；null 视为 {@link #off()}
     */
    public static QueryCacheConfig custom(QueryCache cache) {
        if (cache == null) {
            return off();
        }
        return new QueryCacheConfig(CacheMode.CUSTOM, cache);
    }

    public CacheMode getMode() {
        return mode;
    }

    public QueryCache getCache() {
        return cache;
    }
}
