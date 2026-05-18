package cn.vonce.sql.spring.config;

import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.java.mapper.MybatisSqlBeanMapperInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import java.sql.Connection;
import java.util.List;
import java.util.logging.Logger;

/**
 * Mybatis自动配置
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2019/8/21 13:05
 */
public class MybatisAutoConfig {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Spring mvc模式使用，Spring boot为空
     */
    @Autowired(required = false)
    private SqlSessionFactoryBean sqlSessionFactoryBean;

    @Autowired
    private BeanFactory beanFactory;

    @Autowired(required = false)
    private SqlBeanConfig sqlBeanConfig;


    @Bean
    @Conditional(ConditionalOnUseMybatis.class)
    public Interceptor interceptor() throws Exception {
        MybatisSqlBeanMapperInterceptor mybatisMapperInterceptor = new MybatisSqlBeanMapperInterceptor();
        SqlSessionFactory sqlSessionFactory = null;
        if (sqlSessionFactoryBean != null) {
            sqlSessionFactory = sqlSessionFactoryBean.getObject();
        } else {
            try {
                sqlSessionFactory = beanFactory.getBean(SqlSessionFactory.class);
            } catch (Exception e) {
                logger.warning(String.format("interceptor：%s", e.getMessage()));
            }
        }
        if (sqlSessionFactory != null) {
            List<Interceptor> interceptorList = sqlSessionFactory.getConfiguration().getInterceptors();
            if (interceptorList != null) {
                if (!interceptorList.stream().anyMatch(item -> item instanceof MybatisSqlBeanMapperInterceptor)) {
                    sqlSessionFactory.getConfiguration().addInterceptor(mybatisMapperInterceptor);
                }
            }
        }
        return mybatisMapperInterceptor;
    }

    @Bean(name = "sqlBeanConfigForMybatis")
    @Conditional(ConditionalOnUseMybatis.class)
    public SqlBeanMeta sqlBeanMeta() throws Exception {
        SqlSessionFactory sqlSessionFactory = null;
        if (sqlSessionFactoryBean != null) {
            sqlSessionFactory = sqlSessionFactoryBean.getObject();
        } else {
            try {
                sqlSessionFactory = beanFactory.getBean(SqlSessionFactory.class);
            } catch (Exception e) {
                logger.warning(String.format("sqlBeanMeta：%s", e.getMessage()));
            }
        }
        if (sqlSessionFactory == null) {
            throw new IllegalStateException("SqlSessionFactory is null");
        }
        Connection connection = null;
        try {
            connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
            return SqlBeanMeta.build(sqlBeanConfig, connection.getMetaData());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.warning(String.format("Failed to close connection: %s", e.getMessage()));
                }
            }
        }
    }

}
