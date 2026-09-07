package cn.vonce.sql.jfinal.config;

import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.java.mapper.MybatisSqlBeanMapperInterceptor;
import cn.vonce.sql.jfinal.datasource.FlexSqlInterceptor;
import cn.vonce.sql.jfinal.listener.JFinalAutoCreateTableListener;
import com.jfinal.aop.AopManager;
import com.jfinal.kit.PropKit;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * JFinal Mybatis 配置
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/8/12 15:27
 */
public class AutoConfigJFinal {

    /**
     * 初始化Mybatis配置
     *
     * @param dataSource 数据源
     */
    public static void init(DataSource dataSource) {
        try {
            // 创建SqlBeanConfig
            SqlBeanConfig sqlBeanConfig = getSqlBeanConfig();
            // 将SqlBeanConfig注册到JFinal的Aop容器
            AopManager.me().addSingletonObject(sqlBeanConfig);

            // 创建SqlBeanMeta
            try (Connection connection = dataSource.getConnection()) {
                SqlBeanMeta sqlBeanMeta = SqlBeanMeta.build(sqlBeanConfig, connection.getMetaData());
                // 将SqlBeanMeta注册到JFinal的Aop容器
                AopManager.me().addSingletonObject(sqlBeanMeta);
            }

            // 配置Mybatis
            SqlSessionFactory sqlSessionFactory = configureMybatis(dataSource);

            // 创建SqlSessionManager用于线程安全的Mapper获取
            SqlSessionManager sqlSessionManager = SqlSessionManager.newInstance(sqlSessionFactory);
            AopManager.me().addSingletonObject(sqlSessionManager);

            // 执行自动创建表（所有bean注册完成后直接调用，processAutoCreate会检查autoCreate配置）
            JFinalAutoCreateTableListener autoCreateTableListener = new JFinalAutoCreateTableListener();
            autoCreateTableListener.processAutoCreate();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取SqlBeanConfig配置
     */
    private static SqlBeanConfig getSqlBeanConfig() {
        SqlBeanConfig sqlBeanConfig = com.jfinal.aop.Aop.get(SqlBeanConfig.class);
        if (sqlBeanConfig == null) {
            sqlBeanConfig = new SqlBeanConfig();
            // 从PropKit中读取配置，使用get(key, defaultValue)避免null
            // 注意：autoAlter是@SqlTable注解的属性，不是全局配置
            // showSql在SqlBeanConfig中不存在，不需要配置
            sqlBeanConfig.setAutoCreate(Boolean.parseBoolean(PropKit.get("flexsql.autoCreate", "true")));
            sqlBeanConfig.setToUpperCase(Boolean.parseBoolean(PropKit.get("flexsql.toUpperCase", "false")));
        }
        return sqlBeanConfig;
    }

    /**
     * 配置Mybatis
     */
    private static SqlSessionFactory configureMybatis(DataSource dataSource) {
        try {
            // 读取mybatis配置文件
            InputStream inputStream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("mybatis-config.xml");

            SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
            SqlSessionFactory sqlSessionFactory;

            if (inputStream != null) {
                sqlSessionFactory = builder.build(inputStream);
            } else {
                // 使用默认配置
                Configuration configuration = new Configuration();
                configuration.setMapUnderscoreToCamelCase(true);

                // 直接将DynamicDataSource传递给Environment
                org.apache.ibatis.mapping.Environment environment =
                        new org.apache.ibatis.mapping.Environment("development",
                                new JdbcTransactionFactory(),
                                dataSource);
                configuration.setEnvironment(environment);

                sqlSessionFactory = builder.build(configuration);
            }

            // 注册Mapper和Interceptor
            Configuration config = sqlSessionFactory.getConfiguration();
            config.addMapper(cn.vonce.sql.java.dao.MybatisSqlBeanDao.class);
            config.addInterceptor(new MybatisSqlBeanMapperInterceptor());

            // 将SqlSessionFactory注册到Aop容器
            AopManager.me().addSingletonObject(sqlSessionFactory);

            return sqlSessionFactory;

        } catch (Exception e) {
            throw new RuntimeException("Mybatis配置初始化失败", e);
        }
    }

    /**
     * 获取FlexSQL拦截器
     * 用户需要在自己的服务类上使用 @Before(FlexSqlInterceptor.class) 注解
     */
    public static Class<? extends com.jfinal.aop.Interceptor> getInterceptor() {
        return FlexSqlInterceptor.class;
    }

}