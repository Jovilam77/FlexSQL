package cn.vonce.sql.java.datasource;

import cn.vonce.sql.exception.SqlBeanException;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.logging.Logger;

/**
 * Druid 数据源提供者
 * 使用反射方式创建 Druid 数据源，避免强依赖
 * 只有当 Druid 库在 classpath 中时才可用
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
public class DruidDataSourceProvider implements FlexDataSourceProvider {

    private static final Logger logger = Logger.getLogger(DruidDataSourceProvider.class.getName());

    /**
     * Druid 核心类名
     */
    private static final String DRUID_DATA_SOURCE_CLASS = "com.alibaba.druid.pool.DruidDataSource";

    /**
     * Druid 是否在 classpath 中
     */
    private boolean available = false;

    public DruidDataSourceProvider() {
        try {
            Class.forName(DRUID_DATA_SOURCE_CLASS);
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
        return FlexDataSourceConfig.POOL_DRUID.equals(lowerPoolType)
                || lowerPoolType.contains("druid")
                || lowerPoolType.contains("alibaba");
    }

    @Override
    public DataSource createDataSource(FlexDataSourceConfig config) {
        if (!available) {
            throw new SqlBeanException("Druid is not available in classpath. Please add Druid dependency to your project.");
        }

        try {
            // 使用反射创建 DruidDataSource
            Class<?> dataSourceClass = Class.forName(DRUID_DATA_SOURCE_CLASS);
            Object dataSource = dataSourceClass.newInstance();

            // 设置基本配置
            setFieldValue(dataSource, "url", config.getUrl());
            setFieldValue(dataSource, "username", config.getUsername());
            setFieldValue(dataSource, "password", config.getPassword());
            setFieldValue(dataSource, "driverClassName", config.getDriverClassName());

            // 设置连接池配置
            setFieldValue(dataSource, "maxActive", config.getMaximumPoolSize());
            setFieldValue(dataSource, "minIdle", config.getMinimumIdle());
            setFieldValue(dataSource, "initialSize", config.getMinimumIdle());
            setFieldValue(dataSource, "maxWait", config.getConnectionTimeout());

            // 设置连接测试配置
            setFieldValue(dataSource, "validationQuery", config.getConnectionTestQuery());
            setFieldValue(dataSource, "testWhileIdle", true);
            setFieldValue(dataSource, "testOnBorrow", false);
            setFieldValue(dataSource, "testOnReturn", false);

            logger.info("FlexSQL: Druid DataSource created - " + config.getName());
            return (DataSource) dataSource;
        } catch (Exception e) {
            throw new SqlBeanException("Failed to create Druid DataSource: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPoolType() {
        return FlexDataSourceConfig.POOL_DRUID;
    }

    @Override
    public int getPriority() {
        return 2; // 次高优先级
    }

    /**
     * 使用反射设置字段值
     */
    private void setFieldValue(Object obj, String fieldName, Object value) throws Exception {
        if (value == null) {
            return;
        }
        Field field = findField(obj.getClass(), fieldName);
        if (field != null) {
            field.setAccessible(true);
            field.set(obj, value);
        }
    }

    /**
     * 查找字段（支持驼峰命名）
     */
    private Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // 尝试在父类中查找
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            return null;
        }
    }

    /**
     * 检查 Druid 是否可用
     */
    public boolean isAvailable() {
        return available;
    }

}