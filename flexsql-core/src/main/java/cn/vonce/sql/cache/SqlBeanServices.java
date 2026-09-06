package cn.vonce.sql.cache;

import cn.vonce.sql.config.CacheMode;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.service.SqlBeanService;

import java.util.Collection;

/**
 * SqlBeanService 缓存激活工厂。
 * <p>统一入口，由全局 {@link QueryCacheConfig} 驱动（取代原先 per-service 的 SqlBeanConfig 缓存开关）：
 * <ul>
 *   <li>{@link #caching(SqlBeanService)}：按全局 {@link QueryCacheConfig} 决定。
 *       {@link CacheMode#OFF}（默认）原样返回 service；{@link CacheMode#LOCAL}/{@link CacheMode#REDIS}
 *       使用对应实现包裹原 service。会自动应用 {@link #installIndexedCache} 注册的
 *       {@link IndexedQueryCache} 装饰器（即按主键失效开关开启时）。</li>
 *   <li>{@link #caching(SqlBeanService, QueryCache)}：用指定的缓存实现（如自定义 {@link QueryCache}）包裹，不受全局开关影响。</li>
 *   <li>{@link #applyFromSqlBeanConfig(SqlBeanConfig, RedisOps)}：spring/solon 自动拾取
 *       {@code SqlBeanConfig} Bean 时调用，把 cache 字段翻译为 {@link QueryCacheConfig}。
 *       <b>仅在当前配置为 OFF 时才生效</b>，避免覆盖用户的编程式调用。</li>
 *   <li>{@link #evictById(String, Object)} / {@link #evictByIds(String, Collection)}：
 *       按主键精确失效。仅在 {@link #installIndexedCache} 已注册 {@link IndexedQueryCache}
 *       装饰器时生效；否则返回 0（无影响，零打扰）。</li>
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

    /**
     * 当前激活的按主键失效装饰器。{@code null} 表示未启用。spring/solon 启动器在
     * {@link SqlBeanConfig#keyEvictionById} = true 时设置；用户代码也可手动调
     * {@link #installIndexedCache} 注册/清除。
     */
    private static volatile IndexedQueryCache indexedCache;

    /** 设置全局查询缓存配置（后端启动时调用）。null 视为 off。 */
    public static void setCacheConfig(QueryCacheConfig config) {
        cacheConfig = (config == null) ? QueryCacheConfig.off() : config;
    }

    /** 读取当前全局查询缓存配置。 */
    public static QueryCacheConfig getCacheConfig() {
        return cacheConfig;
    }

    /**
     * 安装/清除按主键失效装饰器。spring/solon 启动器在 SqlBeanConfig.keyEvictionById=true 时调用；
     * 设为 null 关闭装饰器（变回原始 cache）。多次安装会以最后一个为准。
     */
    public static void installIndexedCache(IndexedQueryCache indexed) {
        indexedCache = indexed;
    }

    /** 读取当前激活的按主键失效装饰器（null 表示未启用），给测试/监控使用。 */
    public static IndexedQueryCache getIndexedCache() {
        return indexedCache;
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
     * 按全局缓存配置自动包裹（OFF 时原样返回，否则用对应实现包裹）。
     * <p>若已注册 {@link IndexedQueryCache} 装饰器，且其 delegate 与当前 cacheConfig.cache 同源，
     * 优先用装饰器 wrap（让 put 时同步建反向索引）。</p>
     */
    public static <T, ID> SqlBeanService<T, ID> caching(SqlBeanService<T, ID> delegate) {
        if (delegate == null) {
            return null;
        }
        QueryCache base = cacheConfig.getCache();
        if (base == null || cacheConfig.getMode() == CacheMode.OFF) {
            return delegate;
        }
        QueryCache effective = effectiveCache(base);
        return CacheableSqlBeanService.wrap(delegate, effective);
    }

    /**
     * 用指定的缓存实现包裹（例如 RedisQueryCache 以实现分布式缓存）。不受全局开关影响。
     */
    public static <T, ID> SqlBeanService<T, ID> caching(SqlBeanService<T, ID> delegate, QueryCache cache) {
        return CacheableSqlBeanService.wrap(delegate, cache);
    }

    /**
     * 按主键精确失效。返回被失效的缓存项数；未启用按主键失效时返回 0。
     * 推荐在 update/delete 之后调用；同时仍调用 {@link SqlBeanService} 上触发
     * 的 {@code evictByTable} 来兜底 list 类缓存项——零漏失效。
     */
    public static int evictById(String table, Object id) {
        IndexedQueryCache indexed = indexedCache;
        return indexed == null ? 0 : indexed.evictById(table, id);
    }

    /** 按主键集合批量失效。未启用按主键失效时返回 0。 */
    public static int evictByIds(String table, Collection<?> ids) {
        IndexedQueryCache indexed = indexedCache;
        return indexed == null ? 0 : indexed.evictByIds(table, ids);
    }

    /**
     * 决定 wrap 用的 cache：优先返回已注册的 IndexedQueryCache 装饰器（前提是其 delegate 是当前 base），
     * 避免重复包装。
     */
    private static QueryCache effectiveCache(QueryCache base) {
        IndexedQueryCache indexed = indexedCache;
        if (indexed != null && indexed.getDelegate() == base) {
            return indexed;
        }
        return base;
    }
}