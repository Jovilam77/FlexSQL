package cn.vonce.sql.java.datasource;

import cn.vonce.sql.exception.SqlBeanException;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * C3P0 数据源提供者
 * 使用反射方式创建 C3P0 数据源，避免强依赖
 * 只有当 C3P0 库在 classpath 中时才可用
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
public class C3p0DataSourceProvider implements FlexDataSourceProvider {

    private static final Logger logger = Logger.getLogger(C3p0DataSourceProvider.class.getName());

    /**
     * C3P0 核心类名
     */
    private static final String C3P0_DATA_SOURCE_CLASS = "com.mchange.v2.c3p0.ComboPooledDataSource";
    private static final String C3P0_POOL_CONFIG_CLASS = "com.mchange.v2.c3p0.impl.C3P0PooledConnectionPool$PoolConfig";

    /**
     * C3P0 是否在 classpath 中
     */
    private boolean available = false;

    public C3p0DataSourceProvider() {
        try {
            Class.forName(C3P0_DATA_SOURCE_CLASS);
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
            return true;
        }
        String lowerPoolType = poolType.toLowerCase();
        return FlexDataSourceConfig.POOL_C3P0.equals(lowerPoolType)
                || lowerPoolType.contains("c3p0")
                || lowerPoolType.contains("mchange");
    }

    @Override
    public DataSource createDataSource(FlexDataSourceConfig config) {
        if (!available) {
            throw new SqlBeanException("C3P0 is not available in classpath. Please add C3P0 dependency to your project.");
        }

        try {
            // 使用反射创建 ComboPooledDataSource
            Class<?> dataSourceClass = Class.forName(C3P0_DATA_SOURCE_CLASS);
            Object dataSource = dataSourceClass.newInstance();

            // 设置基本配置（C3P0 使用 setter 方法）
            setProperty(dataSource, "setDriverClass", config.getDriverClassName());
            setProperty(dataSource, "setJdbcUrl", config.getUrl());
            setProperty(dataSource, "setUser", config.getUsername());
            setProperty(dataSource, "setPassword", config.getPassword());

            // C3P0 连接池配置通过 Properties 设置
            Properties poolProperties = new Properties();
            poolProperties.setProperty("minPoolSize", String.valueOf(config.getMinimumIdle()));
            poolProperties.setProperty("maxPoolSize", String.valueOf(config.getMaximumPoolSize()));
            poolProperties.setProperty("initialPoolSize", String.valueOf(config.getMinimumIdle()));
            poolProperties.setProperty("maxIdleTime", String.valueOf(config.getIdleTimeout() / 1000));
            poolProperties.setProperty("acquireIncrement", "3");
            poolProperties.setProperty("checkoutTimeout", String.valueOf(config.getConnectionTimeout()));
            poolProperties.setProperty("maxAdministrativeTaskTime", "10");
            poolProperties.setProperty("numHelperThreads", "3");

            // 设置连接池属性
            setProperty(dataSource, "setPoolProperties", poolProperties);

            // 设置连接测试配置
            if (config.getConnectionTestQuery() != null) {
                setProperty(dataSource, "setPreferredTestQuery", config.getConnectionTestQuery());
                setProperty(dataSource, "setTestConnectionOnCheckout", Boolean.FALSE);
                setProperty(dataSource, "setTestConnectionOnCheckin", Boolean.TRUE);
                setProperty(dataSource, "setIdleConnectionTestPeriod", String.valueOf(Math.max(60, config.getIdleTimeout() / 10000)));
            }

            logger.info("FlexSQL: C3P0 DataSource created - " + config.getName());
            return (DataSource) dataSource;
        } catch (Exception e) {
            throw new SqlBeanException("Failed to create C3P0 DataSource: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPoolType() {
        return FlexDataSourceConfig.POOL_C3P0;
    }

    @Override
    public int getPriority() {
        return 3; // 第三优先级
    }

    /**
     * 使用反射调用 setter 方法
     */
    private void setProperty(Object obj, String methodName, Object value) throws Exception {
        if (value == null) {
            return;
        }
        try {
            Class<?> clazz = obj.getClass();
            Method method = clazz.getMethod(methodName, getParameterType(value));
            method.invoke(obj, value);
        } catch (NoSuchMethodException e) {
            // 尝试查找带 String 参数的方法（C3P0 有些 setter 只接受 String）
            try {
                Class<?> clazz = obj.getClass();
                Method method = clazz.getMethod(methodName, String.class);
                method.invoke(obj, String.valueOf(value));
            } catch (NoSuchMethodException e2) {
                // 忽略不存在的方法
                logger.fine("C3P0 setter not found: " + methodName + "(" + value.getClass().getSimpleName() + ")");
            }
        }
    }

    /**
     * 获取参数类型
     */
    private Class<?> getParameterType(Object value) {
        if (value instanceof String) {
            return String.class;
        } else if (value instanceof Integer) {
            return int.class;
        } else if (value instanceof Boolean) {
            return boolean.class;
        } else if (value instanceof Long) {
            return long.class;
        } else if (value instanceof Properties) {
            return Properties.class;
        } else if (value instanceof Map) {
            return Map.class;
        }
        return value.getClass();
    }

    /**
     * 检查 C3P0 是否可用
     */
    public boolean isAvailable() {
        return available;
    }

}
