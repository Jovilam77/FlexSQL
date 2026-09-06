package cn.vonce.sql.config;

/**
 * 查询缓存模式（合并自 cn.vonce.sql.cache.CacheMode，作为 SqlBeanConfig 的字段类型）。
 * <p>
 * 全局生效（所有 SqlBeanService 共享同一 JVM 单例），由
 * {@link cn.vonce.sql.cache.SqlBeanServices#setCacheConfig} 持有；
 * 用户也可在 spring/solon 容器里注册 {@link SqlBeanConfig} Bean 间接驱动，
 * 框架在启动期把 SqlBeanConfig.cache* 字段翻译为 {@link cn.vonce.sql.cache.QueryCacheConfig}。
 *
 * @author Jovi
 * @date 2026/9/6
 */
public enum CacheMode {

    /** 默认：缓存关闭。 */
    OFF,

    /**
     * 本地内存缓存（{@link cn.vonce.sql.cache.SimpleQueryCache} 或用户通过
     * {@link cn.vonce.sql.cache.QueryCacheFactory} 注册的实现）。
     * 仅适用于单实例部署；分布式环境切勿使用，否则会出现跨节点脏读。
     */
    LOCAL,

    /**
     * 分布式缓存（{@link cn.vonce.sql.cache.RedisQueryCache}，需要用户提供 {@code RedisOps} 实现）。
     * 多实例部署推荐使用；框架会自动检测并拾取容器里的 {@code RedisOps} Bean，缺则降级 OFF 并输出 WARN。
     */
    REDIS,

    /**
     * 自定义 {@link cn.vonce.sql.cache.QueryCache} 实现（如 Caffeine 模块提供的 CaffeineQueryCache）。
     * 是否分布式由实现自身的 {@link cn.vonce.sql.cache.QueryCache#isDistributed()} 决定。
     */
    CUSTOM

}