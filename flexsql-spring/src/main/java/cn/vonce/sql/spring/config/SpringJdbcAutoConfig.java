package cn.vonce.sql.spring.config;

import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * SpringJdbc自动配置
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2025/06/09 10:51
 */
public class SpringJdbcAutoConfig {

    private final java.util.logging.Logger logger = Logger.getLogger(this.getClass().getName());

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private SqlBeanConfig sqlBeanConfig;

    @Bean(name = "sqlBeanMetaForSpringJdbc")
    public SqlBeanMeta sqlBeanMeta() {
        Connection connection = null;
        try {
            connection = jdbcTemplate.getDataSource().getConnection();
            return SqlBeanMeta.build(sqlBeanConfig, connection.getMetaData());
        } catch (SQLException e) {
            logger.warning(String.format("sqlBeanMeta：%s", e.getMessage()));
            return null;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.warning(String.format("Failed to close connection: %s", e.getMessage()));
                }
            }
        }
    }

}
