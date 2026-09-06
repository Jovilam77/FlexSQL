package cn.vonce.sql.cache;

import cn.vonce.sql.config.CacheMode;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.service.SqlBeanService;

/**
 * SqlBeanService 缓存激活工厂。
 * <p>统一入口，由全局 {@link QueryCacheConfig} 驱动（取代原先 per-service 的 SqlBeanConfig 缓存开关）：
 * <ul>
 *   <li>{@link #caching(SqlBeanService)}：按全局 {@link QueryCacheConfig} 决定。
 *       {@link CacheMode#OFF}（默认）原样返回 service；{@link CacheMode#LOCAL}/{@link CacheMode#REDIS}
 *       使用对应实现包裹原 service。</li>
 *   <li>{@link #caching(SqlBeanService, QueryCache)}：用指定的缓存实现（如自定义 {@link QueryCache}）包裹，不受全局开关影响。</li>
 *   <li>{@link #applyFromSqlBeanConfig(SqlBeanConfig, RedisOps)}：spring/solon 自动拾取
 *       {@code SqlBeanConfig} Bean 时调用，把 cache 字段翻译为 {@link QueryCacheConfig}。
 *       <b>仅在当前配置为 OFF 时才生效</b>，避免覆盖用户的编程式调用。</li>
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
     * 从 SqlBeanConfig Bean 应用 cache 字段到全局配置。
     * <p>
     * <b>优先级规则</b>：
     * <ol>
     *   <li>当前全局配置已不是 OFF（即用户已编程式调过 setCacheConfig）→ 跳过，不覆盖。</li>
     *   <li>SqlBeanConfig.cacheMode 为 null（用户没在 Bean 里设）→ 跳过，不写 OFF 也不写其他。</li>
     *   <li>SqlBeanConfig.cacheMode = REDIS 但未提供 RedisOps → 写入 OFF（不抛异常，让调用方 WARN）。</li>
     *   <li>其他情况 → 应用生成的 QueryCacheConfig。</li>
     * </ol>
     *
     * @param sqlBeanConfig 用户在容器里配置的 SqlBeanConfig Bean；null 时不做事。
     * @param redisOps REDIS 模式所需的 RedisOps 实现；null 时若 mode=REDIS 则降级 OFF。
     * @return true 表示本次调用实际改动了全局配置；false 表示未改动。
     */
    public static boolean applyFromSqlBeanConfig(SqlBeanConfig sqlBeanConfig, RedisOps redisOps) {
        if (sqlBeanConfig == null) {
            return false;
        }
        // 编程式优先：用户已主动调用过 setCacheConfig（非 OFF）就不覆盖
        if (cacheConfig != null && cacheConfig.getMode() != CacheMode.OFF) {
            return false;
        }
        QueryCacheConfig built = sqlBeanConfig.buildQueryCacheConfig(redisOps);
        if (built == null) {
            return false;
        }
        cacheConfig = built;
        return true;
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