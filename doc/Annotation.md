# 注解和枚举使用

## 一、注解说明

### 1. @SqlTable - 标识表名

用于标识实体类对应的数据库表。

| 属性 | 解释 | 默认值 | 是否必须 |
| :---: | :--- | :---: | :---: |
| `value` | 表名 | - | 是 |
| `alias` | 表别名 | 空字符串 | 否 |
| `schema` | schema 名称 | 空字符串 | 否 |
| `remarks` | 表注释 | 空字符串 | 否 |
| `autoCreate` | 表不存在时是否自动创建 | true | 否 |
| `autoAlter` | 是否自动更新表结构 | false | 否 |
| `constant` | 是否生成字段常量类 | true | 否 |
| `mapUsToCc` | 是否开启驼峰命名转下划线 | true | 否 |
| `isView` | 是否为视图 | false | 否 |

**示例：**
```java
@SqlTable(value = "t_user", alias = "user", schema = "public", remarks = "用户表", autoAlter = true)
public class User {
    // ...
}
```

### 2. @SqlId - 标识主键

用于标识实体类的主键字段。

| 属性 | 解释 | 默认值 | 是否必须 |
| :---: | :--- | :---: | :---: |
| `type` | ID 生成类型 | `IdType.NORMAL` | 否 |

**支持的 ID 类型：**
- `IdType.NORMAL`：不自动生成，需手动设置
- `IdType.UUID`：UUID 生成
- `IdType.SNOWFLAKE_ID_16`：16 位雪花 ID
- `IdType.SNOWFLAKE_ID_18`：18 位雪花 ID

**示例：**
```java
@SqlId(type = IdType.SNOWFLAKE_ID_16)
private Long id;
```

### 3. @SqlColumn - 标识字段

用于标识实体类字段与数据库列的映射关系。

| 属性 | 解释 | 默认值 | 是否必须 |
| :---: | :--- | :---: | :---: |
| `value` | 数据库列名 | 空字符串（使用字段名） | 否 |
| `notNull` | 是否非空约束 | false | 否 |
| `type` | JDBC 类型 | `JdbcType.NULL` | 否 |
| `length` | 字段长度 | 0 | 否 |
| `decimal` | 小数位数 | 0 | 否 |
| `def` | 默认值 | 空字符串 | 否 |
| `remarks` | 字段注释 | 空字符串（自动读取 Java 注释） | 否 |
| `ignore` | 是否忽略该字段 | false | 否 |
| `oldName` | 旧字段名（用于字段重命名） | 空字符串 | 否 |

**示例：**
```java
@SqlColumn(notNull = true, length = 100, remarks = "用户名")
private String userName;

// 字段重命名：将 head_portrait 改为 avatar
@SqlColumn(value = "avatar", oldName = "head_portrait")
private String avatar;
```

### 4. @SqlUnion - 标识联合查询

标识实体类继承自另一个实体类进行联合查询。

**示例：**
```java
public class UserDetail extends User {
    // User 的字段会自动包含
}
```

### 5. @SqlJoin - 标识表连接

用于联表查询时标识关联字段。

| 属性 | 解释 | 默认值 | 是否必须 |
| :---: | :--- | :---: | :---: |
| `value` | 查询字段（如 `$User.id`） | 空字符串 | 否 |
| `isBean` | 是否为实体对象 | false | 否 |
| `type` | 连接类型 | `JoinType.INNER_JOIN` | 否 |
| `schema` | 连接表的 schema | 空字符串 | 否 |
| `table` | 连接表名 | 空字符串 | 否 |
| `tableAlias` | 连接表别名 | 空字符串 | 否 |
| `tableKeyword` | 连接表关联字段 | 空字符串 | 否 |
| `mainKeyword` | 主表关联字段 | 空字符串 | 否 |
| `from` | 连接的实体类（优先级最高） | `void.class` | 否 |
| `on` | 连接条件类 | `void.class` | 否 |

**支持的连接类型：**
- `JoinType.INNER_JOIN`：内连接
- `JoinType.LEFT_JOIN`：左连接
- `JoinType.RIGHT_JOIN`：右连接
- `JoinType.FULL_JOIN`：全连接

**示例：**
```java
public class UserWithAddress extends User {
    
    // 主外键关联查询
    @SqlJoin(mainKeyword = User$.id, isBean = true)
    private UserAddress address;
    
    // 指定字段查询
    @SqlJoin(value = "$UserAddress.city", table = "$UserAddress._tableName", 
             tableKeyword = "$UserAddress.userId", mainKeyword = "$User.id")
    private String city;
}
```

### 6. @SqlDefaultValue - 标识默认值

用于标识字段在插入或更新时自动填充默认值。

| 属性 | 解释 | 默认值 | 是否必须 |
| :---: | :--- | :---: | :---: |
| `with` | 填充时机 | - | 是 |

**支持的填充时机：**
- `FillWith.INSERT`：仅插入时填充
- `FillWith.UPDATE`：仅更新时填充
- `FillWith.TOGETHER`：插入和更新时都填充

**支持的类型：**
- 基本类型（int, long, boolean 等）
- String
- Date, Timestamp
- BigDecimal

**示例：**
```java
@SqlDefaultValue(with = FillWith.INSERT)
private Date createTime;

@SqlDefaultValue(with = FillWith.UPDATE)
private Date updateTime;
```

### 7. @SqlVersion - 标识乐观锁

用于标识乐观锁字段，支持版本号更新。

**支持的类型：**
- int, long
- Date, Timestamp

**示例：**
```java
@SqlVersion
private Long version;
```

### 8. @SqlLogically - 标识逻辑删除

用于标识逻辑删除字段。

| 属性 | 解释 | 默认值 | 是否必须 |
| :---: | :--- | :---: | :---: |
| `strategy` | 逻辑删除策略 | `LogicallyStrategy.FILTER` | 否 |

**逻辑删除策略：**
- `LogicallyStrategy.FILTER`：默认过滤已删除数据（查询时自动添加 `deleted = 0` 条件）
- `LogicallyStrategy.NOT_FILTER`：不过滤已删除数据（查询时不添加条件）

**示例：**
```java
// 默认过滤已删除数据
@SqlLogically
private Boolean deleted;

// 不过滤已删除数据
@SqlLogically(strategy = LogicallyStrategy.NOT_FILTER)
private Integer isDeleted;
```

---

## 二、枚举使用

### SqlEnum 接口

实体类中使用枚举类型时，需要实现 `SqlEnum` 接口。

**示例：**
```java
public enum UserStatus implements SqlEnum<Integer> {
    DISABLE(0, "禁用"), 
    NORMAL(1, "正常"),
    LOCKED(2, "锁定");

    private Integer code;
    private String desc;

    UserStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public Integer getCode() {
        return this.code;
    }

    @Override
    public String getDesc() {
        return this.desc;
    }
}
```

**在实体类中使用：**
```java
@SqlColumn(notNull = true)
private UserStatus status;
```

---

## 三、生成的常量类

编译项目后，系统会自动根据实体类生成字段常量类，命名规则为 `实体类名$`。

**示例（User 类生成的 User$）：**
```java
package com.example.model.sql;

import cn.vonce.sql.bean.Column;

public class User$ {

    public static final String _schema = "public";
    public static final String _tableName = "t_user";
    public static final String _tableAlias = "user";
    public static final String _remarks = "用户表";
    public static final String _all = "user.*";
    public static final String _count = "COUNT(*)";

    public static final String id = "id";
    public static final Column id$ = new Column(true, "user", "id", "", "唯一标识");

    public static final String userName = "user_name";
    public static final Column userName$ = new Column(true, "user", "user_name", "", "用户名");

    // ... 其他字段
}
```

**使用方式：**
```java
// 使用字段常量
Select select = new Select();
select.column(User$.userName, User$.age);
select.where().eq(User$.id$, 1L);
```

---

## 四、使用示例

### 完整实体类示例

```java
@Data
@SqlTable(value = "t_essay", remarks = "文章表", autoAlter = true)
public class Essay {

    @SqlId(type = IdType.SNOWFLAKE_ID_16)
    private Long id;

    /**
     * 用户ID
     */
    @SqlColumn(notNull = true)
    private Long userId;

    /**
     * 文章内容
     */
    @SqlColumn(type = JdbcType.TEXT)
    private String content;

    /**
     * 是否删除
     */
    @SqlLogically(strategy = LogicallyStrategy.FILTER)
    private Integer isDeleted;

    /**
     * 版本号（乐观锁）
     */
    @SqlVersion
    private Long version;

    /**
     * 创建时间
     */
    @SqlDefaultValue(with = FillWith.INSERT)
    private Date creationTime;

    /**
     * 更新时间
     */
    @SqlDefaultValue(with = FillWith.UPDATE)
    private Date updateTime;
}
```

### 联表查询示例

```java
// 定义联合查询类
public class EssayUnion extends Essay {

    // 关联用户信息
    @SqlJoin(mainKeyword = Essay$.userId, isBean = true)
    private User user;

    // 查询用户昵称
    @SqlJoin(value = "$User.nickname", table = "$User._tableName", 
             tableKeyword = "$User.id", mainKeyword = Essay$.userId)
    private String authorName;
}

// 使用
List<EssayUnion> list = essayService.select(EssayUnion.class);
```
