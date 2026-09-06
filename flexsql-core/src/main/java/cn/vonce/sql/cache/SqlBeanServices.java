package cn.vonce.sql.cache;

import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.service.SqlBeanService;

/**
 * SqlBeanService 缓存激活工厂。
 * <p>统一入口：
 * <ul>
 *   <li>{@link #caching(SqlBeanService)}：按 {@code SqlBeanMeta -> SqlBeanConfig} 自动判断是否开启，
 *       开启则使用配置中的 {@link QueryCache}（未指定则新建默认 {@link SimpleQueryCache} 本地缓存），包裹原 service。</li>
 *   <li>{@link #caching(SqlBeanService, QueryCache)}：用指定的缓存实现（如 {@link RedisQueryCache}）包裹。</li>
 * </ul>
 * 默认关闭，不影响既有行为。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public final class SqlBeanServices {

    private SqlBeanServices() {
    }

    /**
     * 按配置自动包裹（开启查询缓存时生效，否则原样返回）
     */
    public static <T, ID> SqlBeanService<T, ID> caching(SqlBeanService<T, ID> delegate) {
        if (delegate == null) {
            return null;
        }
        SqlBeanMeta meta = CacheableSqlBeanService.resolveMeta(delegate);
        SqlBeanConfig config = meta.getSqlBeanConfig();
        if (config == null || !config.getQueryCacheEnabled()) {
            return delegate;
        }
        QueryCache cache = config.getQueryCache();
        if (cache == null) {
            cache = new SimpleQueryCache(config.getQueryCacheMaxSize(),
                    config.getQueryCacheExpireAfterWriteSeconds());
        }
        return CacheableSqlBeanService.wrap(delegate, cache);
    }

    /**
     * 用指定的缓存实现包裹（例如 RedisQueryCache 以实现分布式缓存）
     */
    public static <T, ID> SqlBeanService<T, ID> caching(SqlBeanService<T, ID> delegate, QueryCache cache) {
        return CacheableSqlBeanService.wrap(delegate, cache);
    }
}
