## FlexSQL For Android

###### 1.引入Gradle依赖

	implementation 'cn.vonce:flexsql-android:1.7.1-beta1'
    annotationProcessor 'cn.vonce:flexsql-android:1.7.1-beta1'

###### 2.标注实体类

```java
//实体类基类
@Data
public class BaseEntity {

    @SqlId(type = IdType.SNOWFLAKE_ID_16)
    @SqlColumn(notNull = true, remarks = "唯一id")//字段不能为null,表字段注释
    private Long id;

    @SqlColumn(remarks = "创建者")//表字段注释
    private Long creator;

    @SqlDefaultValue(with = FillWith.INSERT)
    @SqlColumn(remarks = "创建时间")//表字段注释
    private Date createTime;

    @SqlColumn(remarks = "更新者")//表字段注释
    private Long updater;

    @SqlDefaultValue(with = FillWith.UPDATE)
    @SqlColumn(remarks = "更新时间")//表字段注释
    private Date updateTime;

    @SqlLogically
    @SqlColumn(remarks = "是否删除(0正常 1删除)")//表字段注释
    private Boolean deleted;

}

//构建项目（Rebuild Project）后会根据实体类生成字段常量，例如User类生成的常量类是User$，例如获取id字段是User$.id$
@Data
//autoAlter设置为true，实体类有变动时自动同步表结构
@SqlTable(autoAlter = true, value = "t_user", remarks = "用户")
public class User extends BaseEntity {

    @SqlColumn(notNull = true, remarks = "用户名")//字段不能为null,表字段注释
    private String userName;

    @SqlColumn(remarks = "姓名")//表字段注释
    private String nickName;

    @SqlColumn(notNull = true, remarks = "手机号码")//表字段注释
    private String mobilePhone;

    @SqlColumn(notNull = true, remarks = "密码")//表字段注释
    private String password;

    @SqlColumn(notNull = true, remarks = "性别")//表字段注释
    private Integer gender;

    @SqlColumn(remarks = "年龄")//表字段注释
    private Integer age;

    @SqlColumn(remarks = "电子邮箱")//表字段注释
    private String email;

    @SqlColumn(remarks = "头像", oldName = "head_portrait")//表字段注释,修改表字段名称需要在oldName指定旧的名称
    private String avatar;

    @SqlDefaultValue(with = FillWith.INSERT)
    @SqlColumn(notNull = true, remarks = "状态(0正常 1停用)")//字段不能为null,表字段注释
    private UserStatus status;

}
```

###### 3.获取连接（建议在上一步把所有表字段关系建立好，第一次获取连接时会自动创建表结构）

```java
public class MainActivity extends AppCompatActivity {

    private SqlBeanHelper<Essay, String> essaySqlBeanHelper;
    //private SqlBeanHelper<User, String> userSqlBeanHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //方式一，单库模式
        SQLiteHelper.init(this, "testdb", 1);//建议放在MainActivity或继承的Application
        essaySqlBeanHelper = SQLiteHelper.db().get(Essay.class);

        //方式二，多库模式
        //essaySqlBeanHelper = SQLiteHelper.db(this, "testdb1", 1).get(Essay.class);
        //userSqlBeanHelper = SQLiteHelper.db(this, "testdb2", 1).get(User.class);

    }
}
```

###### 4.CRUD操作

```java

public class MainActivity extends AppCompatActivity {

    private SqlBeanHelper<Essay, String> sqlBeanHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SQLiteHelper.init(this, "testdb", 1);
        sqlBeanHelper = SQLiteHelper.db().get(Essay.class);

    }

    //查询
    public void select() {
        //查询列表
        List<User> list = userService.select();
        list = sqlBeanHelper.selectBy(Wrapper.where(Cond.gt(User$.id$, 10)).and(Cond.lt(User$.id$, 20)));
        //指定查询
        list = sqlBeanHelper.select(new Select().column(User$.id$, User$.user_name$, User$.mobile_phone$).where().gt(User$.id$, 10));

        //查询一条
        User user = sqlBeanHelper.selectById(1L);
        user = sqlBeanHelper.selectOneBy(Wrapper.where(eq(User$.id$, 1001)));

        //sql语义化查询《20岁且是女性的用户根据创建时间倒序，获取前10条》
        list = sqlBeanHelper.select(new Select().column(User$.id$, User$.user_name$, User$.mobile_phone$).where().eq(User$.age$, 20).and().eq(User$.gender$, 0).back().orderByDesc(User$.create_time$).page(0, 10));

        //联表查询《广州或深圳的18岁的女性用户，根据创建时间倒序，查询前10条用户的信息和地址》
        Select select = new Select();
        select.column(User$.id$, User$.user_name$, User$.mobile_phone$, UserAddress$.province$, UserAddress$.city$, UserAddress$.area$, UserAddress$.details$);
        select.innerJoin(UserAddress.class).on().eq(UserAddress$.id$, User$.id$);
        select.where().eq(User$.age$, 18).and().eq(User$.gender$, 0).and(condition -> condition.eq(UserAddress$.city$, "广州").or().eq(UserAddress$.city$, "深圳"));
        select.orderByDesc(User$.create_time$);
        select.page(0, 10);

        //查询Map
        Map<String, Object> map = sqlBeanHelper.selectMap(select);
        List<Map<String, Object>> mapList = sqlBeanHelper.selectMapList(select);
    }

    //分页
    public void getPageList() {
        // 查询对象
        Select select = new Select();
        PageHelper<User> pageHelper = new PageHelper<>(0, 10);
        pageHelper.paging(select, sqlBeanHelper);
        ResultData<List<Essay>> data = pageHelper.getResultData();
    }

    //统计（函数使用）
    public void getStatistics() {
        Select select = new Select();
        select.column(SqlFun.count(User$.id$), "count").column(SqlFun.avg(User$.age$));
        select.where().gt(SqlFun.date_format(User$.create_time$, "%Y-%m-%d"), "2024-06-24");
        select.groupBy(User$.gender$);
        select.orderByDesc("count");
        List<Map<String, Object>> mapList = sqlBeanHelper.selectMapList(select);
    }

    //更新
    public void update(Essay essay) {
        //根据bean内部id更新
        long i = sqlBeanHelper.updateByBeanId(essay);
        //根据外部id更新
        //i = sqlBeanHelper.updateById(essay, 20);
        //根据条件更新
        //i = sqlBeanHelper.update(new Update<User>().set(User$.gender$, 1).set(User$.name$, "Jovi").setAdd(User$.age$, User$.age$, 1).where().eq(User$.id$, 111).back());
    }

    //删除
    public void deleteById(String[] id) {
        //根据id删除
        long i = sqlBeanHelper.deleteById(id);
        //根据条件删除
        //i = sqlBeanHelper.deleteBy(Wrapper.where(gt(User$.age$, 22)).and(eq(User$.gender$, 1)));
    }

    //插入
    public void add() {
        List<Essay> essayList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Essay essay = new Essay(i, "name" + i);
            essayList.add(essay);
        }
        sqlBeanHelper.insert(essayList);
    }

}
```

##### 👇👇👇更多用法请查看下方文档👇👇👇

###### [0️⃣. 注解详情与使用](doc/Annotation.md "注解详情与使用")

###### [1️⃣. Select](doc/Select.md "Select")

###### [2️⃣. Insert](doc/Insert.md "Insert")

###### [3️⃣. Delete](doc/Delete.md "Delete")

###### [4️⃣. Update](doc/Update.md "Update")

###### [5️⃣. 表操作相关](doc/Table.md "表操作相关")

###### [6️⃣. 分页查询](doc/Paging.md "分页查询")

###### [7️⃣. Service接口和实现类](doc/Interface.md "Service接口和实现类")

###### [8️⃣. SqlBean和SqlHelper](doc/SqlHelper.md "SqlBean和SqlHelper")

###### [9️⃣. Where条件和包装器](doc/Where.md "Where条件和包装器")
