## FlexSQL（原名：SqlBean）

#### 介绍

FlexSQL 是一款革命性的 ORM（对象关系映射）插件，通过 Java 语法生成 SQL 语句，从根本上改变了开发者在 Java 应用程序中与数据库交互的方式。与传统 ORM 框架要求您首先设计数据库架构然后创建相应实体类不同，FlexSQL 采用了代码优先的方式，让您可以直接专注于编写业务逻辑和实体类，数据库表则由系统自动创建和维护。

**🚀 核心特点：**
- **多数据源支持**：灵活配置多个数据源，支持动态切换
- **动态 Schema**：支持多租户场景下的动态 Schema 切换
- **读写分离**：自动实现读写分离，提升系统性能
- **自动建表**：根据实体类自动创建数据库表
- **自动维护表结构**：实体类变更时自动同步表结构
- **联表查询**：支持复杂的多表关联查询
- **乐观锁**：内置乐观锁支持，防止并发冲突
- **分页查询**：灵活的分页功能
- **逻辑删除**：支持逻辑删除策略配置

**💻 支持环境：**
- **Spring**：JDK 8+, Spring MVC 4.1.2+, Spring Boot 1.x/2.x/3.x
- **Solon**：JDK 8+, Solon 2.6+
- **Android**：JDK 8+, Android 5.0+

**💿 支持数据库：**
- MySQL, MariaDB, Oracle, SQL Server 2008+, PostgreSQL, DB2, Derby, SQLite, HSQL, H2

**相关项目：**
- SuperCode 代码生成项目 👉 [https://gitee.com/iJovi/supercode](https://gitee.com/iJovi/supercode)
- FlexSQL 使用示例 👉 [https://gitee.com/iJovi/FlexSQL-example](https://gitee.com/iJovi/flexsql-example)

### 快速开始

##### 1. 引入 Maven 依赖

**Spring 项目：**
```xml
<dependency>
    <groupId>cn.vonce</groupId>
    <artifactId>flexsql-spring</artifactId>
    <version>1.7.2</version>
</dependency>
```

**Solon 项目：**
```xml
<dependency>
    <groupId>cn.vonce</groupId>
    <artifactId>flexsql-solon</artifactId>
    <version>1.7.2</version>
</dependency>
```

**Android 项目：**
```groovy
implementation 'cn.vonce:flexsql-android:1.7.2'
annotationProcessor 'cn.vonce:flexsql-android:1.7.2'
```

##### 2. 标注实体类

```java
@Data
public class BaseEntity {

    /**
     * 唯一id（注：这个Java注释会当作表的字段注释自动同步）
     */
    @SqlId(type = IdType.SNOWFLAKE_ID_16)
    @SqlColumn(notNull = true)
    private Long id;

    /**
     * 创建者
     */
    private Long creator;

    /**
     * 创建时间
     */
    @SqlDefaultValue(with = FillWith.INSERT)
    private Date createTime;

    /**
     * 更新者
     */
    private Long updater;

    /**
     * 更新时间
     */
    @SqlDefaultValue(with = FillWith.UPDATE)
    private Date updateTime;

    /**
     * 是否删除（逻辑删除，默认过滤已删除数据）
     */
    @SqlLogically(strategy = LogicallyStrategy.FILTER)
    private Boolean deleted;

}

// 编译项目（mvn compile）后会根据实体类生成字段常量，例如 User 类生成的常量类是 User$
@Data
// autoAlter 设置为 true，实体类有变动时自动同步表结构
@SqlTable(autoAlter = true, value = "t_user", remarks = "用户表")
public class User extends BaseEntity {

    /**
     * 用户名
     */
    @SqlColumn(notNull = true)
    private String userName;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 手机号码
     */
    private String mobilePhone;

    /**
     * 密码
     */
    @SqlColumn(notNull = true)
    private String password;

    /**
     * 性别
     */
    @SqlColumn(notNull = true)
    private Integer gender;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 电子邮箱
     */
    private String email;

    /**
     * 头像（旧字段名为 head_portrait）
     */
    @SqlColumn(oldName = "head_portrait")
    private String avatar;

    /**
     * 状态
     */
    @SqlDefaultValue(with = FillWith.INSERT)
    @SqlColumn(notNull = true)
    private UserStatus status;

}
```

##### 3. Service 层接口

```java
public interface UserService extends SqlBeanService<User, Long> {
    // 自定义业务方法
}
```

##### 4. Service 实现类

```java
// 使用 Spring JDBC 继承 SpringJdbcSqlBeanServiceImpl
@Service
public class UserServiceImpl extends MybatisSqlBeanServiceImpl<User, Long> implements UserService {

}
```

##### 5. Controller 层示例

```java
@RequestMapping("user")
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    // 查询示例
    @GetMapping("select")
    public Result select() {
        // 查询列表
        List<User> list = userService.select();
        list = userService.selectBy(condition -> condition.gt(User::getId, 10).and().lt(User::getId, 20));
        
        // 指定字段查询
        list = userService.select(new Select()
            .column(User::getId, User::getUserName, User::getMobilePhone)
            .where().gt(User::getId, 10));

        // 查询单条
        User user = userService.selectById(1L);
        user = userService.selectOneBy(condition -> condition.eq(User::getId, 1001));

        // 复杂查询：20岁女性用户，按创建时间倒序，取前10条
        list = userService.select(new Select()
            .column(User::getId, User::getUserName, User::getMobilePhone)
            .where().eq(User::getAge, 20).and().eq(User::getGender, 0)
            .orderByDesc(User::getCreateTime)
            .page(0, 10));

        return Result.success(list);
    }

    // 分页查询
    @GetMapping("getList")
    public Map<String, Object> getList(HttpServletRequest request) {
        Select select = new Select();
        ReqPageHelper<User> pageHelper = new ReqPageHelper<>(request);
        userService.paging(select, pageHelper);
        return pageHelper.toResult("获取列表成功");
    }
    
    // 统计查询
    @GetMapping("statistics")
    public Result statistics() {
        Select select = new Select();
        select.column(SqlFun.count(User::getId), "count")
              .column(SqlFun.avg(User::getAge), "avgAge");
        select.where().gt(SqlFun.date_format(User::getCreateTime, "%Y-%m-%d"), "2024-06-24");
        select.groupBy(User::getGender);
        select.orderByDesc("count");
        List<Map<String, Object>> result = userService.selectMapList(select);
        return Result.success(result);
    }

    // 更新示例
    @PostMapping("update")
    public Result update(User user) {
        // 根据 ID 更新
        long count = userService.updateByBeanId(user);
        
        // 自定义更新：UPDATE t_user SET gender = 1, user_name = 'Jovi', age = age + 1 WHERE id = 111
        userService.update(new Update<User>()
            .set(User::getGender, 1)
            .set(User::getUserName, "Jovi")
            .setAdd(User::getAge, User::getAge, 1)
            .where().eq(User::getId, 111));
        
        return count > 0 ? Result.success("更新成功") : Result.fail("更新失败");
    }

    // 插入示例
    @PostMapping("add")
    public Result add() {
        User user = new User();
        user.setUserName("testUser");
        user.setPassword("123456");
        userService.insert(user);
        return Result.success("添加成功");
    }

}
```

#### 文档说明

| 序号 | 文档 | 描述 |
| :---: | :--- | :--- |
| 0️⃣ | [注解和枚举使用](doc/Annotation.md) | 实体类注解、枚举类型使用说明 |
| 1️⃣ | [Select 查询](doc/Select.md) | 查询功能详细文档 |
| 2️⃣ | [Insert 插入](doc/Insert.md) | 插入功能详细文档 |
| 3️⃣ | [Delete 删除](doc/Delete.md) | 删除功能详细文档 |
| 4️⃣ | [Update 更新](doc/Update.md) | 更新功能详细文档 |
| 5️⃣ | [数据库管理](doc/DbManage.md) | 建表、备份、表结构维护等 |
| 6️⃣ | [分页查询](doc/Paging.md) | 分页功能使用说明 |
| 7️⃣ | [Service 接口](doc/Interface.md) | Service 接口和实现类说明 |
| 8️⃣ | [SqlHelper](doc/SqlHelper.md) | SQL 生成工具类使用 |
| 9️⃣ | [Where 条件](doc/Where.md) | 条件构造器和 SQL 函数 |
| 🔟 | [多数据源配置](doc/DataSourceConfig.md) | 多数据源、读写分离配置 |
