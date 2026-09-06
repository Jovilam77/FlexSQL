package cn.vonce.sql.solon.config;

import cn.vonce.sql.cache.CacheableSqlBeanService;
import cn.vonce.sql.cache.RedisOps;
import cn.vonce.sql.cache.SqlBeanServices;
import cn.vonce.sql.config.CacheMode;
import cn.vonce.sql.config.FlexsqlCacheProperties;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.java.annotation.DbSwitch;
import cn.vonce.sql.java.datasource.DataSourceContextHolder;
import cn.vonce.sql.java.mapper.MybatisSqlBeanMapperInterceptor;
import cn.vonce.sql.service.SqlBeanService;
import cn.vonce.sql.solon.annotation.EnableAutoConfigMultiDataSource;
import cn.vonce.sql.solon.datasource.DataSourceInterceptor;
import org.apache.ibatis.solon.MybatisAdapter;
import org.apache.ibatis.solon.integration.MybatisAdapterManager;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.BeanWrap;
import org.noear.solon.core.Plugin;
import org.noear.solon.core.event.AppBeanLoadEndEvent;
import org.noear.solon.core.event.EventListener;
import org.noear.solon.data.tran.TranListener;
import org.noear.solon.data.tran.TranUtils;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Solon Mybatis & 查询缓存配置
 *
 * <p><b>cache 配置应用优先级（从高到低）</b>：</p>
 * <ol>
 *   <li>用户已编程式调用 {@link SqlBeanServices#setCacheConfig}（最高优先级，Bean/yml 都不覆盖）</li>
 *   <li>用户写了 {@code @Bean SqlBeanConfig}（yml 即使配了也不会覆盖该 Bean）</li>
 *   <li>用户没写 Bean 时，回退到 {@code flexsql.cache.*} yml / properties 属性绑定</li>
 *   <li>都没有 → 默认 OFF，零干扰</li>
 * </ol>
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/8/12 15:27
 */
public class AutoConfigSolon implements Plugin {

    /** 收集到的 SqlBeanService 包装，供 BeanLoadEnd 时统一重试包裹（规避 meta 尚未就绪的时序问题） */
    private final List<BeanWrap> sqlBeanServiceWraps = new ArrayList<>();

    @Override
    public void start(AppContext context) {
        context.beanBuilderAdd(EnableAutoConfigMultiDataSource.class, new AutoConfigMultiDataSource());
        context.subWrapsOfType(DataSource.class, (bw) -> {
            init(bw);
        });
        // 查询缓存自动包裹：收集所有 SqlBeanService 的 BeanWrap（注册时回调，覆盖已存在与未来的 bean）。
        // 真正的包裹放在 AppBeanLoadEndEvent（此时 SqlBeanMeta 等依赖均已就绪），并由 isCacheProxy 保证幂等。
        context.subWrapsOfType(SqlBeanService.class, (bw) -> {
            sqlBeanServiceWraps.add(bw);
            wrapSqlBeanServiceIfEnabled(bw);
        });
        context.onEvent(AppBeanLoadEndEvent.class, (EventListener<AppBeanLoadEndEvent>) (e) -> {
            for (BeanWrap bw : sqlBeanServiceWraps) {
                wrapSqlBeanServiceIfEnabled(bw);
            }
            // ===== SqlBeanConfig Bean 拾取；yml 回退；apply + 注册 reporter =====
            applyCacheConfig(e.context());
        });

        // 当前数据源名（多数据源 / @DbSwitch 场景），供缓存键区分数据源，避免跨数据源串数据。
        CacheableSqlBeanService.setDataSourceResolver(DataSourceContextHolder::getDataSource);
        // 事务同步：在事务提交后才执行缓存失效，避免「提交前失效导致并发读回填旧值」。
        CacheableSqlBeanService.setTransactionSynchronization(new CacheableSqlBeanService.CacheTransactionSynchronization() {
            @Override
            public boolean isActive() {
                return TranUtils.inTrans();
            }

            @Override
            public void executeAfterCommit(Runnable action) {
                TranUtils.listen(new TranListener() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
            }
        });
    }

    /**
     * 解析"最终生效的 SqlBeanConfig"：{@code @Bean SqlBeanConfig} 优先；没有 Bean 时回退到 yml / cfg()。
     * 用户编程式 {@code setCacheConfig} 已调用 → apply 内部会跳过（非 OFF 即不动全局）。
     */
    private static void applyCacheConfig(AppContext context) {
        // 1. 用户 @Bean SqlBeanConfig
        Collection<SqlBeanConfig> cfgBeans = context.getBeansOfType(SqlBeanConfig.class);
        if (cfgBeans.size() > 1) {
            throw new IllegalStateException(
                    "FlexSQL 检测到 " + cfgBeans.size() + " 个 SqlBeanConfig Bean，请只保留一个。" +
                            "多个 SqlBeanConfig 会造成 cache 配置歧义。");
        }
        SqlBeanConfig effective = cfgBeans.isEmpty() ? null : cfgBeans.iterator().next();

        // 2. yml 回退（仅在用户没写 Bean 时生效）
        if (effective == null) {
            FlexsqlCacheProperties props = bindFromSolonCfg(context);
            if (!props.isEmpty()) {
                effective = props.toSqlBeanConfig();
            }
        }

        // 3. RedisOps Bean 拾取
        RedisOps redisOps = null;
        Collection<RedisOps> redisOpsBeans = context.getBeansOfType(RedisOps.class);
        if (!redisOpsBeans.isEmpty()) {
            redisOps = redisOpsBeans.iterator().next();
        }

        // 4. 没有 cfg → 完全 OFF，零干扰
        if (effective == null) {
            return;
        }

        // 5. REDIS 模式但缺 RedisOps：WARN 并降级 OFF（不阻断启动）
        if (effective.getCacheMode() == CacheMode.REDIS && redisOps == null) {
            System.err.println("[FlexSQL WARN] cache.mode=REDIS 但容器内未找到 RedisOps Bean，" +
                    "已降级为 OFF。请实现 RedisOps 接口并注册为 Bean。");
        }

        // 6. 应用到全局 QueryCacheConfig（编程式优先语义在 SqlBeanServices.applyFromSqlBeanConfig 内部处理）
        SqlBeanServices.applyFromSqlBeanConfig(effective, redisOps);

        // 7. 启动 slf4j 周期日志 reporter（cfg.cacheMetricsLogIntervalSeconds > 0）
        CacheMetricsSlf4jReporter reporter = new CacheMetricsSlf4jReporter(effective);
        reporter.start();
    }

    /**
     * 从 Solon {@code AppContext.cfg()} 读 {@code flexsql.cache.*} 字段。
     * <p>key 不存在 → 字段保持 null（{@link FlexsqlCacheProperties#isEmpty()} 关键）。
     * 字段值非法（enum 名称错误）→ fail-fast 抛 IllegalStateException。</p>
     */
    static FlexsqlCacheProperties bindFromSolonCfg(AppContext context) {
        FlexsqlCacheProperties props = new FlexsqlCacheProperties();
        String modeStr = context.cfg().getProperty("flexsql.cache.mode");
        if (modeStr != null && !modeStr.isEmpty()) {
            try {
                props.setMode(CacheMode.valueOf(modeStr.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "flexsql.cache.mode 非法: '" + modeStr + "', 必须是 off / local / redis 之一", e);
            }
        }
        setIfPresent(props.getLocal()::setMaximumSize, readLong(context, "flexsql.cache.local.maximum-size"), "local.maximum-size");
        setIfPresent(props.getLocal()::setExpireAfterWrite, readLong(context, "flexsql.cache.local.expire-after-write"), "local.expire-after-write");
        setIfPresent(props.getLocal()::setExpireAfterAccess, readLong(context, "flexsql.cache.local.expire-after-access"), "local.expire-after-access");
        setIfPresent(props.getRedis()::setExpireAfterWrite, readLong(context, "flexsql.cache.redis.expire-after-write"), "redis.expire-after-write");
        setIfPresent(props::setMetricsLogIntervalSeconds, readLong(context, "flexsql.cache.metrics-log-interval-seconds"), "metrics-log-interval-seconds");
        return props;
    }

    private static Long readLong(AppContext context, String key) {
        // solon 2.6 cfg().getProperty 仅返回 String，自行 parse Long；空/null/缺失返回 null（"零干扰"）
        String raw = context.cfg().getProperty(key);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("flexsql.cache.* 字段 '" + key + "' 解析失败（值='" + raw + "' 必须为整数）", e);
        }
    }

    private static void setIfPresent(java.util.function.Consumer<Long> setter, Long value, String keyForLog) {
        if (value != null) {
            setter.accept(value);
        }
    }

    /**
     * 若全局 cn.vonce.sql.cache.QueryCacheConfig 为 LOCAL/REDIS，把真实的 SqlBeanService 包裹为带查询缓存的代理。
     * 默认 OFF 时 caching 返回原对象，零侵入；isCacheProxy 保证重复调用幂等。
     */
    private static void wrapSqlBeanServiceIfEnabled(BeanWrap bw) {
        Object raw = bw.raw();
        if (raw == null || CacheableSqlBeanService.isCacheProxy(raw)) {
            return;
        }
        SqlBeanService<?, ?> service = (SqlBeanService<?, ?>) raw;
        SqlBeanService<?, ?> cached = SqlBeanServices.caching(service);
        if (cached != service) {
            // 注意：Solon 的 BeanWrap.rawSet() 在 raw 已非空时为 no-op，无法覆盖已注册的实例。
            // 查询缓存代理必须在 BeanWrap 创建后就地替换实例，因此通过反射写入 raw / rawUnproxied
            // 两个字段（singleton 下 get() 直接返回 raw，rawUnproxied 用于 get(true) 取原始对象）。
            setRawInstance(bw, cached);
        }
    }

    /** Solon BeanWrap 未提供覆盖非空 raw 的公开 API，这里通过反射写入 raw 与 rawUnproxied，确保 getBean 返回缓存代理。 */
    private static final Field RAW_FIELD = getDeclaredField("raw");
    private static final Field RAW_UNPROXIED_FIELD = getDeclaredField("rawUnproxied");

    private static Field getDeclaredField(String name) {
        try {
            Field f = BeanWrap.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(
                    "FlexSQL 查询缓存自动接线依赖 Solon BeanWrap 的 " + name + " 字段，当前 Solon 版本不兼容", e);
        }
    }

    private static void setRawInstance(BeanWrap bw, Object value) {
        try {
            if (RAW_FIELD != null) {
                RAW_FIELD.set(bw, value);
            }
            if (RAW_UNPROXIED_FIELD != null) {
                RAW_UNPROXIED_FIELD.set(bw, value);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("FlexSQL 查询缓存代理写入 BeanWrap 失败", e);
        }
    }

    protected static void init(BeanWrap bw) {
        MybatisAdapter mybatisAdapter = MybatisAdapterManager.get(bw);
        if (mybatisAdapter != null) {
            mybatisAdapter.getConfiguration().addMapper(cn.vonce.sql.java.dao.MybatisSqlBeanDao.class);
            mybatisAdapter.getConfiguration().addInterceptor(new MybatisSqlBeanMapperInterceptor());
        }
        bw.context().beanMake(SolonAutoCreateTableListener.class);
        bw.context().beanInterceptorAdd(DbSwitch.class, new DataSourceInterceptor());

        if (mybatisAdapter != null) {
            Connection connection = null;
            try {
                connection = mybatisAdapter.getConfiguration().getEnvironment().getDataSource().getConnection();
                SqlBeanConfig sqlBeanConfig = bw.context().getBean(SqlBeanConfig.class);
                SqlBeanMeta sqlBeanMeta = SqlBeanMeta.build(sqlBeanConfig, connection.getMetaData());
                BeanWrap beanWrap = bw.context().wrap("sqlBeanMeta", sqlBeanMeta);
                bw.context().putWrap(SqlBeanMeta.class, beanWrap);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        // log warning if needed
                    }
                }
            }
        }
    }

}
