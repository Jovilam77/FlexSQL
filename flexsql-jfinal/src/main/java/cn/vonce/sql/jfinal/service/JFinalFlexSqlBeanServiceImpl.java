package cn.vonce.sql.jfinal.service;

import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.java.annotation.UseFlexSql;
import cn.vonce.sql.java.dao.FlexSqlBeanDao;
import cn.vonce.sql.java.service.FlexSqlBeanServiceImpl;
import cn.vonce.sql.jfinal.datasource.FlexSqlInterceptor;
import com.jfinal.aop.Before;
import com.jfinal.aop.Inject;
import com.jfinal.plugin.IPlugin;

import javax.sql.DataSource;

/**
 * JFinal环境的FlexSQL原生JDBC实现
 * 不依赖MyBatis，使用纯JDBC执行SQL
 *
 * 使用说明：
 * 1. 在服务类上使用 @UseFlexSql 注解
 * 2. 需要注入 DataSource 和 SqlBeanMeta
 * 3. 事务和数据源切换通过 @Before(FlexSqlInterceptor.class) 实现
 *
 * @param <T>
 * @param <ID>
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
@UseFlexSql
@Before(FlexSqlInterceptor.class)
public class JFinalFlexSqlBeanServiceImpl<T, ID> extends FlexSqlBeanServiceImpl<T, ID> implements IPlugin {

    @Inject
    private DataSource dataSource;

    @Inject
    private SqlBeanMeta sqlBeanMeta;

    private boolean started = false;

    /**
     * 默认构造函数（由JFinal的AOP容器调用）
     */
    public JFinalFlexSqlBeanServiceImpl() {
        super();
    }

    @Override
    public SqlBeanMeta getSqlBeanMeta() {
        return sqlBeanMeta;
    }

    @Override
    public Long getAutoIncrId() {
        return 0L;
    }

    /**
     * 初始化 FlexSqlBeanDao
     * 通过 IPlugin.start() 在注入完成后调用
     */
    @Override
    public boolean start() {
        if (!started) {
            started = true;
            if (dataSource != null && sqlBeanMeta != null) {
                this.flexSqlBeanDao = new FlexSqlBeanDao<>(dataSource, sqlBeanMeta);
                this.sqlBeanMeta = sqlBeanMeta;
            }
        }
        return true;
    }

    @Override
    public boolean stop() {
        started = false;
        return true;
    }

    /**
     * 设置 DataSource（用于手动配置）
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 设置 SqlBeanMeta（用于手动配置）
     */
    @Override
    public void setSqlBeanMeta(SqlBeanMeta sqlBeanMeta) {
        super.setSqlBeanMeta(sqlBeanMeta);
        this.sqlBeanMeta = sqlBeanMeta;
    }

}