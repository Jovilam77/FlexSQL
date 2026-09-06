package cn.vonce.sql.cache;

import cn.vonce.sql.service.SqlBeanService;

/**
 * SqlBeanService 缓存激活工厂。
 * <p>统一入口，由全局 {@link QueryCacheConfig} 驱动（取代原先 per-service 的 SqlBeanConfig 缓存开关）：
 * <ul>
 *   <li>{@link #caching(SqlBeanService)}：按全局 {@link QueryCacheConfig} 决定。
 *       {@link CacheMode#OFF}（默认）原样返回 service；{@link CacheMode#LOCAL}/{@link CacheMode#REDIS}
 *       使用对应实现包裹原 service。</li>
 *   <li>{@link #caching(SqlBeanService, QueryCache)}：用指定的缓存实现（如自定义 {@link QueryCache}）包裹，不受全局开关影响。</li>
 * </ul>
 * 配置通过 {@link #setCacheConfig(QueryCacheConfig)} 注入，默认 {@link QueryCacheConfig#off()}（不开启）。</p>
 *
 * @author Jovi
 * @version 1.1
 */
public final class SqlBeanServices {

    private SqlBeanServices() {
    }

    private static volatile QueryCacheConfig cacheConfig = QueryCacheConfig.off();

    /** 设置全局查询缓存配置（后端启动时调用）。null 视为 off。 */
    public static void setCacheConfig(QueryCacheConfig config) {
        cacheConfig = (config == null) ? QueryCacheConfig.off() : config;
    }

    /** 读取当前全局查询缓存配置。 */
    public static QueryCacheConfig getCacheConfig() {
        return cacheConfig;
    }

    /**
     * 按全局缓存配置自动包裹（OFF 时原样返回，否则用对应实现包裹）
     */
    public static <T, ID> SqlBeanService<T, ID> caching(SqlBeanService<T, ID> delegate) {
        if (delegate == null) {
            return null;
        }
        if (cacheConfig.getMode() == CacheMode.OFF || cacheConfig.getCache() == null) {
            return delegate;
        }
        return CacheableSqlBeanService.wrap(delegate, cacheConfig.getCache());
    }

    /**
     * 用指定的缓存实现包裹（例如 RedisQueryCache 以实现分布式缓存）。不受全局开关影响。
     */
    public static <T, ID> SqlBeanService<T, ID> caching(SqlBeanService<T, ID> delegate, QueryCache cache) {
        return CacheableSqlBeanService.wrap(delegate, cache);
    }
}
