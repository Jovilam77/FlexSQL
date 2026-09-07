package cn.vonce.sql.java.datasource;

import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.uitls.StringUtil;

import java.util.Properties;

/**
 * FlexSQL 数据源配置类
 * 支持从 Properties、Map、环境变量等多种方式读取配置
 * 使用标准配置前缀（spring.datasource.*、solon.datasource.*），无缝兼容现有项目配置
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
public class FlexDataSourceConfig {

    // ==================== 配置前缀常量 ====================
    public static final String PREFIX_SPRING = "spring.datasource";
    public static final String PREFIX_SOLON = "solon.datasource";
    public static final String PREFIX_FLEXSQL = "flexsql.datasource";

    // ==================== 连接池类型常量 ====================
    public static final String POOL_HIKARI = "hikari";
    public static final String POOL_DRUID = "druid";
    public static final String POOL_C3P0 = "c3p0";
    public static final String POOL_DBCP2 = "dbcp2";

    // ==================== 基础配置 ====================
    private String name = "default";
    private String url;
    private String username;
    private String password;
    private String driverClassName;
    private DbType dbType;

    // ==================== 连接池配置 ====================
    private String poolType; // hikari, druid, c3p0, dbcp2, 或完整类名
    private int maximumPoolSize = 10;
    private int minimumIdle = 5;
    private long connectionTimeout = 30000;
    private long idleTimeout = 600000;
    private long maxLifetime = 1800000;

    // ==================== 连接测试配置 ====================
    private String connectionTestQuery = "SELECT 1";
    private boolean validationTimeout = true;

    // ==================== 高级配置 ====================
    private String dataSourceClassName;
    private Properties dataSourceProperties = new Properties();

    // ==================== 构造函数 ====================

    public FlexDataSourceConfig() {
    }

    /**
     * 从 Properties 加载配置（使用标准前缀）
     * 自动检测前缀：spring.datasource -> solon.datasource -> flexsql.datasource
     *
     * @param properties 属性文件
     * @param name       数据源名称（单数据源时为 null）
     */
    public FlexDataSourceConfig(Properties properties, String name) {
        this.name = name != null ? name : "default";

        // 自动检测配置前缀
        String prefix = detectPrefix(properties, name);

        // 读取基础配置
        this.url = properties.getProperty(prefix + ".url");
        this.username = properties.getProperty(prefix + ".username");
        this.password = properties.getProperty(prefix + ".password");
        this.driverClassName = properties.getProperty(prefix + ".driver-class-name");
        this.poolType = properties.getProperty(prefix + ".type");

        // 如果 poolType 是完整类名，尝试提取简单名称
        if (poolType != null && poolType.contains(".")) {
            if (poolType.toLowerCase().contains("hikari")) {
                poolType = POOL_HIKARI;
            } else if (poolType.toLowerCase().contains("druid")) {
                poolType = POOL_DRUID;
            } else if (poolType.toLowerCase().contains("c3p0")) {
                poolType = POOL_C3P0;
            } else if (poolType.toLowerCase().contains("dbcp")) {
                poolType = POOL_DBCP2;
            }
        }

        // 连接池配置
        String maxPoolSize = properties.getProperty(prefix + ".maximum-pool-size");
        if (StringUtil.isEmpty(maxPoolSize)) {
            maxPoolSize = properties.getProperty(prefix + ".maxPoolSize");
        }
        if (StringUtil.isNotEmpty(maxPoolSize)) {
            this.maximumPoolSize = Integer.parseInt(maxPoolSize);
        }

        String minIdle = properties.getProperty(prefix + ".minimum-idle");
        if (StringUtil.isEmpty(minIdle)) {
            minIdle = properties.getProperty(prefix + ".minIdle");
        }
        if (StringUtil.isNotEmpty(minIdle)) {
            this.minimumIdle = Integer.parseInt(minIdle);
        }

        String connTimeout = properties.getProperty(prefix + ".connection-timeout");
        if (StringUtil.isEmpty(connTimeout)) {
            connTimeout = properties.getProperty(prefix + ".connectionTimeout");
        }
        if (StringUtil.isNotEmpty(connTimeout)) {
            this.connectionTimeout = Long.parseLong(connTimeout);
        }

        String idleT = properties.getProperty(prefix + ".idle-timeout");
        if (StringUtil.isEmpty(idleT)) {
            idleT = properties.getProperty(prefix + ".idleTimeout");
        }
        if (StringUtil.isNotEmpty(idleT)) {
            this.idleTimeout = Long.parseLong(idleT);
        }

        String maxLife = properties.getProperty(prefix + ".max-lifetime");
        if (StringUtil.isEmpty(maxLife)) {
            maxLife = properties.getProperty(prefix + ".maxLifetime");
        }
        if (StringUtil.isNotEmpty(maxLife)) {
            this.maxLifetime = Long.parseLong(maxLife);
        }

        // 数据库类型自动推断
        if (url != null) {
            this.dbType = DbType.getDbType(url);
            // 根据数据库类型自动设置驱动
            if (driverClassName == null) {
                this.driverClassName = dbType.getDriverClass();
            }
        }
    }

    /**
     * 检测配置前缀
     * 优先级：spring.datasource -> solon.datasource -> flexsql.datasource
     */
    private String detectPrefix(Properties properties, String dataSourceName) {
        String prefix = null;

        // 单数据源配置
        if (dataSourceName == null || "default".equals(dataSourceName)) {
            // 检查 spring.datasource
            if (properties.containsKey(PREFIX_SPRING + ".url")) {
                prefix = PREFIX_SPRING;
            }
            // 检查 solon.datasource
            else if (properties.containsKey(PREFIX_SOLON + ".url")) {
                prefix = PREFIX_SOLON;
            }
            // 默认 flexsql.datasource
            else if (properties.containsKey(PREFIX_FLEXSQL + ".url")) {
                prefix = PREFIX_FLEXSQL;
            }
        } else {
            // 多数据源配置
            // 检查 spring.datasource.{name}
            if (properties.containsKey(PREFIX_SPRING + "." + dataSourceName + ".url")) {
                prefix = PREFIX_SPRING + "." + dataSourceName;
            }
            // 检查 solon.datasource.{name}
            else if (properties.containsKey(PREFIX_SOLON + "." + dataSourceName + ".url")) {
                prefix = PREFIX_SOLON + "." + dataSourceName;
            }
            // 默认 flexsql.datasource.{name}
            else if (properties.containsKey(PREFIX_FLEXSQL + "." + dataSourceName + ".url")) {
                prefix = PREFIX_FLEXSQL + "." + dataSourceName;
            }
        }

        return prefix != null ? prefix : PREFIX_FLEXSQL + (dataSourceName != null ? "." + dataSourceName : "");
    }

    /**
     * 构建器模式
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final FlexDataSourceConfig config = new FlexDataSourceConfig();

        public Builder name(String name) {
            config.name = name;
            return this;
        }

        public Builder url(String url) {
            config.url = url;
            config.dbType = DbType.getDbType(url);
            if (config.driverClassName == null) {
                config.driverClassName = config.dbType.getDriverClass();
            }
            return this;
        }

        public Builder username(String username) {
            config.username = username;
            return this;
        }

        public Builder password(String password) {
            config.password = password;
            return this;
        }

        public Builder driverClassName(String driverClassName) {
            config.driverClassName = driverClassName;
            return this;
        }

        public Builder dbType(DbType dbType) {
            config.dbType = dbType;
            return this;
        }

        public Builder poolType(String poolType) {
            config.poolType = poolType;
            return this;
        }

        public Builder poolTypeHikari() {
            config.poolType = POOL_HIKARI;
            return this;
        }

        public Builder poolTypeDruid() {
            config.poolType = POOL_DRUID;
            return this;
        }

        public Builder poolTypeC3p0() {
            config.poolType = POOL_C3P0;
            return this;
        }

        public Builder poolTypeDbcp2() {
            config.poolType = POOL_DBCP2;
            return this;
        }

        public Builder maximumPoolSize(int maximumPoolSize) {
            config.maximumPoolSize = maximumPoolSize;
            return this;
        }

        public Builder minimumIdle(int minimumIdle) {
            config.minimumIdle = minimumIdle;
            return this;
        }

        public Builder connectionTimeout(long connectionTimeout) {
            config.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder idleTimeout(long idleTimeout) {
            config.idleTimeout = idleTimeout;
            return this;
        }

        public Builder maxLifetime(long maxLifetime) {
            config.maxLifetime = maxLifetime;
            return this;
        }

        public Builder connectionTestQuery(String connectionTestQuery) {
            config.connectionTestQuery = connectionTestQuery;
            return this;
        }

        public FlexDataSourceConfig build() {
            return config;
        }
    }

    // ==================== Getter/Setter ====================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
        this.dbType = DbType.getDbType(url);
        if (driverClassName == null) {
            this.driverClassName = dbType.getDriverClass();
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public DbType getDbType() {
        return dbType;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }

    public String getPoolType() {
        return poolType;
    }

    public void setPoolType(String poolType) {
        this.poolType = poolType;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public int getMinimumIdle() {
        return minimumIdle;
    }

    public void setMinimumIdle(int minimumIdle) {
        this.minimumIdle = minimumIdle;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public long getMaxLifetime() {
        return maxLifetime;
    }

    public void setMaxLifetime(long maxLifetime) {
        this.maxLifetime = maxLifetime;
    }

    public String getConnectionTestQuery() {
        return connectionTestQuery;
    }

    public void setConnectionTestQuery(String connectionTestQuery) {
        this.connectionTestQuery = connectionTestQuery;
    }

    public boolean isValidationTimeout() {
        return validationTimeout;
    }

    public void setValidationTimeout(boolean validationTimeout) {
        this.validationTimeout = validationTimeout;
    }

    public String getDataSourceClassName() {
        return dataSourceClassName;
    }

    public void setDataSourceClassName(String dataSourceClassName) {
        this.dataSourceClassName = dataSourceClassName;
    }

    public Properties getDataSourceProperties() {
        return dataSourceProperties;
    }

    public void setDataSourceProperties(Properties dataSourceProperties) {
        this.dataSourceProperties = dataSourceProperties;
    }

    @Override
    public String toString() {
        return "FlexDataSourceConfig{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", username='" + username + '\'' +
                ", dbType=" + dbType +
                ", poolType='" + poolType + '\'' +
                ", maximumPoolSize=" + maximumPoolSize +
                ", minimumIdle=" + minimumIdle +
                '}';
    }

}