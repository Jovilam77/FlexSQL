package cn.vonce.sql.spring.config;

import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.java.datasource.FlexDataSourceConfig;
import cn.vonce.sql.java.datasource.FlexDataSourceManager;
import cn.vonce.sql.java.datasource.FlexJdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * FlexSQL 配置类
 * 
 * 通过 @UseFlexSql 注解导入生效
 * 支持 Spring MVC 和 Spring Boot 项目
 * 
 * 自动配置内容：
 * 1. FlexSQL 数据源（使用标准配置前缀 spring.datasource.*）
 * 2. SqlBeanMeta（用于 SQL 生成）
 * 3. DataSourceTransactionManager（Spring 标准事务管理器）
 * 4. FlexJdbcTemplate（支持 Spring 事务感知）
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
@Configuration
@EnableTransactionManagement
public class FlexSqlConfig {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    @Autowired(required = false)
    private SqlBeanConfig sqlBeanConfig;

    @Value("${flexsql.autoCreate:true}")
    private boolean autoCreate;

    @Value("${flexsql.toUpperCase:false}")
    private boolean toUpperCase;

    /**
     * 创建 FlexSQL 数据源
     * 使用标准配置前缀（spring.datasource.*），无缝兼容现有项目配置
     *
     * @return DataSource
     */
    @Bean(name = "flexSqlDataSource")
    @Primary
    public DataSource flexSqlDataSource() {
        logger.info("FlexSQL: Creating FlexSQL DataSource for Spring");
        
        // 创建 FlexSQL 数据源配置
        FlexDataSourceConfig config = new FlexDataSourceConfig();
        config.setName(FlexDataSourceManager.DEFAULT_DATASOURCE_NAME);
        
        return FlexDataSourceManager.getInstance().createDataSource(config);
    }

    /**
     * 创建 SqlBeanMeta
     *
     * @param dataSource DataSource
     * @return SqlBeanMeta
     */
    @Bean(name = "sqlBeanMetaForFlexSql")
    public SqlBeanMeta sqlBeanMeta(DataSource dataSource) {
        logger.info("FlexSQL: Creating SqlBeanMeta");
        
        // 配置 SqlBeanConfig
        if (sqlBeanConfig == null) {
            sqlBeanConfig = new SqlBeanConfig();
        }
        sqlBeanConfig.setAutoCreate(autoCreate);
        sqlBeanConfig.setToUpperCase(toUpperCase);

        // 构建 SqlBeanMeta
        try (Connection conn = dataSource.getConnection()) {
            return SqlBeanMeta.build(sqlBeanConfig, conn.getMetaData());
        } catch (SQLException e) {
            logger.warning("Failed to build SqlBeanMeta: " + e.getMessage());
            throw new RuntimeException("Failed to build SqlBeanMeta", e);
        }
    }

    /**
     * 创建 Spring 事务管理器
     * 使用标准的 DataSourceTransactionManager，
     * 这样用户可以使用 Spring 的 @Transactional 注解
     *
     * @param dataSource DataSource
     * @return PlatformTransactionManager
     */
    @Bean(name = "flexSqlTransactionManager")
    @Primary
    public PlatformTransactionManager flexSqlTransactionManager(DataSource dataSource) {
        logger.info("FlexSQL: Creating DataSourceTransactionManager for Spring");
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 创建 FlexJdbcTemplate
     * 通过匿名内部类重写 getConnection() 和 closeConnection() 方法，
     * 实现对 Spring 事务上下文的感知，确保在 @Transactional 注解下使用同一个事务连接
     *
     * @param dataSource DataSource
     * @return FlexJdbcTemplate
     */
    @Bean(name = "flexJdbcTemplate")
    public FlexJdbcTemplate flexJdbcTemplate(DataSource dataSource) {
        logger.info("FlexSQL: Creating FlexJdbcTemplate with Spring transaction awareness");
        
        return new FlexJdbcTemplate(dataSource) {
            
            @Override
            protected Connection getConnection() throws SQLException {
                // 1. 优先从 FlexSQL 事务上下文获取（多数据源场景）
                Connection conn = super.getConnection();
                if (conn instanceof cn.vonce.sql.java.datasource.ConnectionProxy) {
                    return conn;
                }
                
                // 2. 尝试从 Spring 事务上下文获取（单数据源场景）
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    Object resource = TransactionSynchronizationManager.getResource(dataSource);
                    if (resource instanceof Connection) {
                        return (Connection) resource;
                    }
                    if (resource instanceof org.springframework.jdbc.datasource.ConnectionHolder) {
                        return ((org.springframework.jdbc.datasource.ConnectionHolder) resource).getConnection();
                    }
                }
                
                // 3. 返回父类获取的连接
                return conn;
            }
            
            @Override
            protected void closeConnection(Connection conn) {
                // 如果是 ConnectionProxy，由 FlexSQL 事务管理器负责关闭
                if (conn instanceof cn.vonce.sql.java.datasource.ConnectionProxy) {
                    return;
                }
                
                // 如果处于 Spring 事务中，连接由 Spring 事务管理器负责关闭
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    return;
                }
                
                // 否则调用父类方法关闭连接
                super.closeConnection(conn);
            }
        };
    }

}
