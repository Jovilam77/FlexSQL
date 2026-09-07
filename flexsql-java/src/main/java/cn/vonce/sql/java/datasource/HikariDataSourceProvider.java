package cn.vonce.sql.java.datasource;

import cn.vonce.sql.exception.SqlBeanException;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * HikariCP 数据源提供者
 * 使用反射方式创建 HikariCP 数据源，避免强依赖
 * 只有当 HikariCP 库在 classpath 中时才可用
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
public class HikariDataSourceProvider implements FlexDataSourceProvider {

    private static final Logger logger = Logger.getLogger(HikariDataSourceProvider.class.getName());

    /**
     * HikariCP 核心类名
     */
    private static final String HIKARI_DATA_SOURCE_CLASS = "com.zaxxer.hikari.HikariDataSource";
    private static final String HIKARI_CONFIG_CLASS = "com.zaxxer.hikari.HikariConfig";

    /**
     * HikariCP 是否在 classpath 中
     */
    private boolean available = false;

    /**
     * 静态初始化，检测 HikariCP 是否可用
     */
    static {
        try {
            Class.forName(HIKARI_DATA_SOURCE_CLASS);
            Class.forName(HIKARI_CONFIG_CLASS);
        } catch (ClassNotFoundException e) {
            // HikariCP 不在 classpath 中，不做任何操作
        }
    }

    public HikariDataSourceProvider() {
        try {
            Class.forName(HIKARI_DATA_SOURCE_CLASS);
            Class.forName(HIKARI_CONFIG_CLASS);
            this.available = true;
        } catch (ClassNotFoundException e) {
            this.available = false;
        }
    }

    @Override
    public boolean supports(String poolType) {
        if (!available) {
            return false;
        }
        if (poolType == null) {
            // 未指定连接池类型时，如果 HikariCP 可用则支持
            return true;
        }
        String lowerPoolType = poolType.toLowerCase();
        return FlexDataSourceConfig.POOL_HIKARI.equals(lowerPoolType)
                || lowerPoolType.contains("hikari")
                || lowerPoolType.contains("zaxxer");
    }

    @Override
    public DataSource createDataSource(FlexDataSourceConfig config) {
        if (!available) {
            throw new SqlBeanException("HikariCP is not available in classpath. Please add HikariCP dependency to your project.");
        }

        try {
            // 使用反射创建 HikariConfig
            Class<?> configClass = Class.forName(HIKARI_CONFIG_CLASS);
            Object hikariConfig = configClass.newInstance();

            // 设置基本配置
            setFieldValue(hikariConfig, "jdbcUrl", config.getUrl());
            setFieldValue(hikariConfig, "username", config.getUsername());
            setFieldValue(hikariConfig, "password", config.getPassword());
            setFieldValue(hikariConfig, "driverClassName", config.getDriverClassName());

            // 设置连接池配置
            setFieldValue(hikariConfig, "maximumPoolSize", config.getMaximumPoolSize());
            setFieldValue(hikariConfig, "minimumIdle", config.getMinimumIdle());
            setFieldValue(hikariConfig, "connectionTimeout", config.getConnectionTimeout());
            setFieldValue(hikariConfig, "idleTimeout", config.getIdleTimeout());
            setFieldValue(hikariConfig, "maxLifetime", config.getMaxLifetime());
            setFieldValue(hikariConfig, "connectionTestQuery", config.getConnectionTestQuery());

            // 使用反射创建 HikariDataSource
            Class<?> dataSourceClass = Class.forName(HIKARI_DATA_SOURCE_CLASS);
            Object dataSource = dataSourceClass.getConstructor(configClass).newInstance(hikariConfig);

            logger.info("FlexSQL: HikariCP DataSource created - " + config.getName());
            return (DataSource) dataSource;
        } catch (Exception e) {
            throw new SqlBeanException("Failed to create HikariCP DataSource: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPoolType() {
        return FlexDataSourceConfig.POOL_HIKARI;
    }

    @Override
    public int getPriority() {
        return 1; // 最高优先级
    }

    /**
     * 使用反射设置字段值
     */
    private void setFieldValue(Object obj, String fieldName, Object value) throws Exception {
        if (value == null) {
            return;
        }
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    /**
     * 检查 HikariCP 是否可用
     */
    public boolean isAvailable() {
        return available;
    }

}