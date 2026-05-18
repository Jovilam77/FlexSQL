# 多数据源、动态 Schema、读写分离配置

## 一、多数据源配置

### 1. 数据源配置文件（YAML）

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    flexsql:
      # SQLite 数据源
      sqlite:
        driverClassName: org.sqlite.JDBC
        url: jdbc:sqlite:config.db
        username: 
        password: 
        initialSize: 1
        minIdle: 3
        maxActive: 20
        maxWait: 60000
      
      # MySQL 读库
      mysqlRead:
        driverClassName: com.mysql.cj.jdbc.Driver
        url: jdbc:mysql://127.0.0.1:3306/read_db?useSSL=false&serverTimezone=Asia/Shanghai
        username: root
        password: 123456
        initialSize: 5
        minIdle: 5
        maxActive: 50
      
      # MySQL 写库
      mysqlWrite:
        driverClassName: com.mysql.cj.jdbc.Driver
        url: jdbc:mysql://127.0.0.1:3306/write_db?useSSL=false&serverTimezone=Asia/Shanghai
        username: root
        password: 123456
        initialSize: 5
        minIdle: 5
        maxActive: 50
```

### 2. 定义数据源名称常量

```java
public class MultiDataSource {
    public static final String SQLITE = "sqlite";
    public static final String MYSQL_READ = "mysqlRead";
    public static final String MYSQL_WRITE = "mysqlWrite";
}
```

### 3. 自动配置方式（推荐）

```java
@EnableAutoConfigMultiDataSource(
    multiDataSource = MultiDataSource.class, 
    defaultDataSource = MultiDataSource.MYSQL_READ
)
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 4. 手动配置方式

```java
@Configuration
public class DataSourceConfiguration {

    @Bean(name = "sqliteDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.flexsql.sqlite")
    public DataSource sqliteDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "mysqlReadDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.flexsql.mysqlRead")
    public DataSource mysqlReadDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "mysqlWriteDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.flexsql.mysqlWrite")
    public DataSource mysqlWriteDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Primary
    @Bean(name = "dynamicDataSource")
    public DataSource dynamicDataSource() {
        Map<Object, Object> dataSourceMap = new HashMap<>(3);
        dataSourceMap.put(MultiDataSource.SQLITE, sqliteDataSource());
        dataSourceMap.put(MultiDataSource.MYSQL_READ, mysqlReadDataSource());
        dataSourceMap.put(MultiDataSource.MYSQL_WRITE, mysqlWriteDataSource());
        
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        dynamicDataSource.setDefaultTargetDataSource(mysqlReadDataSource());
        dynamicDataSource.setTargetDataSources(dataSourceMap);
        return dynamicDataSource;
    }
}
```

---

## 二、读写分离配置

### 1. Service 层配置

```java
/**
 * 读写分离配置说明：
 * 1. master 配置写库，slave 配置读库（可多个）
 * 2. 配置后，内置方法自动实现读写分离
 * 3. 自定义方法需使用 @DbSwitch 注解指定数据源角色
 * 4. 如不配置 @DbSource，使用默认数据源
 */
@DbSource(master = MultiDataSource.MYSQL_WRITE, slave = {MultiDataSource.MYSQL_READ})
@Service
public class UserServiceImpl extends MybatisSqlBeanServiceImpl<User, Long> implements UserService {

    /**
     * 强制走读库
     */
    @DbSwitch(DbRole.SLAVE)
    @Override
    public User getById(Long id) {
        return super.selectById(id);
    }

    /**
     * 强制走写库
     */
    @DbSwitch(DbRole.MASTER)
    @Override
    public User save(User user) {
        return super.insert(user);
    }
}
```

### 2. 数据源切换注解

| 注解 | 说明 |
| :--- | :--- |
| `@DbSource` | 配置 Service 级别的数据源（master/slave） |
| `@DbSwitch` | 方法级别数据源切换（MASTER/SLAVE） |

---

## 三、动态 Schema 配置

### 1. Schema 切面配置

```java
@Component
@Aspect
public class SchemaAspect extends AbstractDynSchemaAspect {

    @Override
    public String getSchema() {
        // 根据业务逻辑获取当前请求的 schema
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            return (String) requestAttributes.getAttribute("schema", RequestAttributes.SCOPE_SESSION);
        }
        return null;
    }
}
```

### 2. Service 使用动态 Schema

```java
@DbDynSchema
@Service
public class UserServiceImpl extends MybatisSqlBeanServiceImpl<User, Long> implements UserService {
    // 所有操作都会使用动态获取的 schema
}
```

---

## 四、事务配置

### 1. 事务超时配置

在 `@DbTransactional` 注解中配置事务超时时间：

```java
@Service
public class UserServiceImpl extends MybatisSqlBeanServiceImpl<User, Long> implements UserService {

    /**
     * timeout = 30 表示事务超时时间为 30 秒
     * timeout = -1 表示不超时（默认）
     */
    @DbTransactional(timeout = 30)
    @Override
    public void batchSave(List<User> users) {
        for (User user : users) {
            super.insert(user);
        }
    }
}
```

### 2. 事务注解属性

| 属性 | 解释 | 默认值 |
| :---: | :--- | :---: |
| `timeout` | 事务超时时间（秒） | -1（不超时） |

---

## 五、SqlBeanConfig 配置

```java
@Configuration
public class FlexSqlConfiguration {

    @Bean
    public SqlBeanConfig sqlBeanConfig() {
        SqlBeanConfig config = new SqlBeanConfig();
        config.setToUpperCase(false);        // SQL 是否转大写
        config.setAutoCreate(true);           // 是否自动建表（总开关）
        // config.setUniqueIdProcessor(...);  // 自定义 ID 生成器
        return config;
    }
}
```

---

## 六、配置优先级说明

| 优先级 | 配置层级 | 说明 |
| :---: | :--- | :--- |
| 1 | 方法级别 `@DbSwitch` | 最高优先级，覆盖其他配置 |
| 2 | 类级别 `@DbSource` | Service 级别的数据源配置 |
| 3 | 全局默认数据源 | `@EnableAutoConfigMultiDataSource` 中配置 |

---

## 七、Solon 环境配置

### 1. 数据源配置（app.yml）

```yaml
flexsql:
  datasource:
    sqlite:
      url: jdbc:sqlite:config.db
      driverClassName: org.sqlite.JDBC
    mysqlRead:
      url: jdbc:mysql://127.0.0.1:3306/read_db
      username: root
      password: 123456
    mysqlWrite:
      url: jdbc:mysql://127.0.0.1:3306/write_db
      username: root
      password: 123456
```

### 2. Solon 启动类配置

```java
@EnableAutoConfigMultiDataSource(
    multiDataSource = MultiDataSource.class, 
    defaultDataSource = MultiDataSource.MYSQL_READ
)
public class App {
    public static void main(String[] args) {
        Solon.start(App.class, args);
    }
}
```
