## FlexSQL（原名：SqlBean）

#### 介绍

###### FlexSQL 是一款革命性的 ORM（对象关系映射）插件，通过 Java 语法生成 SQL 语句，从根本上改变了开发者在 Java 应用程序中与数据库交互的方式。与传统 ORM 框架要求您首先设计数据库架构然后创建相应实体类不同，FlexSQL 采用了代码优先的方式，让您可以直接专注于编写业务逻辑和实体类，数据库表则由系统自动创建和维护。

###### 🚀特点: 多数据源, 动态Schema, 读写分离, 自动建表, 自动维护表结构, 联表查询, 乐观锁, 分页

###### 💻Spring环境: JDK8+, (Spring MVC 4.1.2+, Spring Boot 1.x, 2.x, 3.x), (Mybatis3.4.0+ 或 Spring JDBC 二选一)

###### 💻Solon环境: JDK8+, Solon2.6+, Mybatis3.4.0+

###### 📱Android环境: JDK8+, Android 5.0+

###### 💿数据库: Mysql, MariaDB, Oracle, Sqlserver2008+, Postgresql, DB2, Derby, Sqlite, HSQL, H2

###### SuperCode代码生成项目👉 [https://gitee.com/iJovi/supercode](https://gitee.com/iJovi/supercode "SuperCode")
###### FlexSQL使用例子以及代码生成例子点击这里👉 [https://gitee.com/iJovi/FlexSQL-example](https://gitee.com/iJovi/flexsql-example "FlexSQL-Example")

### 快速开始

##### 1.引入Maven依赖
###### Spring项目
	<dependency>
		<groupId>cn.vonce</groupId>
		<artifactId>flexsql-spring</artifactId>
		<version>1.7.2-beta1</version>
	</dependency>
###### Solon项目
	<dependency>
		<groupId>cn.vonce</groupId>
		<artifactId>flexsql-solon</artifactId>
		<version>1.7.2-beta1</version>
	</dependency>
###### Android项目（[Android项目详细使用文档](doc/Android.md "Android项目详细使用文档")）
	implementation 'cn.vonce:flexsql-android:1.7.2-beta1'
    annotationProcessor 'cn.vonce:flexsql-android:1.7.2-beta1'
##### 2.标注实体类

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
     * 创建者（注：这个Java注释会当作表的字段注释自动同步）
     */
    private Long creator;

    /**
     * 创建时间（注：这个Java注释会当作表的字段注释自动同步）
     */
    @SqlDefaultValue(with = FillWith.INSERT)
    private Date createTime;

    /**
     * 更新者（注：这个Java注释会当作表的字段注释自动同步）
     */
    private Long updater;

    /**
     * 更新时间（注：这个Java注释会当作表的字段注释自动同步）
     */
    @SqlDefaultValue(with = FillWith.UPDATE)
    private Date updateTime;

    /**
     * 是否删除（注：这个Java注释会当作表的字段注释自动同步）
     */
    @SqlLogically
    private Boolean deleted;

}

//编译项目（mvn compile）后会根据实体类生成字段常量，例如User类生成的常量类是User$，例如获取id字段是User$.id$
@Data
//autoAlter设置为true，实体类有变动时自动同步表结构
@SqlTable(autoAlter = true, value = "t_user", remarks = "用户")
public class User extends BaseEntity {

    /**
     * 用户名（注：这个Java注释会当作表的字段注释自动同步）
     */
    @SqlColumn(notNull = true)
    private String userName;

    /**
     * 昵称（注：这个Java注释会当作表的字段注释自动同步）
     */
    private String nickName;

    /**
     * 手机号码（注：这个Java注释会当作表的字段注释自动同步）
     */
    private String mobilePhone;

    /**
     * 密码（注：这个Java注释会当作表的字段注释自动同步）
     */
    @SqlColumn(notNull = true)
    private String password;

    /**
     * 性别（注：这个Java注释会当作表的字段注释自动同步）
     */
    @SqlColumn(notNull = true)
    private Integer gender;

    /**
     * 年龄（注：这个Java注释会当作表的字段注释自动同步）
     */
    private Integer age;

    /**
     * 电子邮箱（注：这个Java注释会当作表的字段注释自动同步）
     */
    private String email;

    /**
     * 头像（注：这个Java注释会当作表的字段注释自动同步）
     */
    @SqlColumn(oldName = "head_portrait")//修改表字段名称需要在oldName指定旧的名称
    private String avatar;

    /**
     * 状态（注：这个Java注释会当作表的字段注释自动同步）
     */
    @SqlDefaultValue(with = FillWith.INSERT)
    @SqlColumn(notNull = true)
    private UserStatus status;

}
```

##### 3.无需Dao层，Service层接口只需继承SqlBeanService<实体类, id类型>

```java
public interface UserService extends SqlBeanService<User, Long> {
    //这里可以写自己封装的方法

}
```

##### 4.Service实现类只需继承MybatisSqlBeanServiceImpl<实体类, id类型>和实现你的Service接口

```java
//使用Spring Jdbc的话将继承的父类改成SpringJdbcSqlBeanServiceImpl即可
@Service
public class UserServiceImpl extends MybatisSqlBeanServiceImpl<User, Long> implements UserService {

}
```

##### 5.Controller层

```java

@RequestMapping("user")
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    //查询
    @GetMapping("select")
    public RS select() {
        //查询列表
        List<User> list = userService.select();
        list = userService.selectBy(condition -> condition.gt(User::getId, 10).and().lt(User::getId, 20));
        //指定查询
        list = userService.select(new Select().column(User::getId, User::getUserName, User::getMobilePhone).where().gt(User::getId, 10));

        //查询一条
        User user = userService.selectById(1L);
        user = userService.selectOneBy(condition -> condition.eq(User::getId, 1001));

        //sql语义化查询《20岁且是女性的用户根据创建时间倒序，获取前10条》
        list = userService.select(new Select().column(User::getId, User::getUserName, User::getMobilePhone).where().eq(User::getAge, 20).and().eq(User::getGender, 0).back().orderByDesc(User::getCreateTime).page(0, 10));

        //联表查询《广州或深圳的18岁的女性用户，根据创建时间倒序，查询前10条用户的信息和地址》
        Select select = new Select();
        select.column(User::getId, User::getUserName, User::getMobilePhone, UserAddress::getProvince, UserAddress::getCity, UserAddress::getArea, UserAddress::getDetails);
        select.innerJoin(UserAddress.class).on().eq(UserAddress::getId, User::getId);
        select.where().eq(User::getAge, 18).and().eq(User::getGender, 0).and(condition -> condition.eq(UserAddress::getCity, "广州").or().eq(UserAddress::getCity, "深圳"));
        select.orderByDesc(User::getCreateTime);
        select.page(0, 10);

        //查询Map
        Map<String, Object> map = userService.selectMap(select);
        List<Map<String, Object>> mapList = userService.selectMapList(select);

        return super.successHint("获取成功", list);
    }

    //分页
    @GetMapping("getList")
    public Map getList(HttpServletRequest request) {
        // 查询对象
        Select select = new Select();
        ReqPageHelper<User> pageHelper = new ReqPageHelper<>(request);
        userService.paging(select, pageHelper);
        return pageHelper.toResult("获取列表成功");
    }
    
    //统计（函数使用）
    @GetMapping("getStatistics")
    public Result getStatistics() {
        Select select = new Select();
        select.column(SqlFun.count(User::getId), "count").column(SqlFun.avg(User::getAge));
        select.where().gt(SqlFun.date_format(User::getCreateTime, "%Y-%m-%d"), "2024-06-24");
        select.groupBy(User::getGender);
        select.orderByDesc("count");
        List<Map<String, Object>> mapList = userService.selectMapList(select);
        return Result.success(mapList);
    }

    //更新
    @PostMapping("update")
    public RS update(User user) {
        //根据bean内部id更新
        long i = userService.updateByBeanId(user);
        //根据条件更新
        //i = userService.updateBy(Wrapper.where(Cond.gt(User::getAge, 22)).and(Cond.eq(User::getGender, 1)));
        //指定更新某个字段 UPDATE user SET gender = 1, name = 'Jovi' ,age = age + 1 WHERE = id = 111
        userService.update(new Update<User>().set(User::getGender, 1).set(User::getUserName, "Jovi").setAdd(User::getAge, User::getAge, 1).where().eq(User::getId, 111).back());
        if (i > 0) {
            return super.successHint("更新成功");
        }
        return super.othersHint("更新失败");
    }

    //删除
    @PostMapping("deleteById")
    public RS deleteById(Integer[] id) {
        //根据id删除
        long i = userService.deleteById(id);
        //根据条件删除
        //i = userService.deleteBy(Wrapper.where(gt(User::getAge, 22)).and(eq(User::getGender, 1)));
        if (i > 0) {
            return super.successHint("删除成功");
        }
        return super.othersHint("删除失败");
    }

    //插入
    @PostMapping("add")
    public RS add() {
        List<User> userList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            User user = new User(i, "name" + i);
            userList.add(user);
        }
        userService.insert(userList);
        return successHint("成功");
    }

}
```

##### 👇👇👇更多用法请查看下方文档👇👇👇

#### 文档说明

###### [0️⃣. 注解和枚举使用](doc/Annotation.md "注解和枚举使用")

###### [1️⃣. Select](doc/Select.md "Select")

###### [2️⃣. Insert](doc/Insert.md "Insert")

###### [3️⃣. Delete](doc/Delete.md "Delete")

###### [4️⃣. Update](doc/Update.md "Update")

###### [5️⃣. 数据库相关操作](doc/DbManage.md "数据库相关操作")

###### [6️⃣. 分页查询](doc/Paging.md "分页查询")

###### [7️⃣. Service接口和实现类](doc/Interface.md "Service接口和实现类")

###### [8️⃣. FlexSQL和SqlHelper](doc/SqlHelper.md "FlexSQL和SqlHelper")

###### [9️⃣. Where条件和Sql函数](doc/Where.md "Where条件和Sql函数")

###### [🔟. 多数据源动态Schema读写分离相关配置](doc/DataSourceConfig.md "多数据源动态Schema读写分离相关配置")
