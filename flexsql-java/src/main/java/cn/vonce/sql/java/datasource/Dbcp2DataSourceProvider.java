package cn.vonce.sql.java.datasource;

import cn.vonce.sql.exception.SqlBeanException;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * DBCP2 数据源提供者
 * 使用反射方式创建 Apache Commons DBCP2 数据源，避免强依赖
 * 只有当 DBCP2 库在 classpath 中时才可用
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
public class Dbcp2DataSourceProvider implements FlexDataSourceProvider {

    private static final Logger logger = Logger.getLogger(Dbcp2DataSourceProvider.class.getName());

    /**
     * DBCP2 核心类名
     */
    private static final String DBCP2_BASIC_DATA_SOURCE_CLASS = "org.apache.commons.dbcp2.BasicDataSource";
    private static final String DBCP2_POOLING_DATA_SOURCE_CLASS = "org.apache.commons.dbcp2.PoolingDataSource";

    /**
     * DBCP2 是否在 classpath 中
     */
    private boolean available = false;

    public Dbcp2DataSourceProvider() {
        try {
            Class.forName(DBCP2_BASIC_DATA_SOURCE_CLASS);
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
        return FlexDataSourceConfig.POOL_DBCP2.equals(lowerPoolType)
                || lowerPoolType.contains("dbcp")
                || lowerPoolType.contains("commons-pool")
                || lowerPoolType.contains("apache");
    }

    @Override
    public DataSource createDataSource(FlexDataSourceConfig config) {
        if (!available) {
            throw new SqlBeanException("DBCP2 is not available in classpath. Please add Apache Commons DBCP2 dependency to your project.");
        }

        try {
            // 使用反射创建 BasicDataSource
            Class<?> dataSourceClass = Class.forName(DBCP2_BASIC_DATA_SOURCE_CLASS);
            Object dataSource = dataSourceClass.newInstance();

            // 设置基本配置
            setProperty(dataSource, "setDriverClassName", config.getDriverClassName());
            setProperty(dataSource, "setUrl", config.getUrl());
            setProperty(dataSource, "setUsername", config.getUsername());
            setProperty(dataSource, "setPassword", config.getPassword());

            // 设置连接池配置
            setProperty(dataSource, "setMaxTotal", config.getMaximumPoolSize());
            setProperty(dataSource, "setMaxIdle", config.getMaximumPoolSize());
            setProperty(dataSource, "setMinIdle", config.getMinimumIdle());
            setProperty(dataSource, "setInitialSize", config.getMinimumIdle());
            setProperty(dataSource, "setMaxWaitMillis", config.getConnectionTimeout());
            setProperty(dataSource, "setMinEvictableIdleTimeMillis", config.getIdleTimeout());

            // 设置连接测试配置
            if (config.getConnectionTestQuery() != null) {
                setProperty(dataSource, "setValidationQuery", config.getConnectionTestQuery());
                setProperty(dataSource, "setTestOnBorrow", Boolean.FALSE);
                setProperty(dataSource, "setTestOnReturn", Boolean.FALSE);
                setProperty(dataSource, "setTestWhileIdle", Boolean.TRUE);
                setProperty(dataSource, "setTimeBetweenEvictionRunsMillis", 
                        String.valueOf(Math.max(60000, config.getIdleTimeout() / 10)));
                setProperty(dataSource, "setNumTestsPerEvictionRun", "3");
            }

            // 设置连接属性
            setProperty(dataSource, "setDefaultAutoCommit", Boolean.TRUE);
            setProperty(dataSource, "setDefaultTransactionIsolation", "READ_COMMITTED");

            // 设置连接池属性
            setProperty(dataSource, "setRemoveAbandonedOnBorrow", Boolean.TRUE);
            setProperty(dataSource, "setRemoveAbandonedOnMaintenance", Boolean.TRUE);
            setProperty(dataSource, "setRemoveAbandonedTimeout", "60");
            setProperty(dataSource, "setLogAbandoned", Boolean.FALSE);

            logger.info("FlexSQL: DBCP2 BasicDataSource created - " + config.getName());
            return (DataSource) dataSource;
        } catch (Exception e) {
            throw new SqlBeanException("Failed to create DBCP2 DataSource: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPoolType() {
        return FlexDataSourceConfig.POOL_DBCP2;
    }

    @Override
    public int getPriority() {
        return 4; // 第四优先级
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
            // 尝试查找带 String 参数的方法
            try {
                Class<?> clazz = obj.getClass();
                Method method = clazz.getMethod(methodName, String.class);
                method.invoke(obj, String.valueOf(value));
            } catch (NoSuchMethodException e2) {
                // 尝试查找带 int 参数的方法
                if (value instanceof String) {
                    try {
                        Class<?> clazz = obj.getClass();
                        Method method = clazz.getMethod(methodName, int.class);
                        method.invoke(obj, Integer.parseInt((String) value));
                    } catch (NoSuchMethodException e3) {
                        logger.fine("DBCP2 setter not found: " + methodName + "(" + value.getClass().getSimpleName() + ")");
                    } catch (NumberFormatException e3) {
                        logger.fine("DBCP2 setter conversion failed: " + methodName + "(" + value + ")");
                    }
                } else {
                    logger.fine("DBCP2 setter not found: " + methodName + "(" + value.getClass().getSimpleName() + ")");
                }
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
        }
        return value.getClass();
    }

    /**
     * 检查 DBCP2 是否可用
     */
    public boolean isAvailable() {
        return available;
    }

}
