package cn.vonce.sql.spring.config;

import cn.vonce.sql.cache.CacheableSqlBeanService;
import cn.vonce.sql.cache.RedisOps;
import cn.vonce.sql.cache.SqlBeanServices;
import cn.vonce.sql.config.CacheMode;
import cn.vonce.sql.config.FlexsqlCacheProperties;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.java.datasource.DataSourceContextHolder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 查询缓存自动接线配置。
 * <p>注册 {@link CacheableSqlBeanServicePostProcessor}，使其在 SqlBeanService bean 初始化后按
 * 全局缓存配置自动包裹为带查询缓存的代理。</p>
 * <p>同时通过可插拔 SPI 向 core 注册「当前数据源名解析器」与「事务同步钩子」，使缓存键能区分多数据源、
 * 且写操作在事务提交后才失效（避免脏数据窗口）。这两个能力由具体框架提供，core 本身不依赖 Spring/Solon。</p>
 *
 * <p><b>cache 配置应用优先级（从高到低）</b>：</p>
 * <ol>
 *   <li>用户已编程式调用 {@link SqlBeanServices#setCacheConfig}（最高优先级，Bean/yml 都不覆盖）</li>
 *   <li>用户写了 {@code @Bean SqlBeanConfig}（yml 即使配了也不会覆盖该 Bean）</li>
 *   <li>用户没写 Bean 时，回退到 {@code flexsql.cache.*} yml / properties 属性绑定</li>
 *   <li>都没有 → 默认 OFF，零干扰</li>
 * </ol>
 *
 * <p><b>Bean 自动拾取规则</b>：</p>
 * <ul>
 *   <li>检测到 {@link SqlBeanConfig} Bean → 把 cache 字段翻译为 {@link cn.vonce.sql.cache.QueryCacheConfig}，
 *       通过 {@link SqlBeanServices#applyFromSqlBeanConfig} 应用到全局。</li>
 *   <li>检测到 {@link RedisOps} Bean（用户自定义实现）→ 作为 REDIS 模式的实现拾取。</li>
 *   <li>多个 {@link SqlBeanConfig} Bean → fail-fast 抛异常（避免歧义）。</li>
 *   <li>用户已编程式调用 {@link SqlBeanServices#setCacheConfig} → Bean / yml 都不覆盖（编程式优先）。</li>
 *   <li>没有任何 Bean 也没写 yml → 默认 OFF，零干扰。</li>
 * </ul>
 *
 * @author Jovi
 * @version 1.1
 */
@Configuration
public class SqlBeanCacheAutoConfig implements EnvironmentAware {

    /** yml / properties 属性绑定的公共前缀。spring 与 solon 模块共用。 */
    static final String CACHE_PROPERTY_PREFIX = "flexsql.cache.";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * Spring 4.1 不支持 {@code ObjectProvider}（4.3 才有），改用 {@code List<T>} 注入
     * （spring 4.0+ 已支持集合 / 数组注入）。
     */
    @Bean
    public CacheableSqlBeanServicePostProcessor cacheableSqlBeanServicePostProcessor(
            List<SqlBeanConfig> sqlBeanConfigs,
            List<RedisOps> redisOpsList) {
        // 当前数据源名（多数据源 / @DbSwitch 场景），供缓存键区分数据源，避免跨数据源串数据。
        CacheableSqlBeanService.setDataSourceResolver(DataSourceContextHolder::getDataSource);
        // 事务同步：在事务提交后才执行缓存失效，避免「提交前失效导致并发读回填旧值」。
        CacheableSqlBeanService.setTransactionSynchronization(new CacheableSqlBeanService.CacheTransactionSynchronization() {
            @Override
            public boolean isActive() {
                return TransactionSynchronizationManager.isActualTransactionActive();
            }

            @Override
            public void executeAfterCommit(Runnable action) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
            }
        });

        // ===== 应用 cache 配置：Bean 优先；否则尝试 yml/properties =====
        SqlBeanConfig effective = resolveEffectiveConfig(sqlBeanConfigs, redisOpsList);
        if (effective != null) {
            // 注册 slf4j 周期日志 reporter
            CacheMetricsSlf4jReporter reporter = new CacheMetricsSlf4jReporter(effective);
            cn.vonce.sql.cache.CacheMetrics.addReporter(reporter);
            return new ReportingCachePostProcessor(reporter);
        }

        return new CacheableSqlBeanServicePostProcessor();
    }

    /**
     * 解析"最终生效的 SqlBeanConfig"：Bean 优先；没有 Bean 时回退到 yml。
     * 用户编程式 setCacheConfig 已调用 → 当前配置非 OFF，则 apply 时内部会跳过，不被 Bean/yml 覆盖。
     */
    private SqlBeanConfig resolveEffectiveConfig(List<SqlBeanConfig> sqlBeanConfigs,
                                                  List<RedisOps> redisOpsList) {
        if (sqlBeanConfigs != null && sqlBeanConfigs.size() > 1) {
            throw new IllegalStateException(
                    "FlexSQL 检测到 " + sqlBeanConfigs.size() + " 个 SqlBeanConfig Bean，请只保留一个。" +
                            "多个 SqlBeanConfig 会造成 cache 配置歧义。");
        }
        SqlBeanConfig userBean = (sqlBeanConfigs == null || sqlBeanConfigs.isEmpty()) ? null : sqlBeanConfigs.get(0);
        RedisOps redisOps = (redisOpsList == null || redisOpsList.isEmpty()) ? null : redisOpsList.get(0);

        SqlBeanConfig effective;
        if (userBean != null) {
            // 用户写了 Bean → 不读 yml（Bean 优先）
            effective = userBean;
        } else if (environment != null) {
            // 没 Bean → 尝试 yml
            FlexsqlCacheProperties props = bindFromEnvironment(environment);
            if (props.isEmpty()) {
                return null;
            }
            effective = props.toSqlBeanConfig();
        } else {
            return null;
        }

        // REDIS 模式但缺 RedisOps：WARN 并降级 OFF（不阻断启动）
        if (effective.getCacheMode() == CacheMode.REDIS && redisOps == null) {
            System.err.println("[FlexSQL WARN] cache.mode=REDIS 但容器内未找到 RedisOps Bean，" +
                    "已降级为 OFF。请实现 RedisOps 接口并注册为 Bean。");
        }
        SqlBeanServices.applyFromSqlBeanConfig(effective, redisOps);
        return effective;
    }

    /**
     * 从 Spring {@link Environment} 读 {@code flexsql.cache.*} 字段。
     * <p>key 不存在 → 字段保持 null（{@link FlexsqlCacheProperties#isEmpty()} 关键）。
     * 字段值非法（enum 名称错误、字符串无法转 Long）→ fail-fast 抛 IllegalStateException
     * （启动期错误比运行时静默降级更易排查）。</p>
     */
    static FlexsqlCacheProperties bindFromEnvironment(Environment env) {
        FlexsqlCacheProperties props = new FlexsqlCacheProperties();

        String modeStr = env.getProperty(CACHE_PROPERTY_PREFIX + "mode");
        if (modeStr != null && !modeStr.isEmpty()) {
            try {
                props.setMode(CacheMode.valueOf(modeStr.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "flexsql.cache.mode 非法: '" + modeStr + "', 必须是 off / local / redis 之一", e);
            }
        }

        setIfPresent(props.getLocal()::setMaximumSize, readLong(env, CACHE_PROPERTY_PREFIX + "local.maximum-size"), "local.maximum-size");
        setIfPresent(props.getLocal()::setExpireAfterWrite, readLong(env, CACHE_PROPERTY_PREFIX + "local.expire-after-write"), "local.expire-after-write");
        setIfPresent(props.getLocal()::setExpireAfterAccess, readLong(env, CACHE_PROPERTY_PREFIX + "local.expire-after-access"), "local.expire-after-access");

        setIfPresent(props.getRedis()::setExpireAfterWrite, readLong(env, CACHE_PROPERTY_PREFIX + "redis.expire-after-write"), "redis.expire-after-write");

        setIfPresent(props::setMetricsLogIntervalSeconds, readLong(env, CACHE_PROPERTY_PREFIX + "metrics-log-interval-seconds"), "metrics-log-interval-seconds");

        return props;
    }

    private static Long readLong(Environment env, String key) {
        try {
            return env.getProperty(key, Long.class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("flexsql.cache.* 字段 '" + key + "' 解析失败: " + e.getMessage(), e);
        }
    }

    private static void setIfPresent(java.util.function.Consumer<Long> setter, Long value, String key) {
        if (value != null) {
            setter.accept(value);
        }
    }

    /**
     * 复合后处理器：既负责触发 reporter 的生命周期（{@code afterPropertiesSet/destroy}），
     * 又负责 SqlBeanService 缓存包裹。
     * Spring 4.1 没有 {@code ObjectProvider}（4.3 才有），也没有优雅的"延迟实例化 lifecycle bean"，
     * 这里把两个职责合并在一个 Bean 里。
     */
    public static final class ReportingCachePostProcessor extends CacheableSqlBeanServicePostProcessor
            implements org.springframework.beans.factory.DisposableBean, org.springframework.beans.factory.InitializingBean {

        private final CacheMetricsSlf4jReporter reporter;

        public ReportingCachePostProcessor(CacheMetricsSlf4jReporter reporter) {
            this.reporter = reporter;
        }

        @Override
        public void afterPropertiesSet() {
            reporter.afterPropertiesSet();
        }

        @Override
        public void destroy() {
            reporter.destroy();
            // 清理注册，避免容器重启时重复注册
            cn.vonce.sql.cache.CacheMetrics.removeReporter(reporter);
        }
    }
}
