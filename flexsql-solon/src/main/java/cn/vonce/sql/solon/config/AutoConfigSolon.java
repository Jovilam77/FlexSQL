package cn.vonce.sql.solon.config;

import cn.vonce.sql.cache.CacheableSqlBeanService;
import cn.vonce.sql.cache.SqlBeanServices;
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
import java.util.List;

/**
 * Solon Mybatis 配置
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
     * 若 SqlBeanConfig.queryCacheEnabled=true，把真实的 SqlBeanService 包裹为带查询缓存的代理。
     * 默认关闭时 caching 返回原对象，零侵入；isCacheProxy 保证重复调用幂等。
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