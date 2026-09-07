package cn.vonce.sql.java.datasource;

import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.uitls.StringUtil;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * FlexSQL 数据源管理器
 * 负责创建和管理多个数据源，支持动态连接池检测
 * 使用标准配置前缀（spring.datasource.*、solon.datasource.*），无缝兼容现有项目配置
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
public class FlexDataSourceManager {

    private static final Logger logger = Logger.getLogger(FlexDataSourceManager.class.getName());

    /**
     * 默认数据源名称
     */
    public static final String DEFAULT_DATASOURCE_NAME = "default";

    /**
     * 单例实例
     */
    private static volatile FlexDataSourceManager instance;

    /**
     * 数据源映射（名称 -> DataSource）
     */
    private final Map<String, DataSource> dataSourceMap = new HashMap<>();

    /**
     * 默认数据源
     */
    private DataSource defaultDataSource;

    /**
     * 可用的数据源提供者列表
     */
    private final List<FlexDataSourceProvider> providers = new ArrayList<>();

    /**
     * 私有构造函数
     */
    private FlexDataSourceManager() {
        loadProviders();
    }

    /**
     * 获取单例实例
     *
     * @return FlexDataSourceManager 实例
     */
    public static FlexDataSourceManager getInstance() {
        if (instance == null) {
            synchronized (FlexDataSourceManager.class) {
                if (instance == null) {
                    instance = new FlexDataSourceManager();
                }
            }
        }
        return instance;
    }

    /**
     * 加载数据源提供者（通过 SPI）
     */
    private void loadProviders() {
        try {
            ServiceLoader<FlexDataSourceProvider> loader = ServiceLoader.load(FlexDataSourceProvider.class);
            for (FlexDataSourceProvider provider : loader) {
                // 只有当连接池库在 classpath 中时才添加
                if (provider.supports(null)) {
                    providers.add(provider);
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load FlexDataSourceProvider via SPI: " + e.getMessage());
        }

        // 如果 SPI 未加载到提供者，手动尝试加载（用于非模块化环境）
        if (providers.isEmpty()) {
            tryLoadProvidersManually();
        }

        // 按优先级排序
        providers.sort(Comparator.comparingInt(FlexDataSourceProvider::getPriority));

        logger.info("FlexSQL: Loaded " + providers.size() + " data source providers");
        for (FlexDataSourceProvider provider : providers) {
            logger.info("  - " + provider.getPoolType() + " (priority: " + provider.getPriority() + ")");
        }
    }

    /**
     * 手动加载提供者（用于非模块化环境）
     */
    private void tryLoadProvidersManually() {
        // 加载 HikariCP
        try {
            Class<?> hikariClass = Class.forName("cn.vonce.sql.java.datasource.HikariDataSourceProvider");
            FlexDataSourceProvider hikariProvider = (FlexDataSourceProvider) hikariClass.newInstance();
            if (hikariProvider.supports(null)) {
                providers.add(hikariProvider);
            }
        } catch (Exception e) {
            // HikariCP 不可用
        }

        // 加载 Druid
        try {
            Class<?> druidClass = Class.forName("cn.vonce.sql.java.datasource.DruidDataSourceProvider");
            FlexDataSourceProvider druidProvider = (FlexDataSourceProvider) druidClass.newInstance();
            if (druidProvider.supports(null)) {
                providers.add(druidProvider);
            }
        } catch (Exception e) {
            // Druid 不可用
        }

        // 加载 C3P0
        try {
            Class<?> c3p0Class = Class.forName("cn.vonce.sql.java.datasource.C3p0DataSourceProvider");
            FlexDataSourceProvider c3p0Provider = (FlexDataSourceProvider) c3p0Class.newInstance();
            if (c3p0Provider.supports(null)) {
                providers.add(c3p0Provider);
            }
        } catch (Exception e) {
            // C3P0 不可用
        }

        // 加载 DBCP2
        try {
            Class<?> dbcp2Class = Class.forName("cn.vonce.sql.java.datasource.Dbcp2DataSourceProvider");
            FlexDataSourceProvider dbcp2Provider = (FlexDataSourceProvider) dbcp2Class.newInstance();
            if (dbcp2Provider.supports(null)) {
                providers.add(dbcp2Provider);
            }
        } catch (Exception e) {
            // DBCP2 不可用
        }
    }

    /**
     * 获取支持指定连接池类型的提供者
     *
     * @param poolType 连接池类型（可为 null，自动检测）
     * @return 数据源提供者
     */
    public FlexDataSourceProvider getProvider(String poolType) {
        if (StringUtil.isNotEmpty(poolType)) {
            // 查找指定类型的提供者
            for (FlexDataSourceProvider provider : providers) {
                if (provider.supports(poolType)) {
                    return provider;
                }
            }
            throw new SqlBeanException("No data source provider found for pool type: " + poolType
                    + ". Available providers: " + providers.stream().map(FlexDataSourceProvider::getPoolType).collect(Collectors.toList()));
        }

        // 未指定类型，返回优先级最高的可用提供者
        if (!providers.isEmpty()) {
            return providers.get(0);
        }

        throw new SqlBeanException("No data source provider available. Please add one of the following dependencies: " +
                "HikariCP (com.zaxxer:HikariCP), Druid (com.alibaba:druid), C3P0 (com.mchange:c3p0), or DBCP2 (org.apache.commons:commons-dbcp2)");
    }

    /**
     * 获取支持指定配置的提供者
     *
     * @param config 数据源配置
     * @return 数据源提供者
     */
    public FlexDataSourceProvider getProvider(FlexDataSourceConfig config) {
        return getProvider(config.getPoolType());
    }

    /**
     * 创建并注册数据源
     *
     * @param config 数据源配置
     * @return 创建的数据源
     */
    public DataSource createDataSource(FlexDataSourceConfig config) {
        if (StringUtil.isEmpty(config.getName())) {
            config.setName(DEFAULT_DATASOURCE_NAME);
        }

        FlexDataSourceProvider provider = getProvider(config);
        DataSource dataSource = provider.createDataSource(config);

        // 注册到管理器
        dataSourceMap.put(config.getName(), dataSource);

        // 如果是第一个数据源或显式设置为默认，则设为默认数据源
        if (defaultDataSource == null || DEFAULT_DATASOURCE_NAME.equals(config.getName())) {
            defaultDataSource = dataSource;
        }

        logger.info("FlexSQL: DataSource created - " + config.getName() + " (pool: " + provider.getPoolType() + ")");
        return dataSource;
    }

    /**
     * 从配置文件加载并创建数据源
     *
     * @param properties 属性文件
     * @param name       数据源名称（单数据源时为 null）
     * @return 创建的数据源
     */
    public DataSource createDataSource(Properties properties, String name) {
        FlexDataSourceConfig config = new FlexDataSourceConfig(properties, name);
        return createDataSource(config);
    }

    /**
     * 从默认配置文件加载数据源
     *
     * @return 创建的数据源
     */
    public DataSource loadDefaultDataSource() {
        Properties properties = loadProperties();
        return createDataSource(properties, null);
    }

    /**
     * 加载配置文件
     * 支持 application.properties、application.yml、flexsql.properties
     */
    private Properties loadProperties() {
        Properties properties = new Properties();

        // 尝试加载多个配置文件
        String[] configFiles = {
                "application.properties",
                "application.yml",
                "flexsql.properties",
                "flexsql.yml"
        };

        for (String configFile : configFiles) {
            try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(configFile)) {
                if (is != null) {
                    if (configFile.endsWith(".yml") || configFile.endsWith(".yaml")) {
                        // YAML 文件需要特殊处理
                        // 这里简化处理，只读取 properties 格式
                    } else {
                        properties.load(is);
                        logger.info("FlexSQL: Loaded config file - " + configFile);
                    }
                }
            } catch (IOException e) {
                // 配置文件不存在，继续尝试下一个
            }
        }

        // 读取环境变量
        Map<String, String> env = System.getenv();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            // 将环境变量转换为标准配置格式
            if (key.startsWith("SPRING_DATASOURCE_") || key.startsWith("FLEXSQL_DATASOURCE_")) {
                String propKey = key.toLowerCase().replace("_", ".");
                properties.setProperty(propKey, entry.getValue());
            }
        }

        return properties;
    }

    /**
     * 加载多数据源配置
     */
    public void loadMultiDataSource() {
        Properties properties = loadProperties();

        // 检测所有数据源名称
        Set<String> dataSourceNames = new LinkedHashSet<>();

        // 检查 spring.datasource 前缀
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(FlexDataSourceConfig.PREFIX_SPRING + ".")) {
                String suffix = key.substring(FlexDataSourceConfig.PREFIX_SPRING.length() + 1);
                if (!suffix.contains(".")) {
                    // 单数据源配置
                    dataSourceNames.add(DEFAULT_DATASOURCE_NAME);
                } else {
                    String name = suffix.substring(0, suffix.indexOf("."));
                    dataSourceNames.add(name);
                }
            } else if (key.startsWith(FlexDataSourceConfig.PREFIX_SOLON + ".")) {
                String suffix = key.substring(FlexDataSourceConfig.PREFIX_SOLON.length() + 1);
                if (!suffix.contains(".")) {
                    dataSourceNames.add(DEFAULT_DATASOURCE_NAME);
                } else {
                    String name = suffix.substring(0, suffix.indexOf("."));
                    dataSourceNames.add(name);
                }
            } else if (key.startsWith(FlexDataSourceConfig.PREFIX_FLEXSQL + ".")) {
                String suffix = key.substring(FlexDataSourceConfig.PREFIX_FLEXSQL.length() + 1);
                if (!suffix.contains(".")) {
                    dataSourceNames.add(DEFAULT_DATASOURCE_NAME);
                } else {
                    String name = suffix.substring(0, suffix.indexOf("."));
                    dataSourceNames.add(name);
                }
            }
        }

        // 创建所有数据源
        for (String name : dataSourceNames) {
            createDataSource(properties, name);
        }
    }

    /**
     * 创建数据源并构建 SqlBeanMeta
     *
     * @param config        数据源配置
     * @param sqlBeanConfig SQL配置
     * @return SqlBeanMeta
     */
    public SqlBeanMeta createDataSourceAndBuildMeta(FlexDataSourceConfig config, cn.vonce.sql.config.SqlBeanConfig sqlBeanConfig) {
        DataSource dataSource = createDataSource(config);
        return buildSqlBeanMeta(dataSource, sqlBeanConfig);
    }

    /**
     * 从数据源构建 SqlBeanMeta
     *
     * @param dataSource    数据源
     * @param sqlBeanConfig SQL配置
     * @return SqlBeanMeta
     */
    public cn.vonce.sql.config.SqlBeanMeta buildSqlBeanMeta(DataSource dataSource, cn.vonce.sql.config.SqlBeanConfig sqlBeanConfig) {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            java.sql.DatabaseMetaData metaData = conn.getMetaData();
            return cn.vonce.sql.config.SqlBeanMeta.build(sqlBeanConfig, metaData);
        } catch (java.sql.SQLException e) {
            throw new SqlBeanException("Failed to build SqlBeanMeta", e);
        }
    }

    /**
     * 获取默认数据源
     *
     * @return 默认数据源
     */
    public DataSource getDefaultDataSource() {
        if (defaultDataSource == null) {
            loadDefaultDataSource();
        }
        return defaultDataSource;
    }

    /**
     * 获取指定名称的数据源
     *
     * @param name 数据源名称
     * @return 数据源
     */
    public DataSource getDataSource(String name) {
        return dataSourceMap.get(name);
    }

    /**
     * 获取当前线程的数据源
     *
     * @return 当前数据源
     */
    public DataSource getCurrentDataSource() {
        String dataSourceName = DataSourceContextHolder.getDataSource();
        if (dataSourceName != null && dataSourceMap.containsKey(dataSourceName)) {
            return dataSourceMap.get(dataSourceName);
        }
        return getDefaultDataSource();
    }

    /**
     * 注册数据源
     *
     * @param name       数据源名称
     * @param dataSource 数据源
     */
    public void registerDataSource(String name, DataSource dataSource) {
        dataSourceMap.put(name, dataSource);
        if (defaultDataSource == null) {
            defaultDataSource = dataSource;
        }
    }

    /**
     * 移除数据源
     *
     * @param name 数据源名称
     */
    public void removeDataSource(String name) {
        dataSourceMap.remove(name);
        if (defaultDataSource != null && DEFAULT_DATASOURCE_NAME.equals(name)) {
            defaultDataSource = dataSourceMap.isEmpty() ? null : dataSourceMap.values().iterator().next();
        }
    }

    /**
     * 设置默认数据源
     *
     * @param name 数据源名称
     */
    public void setDefaultDataSource(String name) {
        if (dataSourceMap.containsKey(name)) {
            defaultDataSource = dataSourceMap.get(name);
        }
    }

    /**
     * 获取所有数据源名称
     *
     * @return 数据源名称集合
     */
    public Set<String> getDataSourceNames() {
        return dataSourceMap.keySet();
    }

    /**
     * 获取所有数据源
     *
     * @return 数据源映射
     */
    public Map<String, DataSource> getAllDataSources() {
        return Collections.unmodifiableMap(dataSourceMap);
    }

    /**
     * 获取可用的连接池类型
     *
     * @return 可用的连接池类型列表
     */
    public List<String> getAvailablePoolTypes() {
        return providers.stream()
                .map(FlexDataSourceProvider::getPoolType)
                .collect(Collectors.toList());
    }

    /**
     * 判断是否存在指定名称的数据源
     *
     * @param name 数据源名称
     * @return 是否存在
     */
    public boolean hasDataSource(String name) {
        return dataSourceMap.containsKey(name);
    }

    /**
     * 关闭所有数据源
     */
    public void closeAll() {
        for (DataSource dataSource : dataSourceMap.values()) {
            try {
                // 如果数据源支持关闭，调用关闭方法
                if (dataSource instanceof AutoCloseable) {
                    ((AutoCloseable) dataSource).close();
                }
            } catch (Exception e) {
                logger.warning("Failed to close data source: " + e.getMessage());
            }
        }
        dataSourceMap.clear();
        defaultDataSource = null;
    }

}
