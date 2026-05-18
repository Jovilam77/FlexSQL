# Select 查询

## 一、Select 对象使用示例

### 1. 基础查询

```java
// 查询所有字段
Select select = new Select();
select.column(User$.userName, User$.age, User$.email);
select.where().eq(User$.status$, UserStatus.NORMAL);
List<User> list = userService.select(select);
```

### 2. 联表查询

```java
Select select = new Select();
select.column(User$.id, User$.userName, UserAddress$.city);
select.innerJoin(UserAddress.class).on().eq(UserAddress$.userId$, User$.id$);
select.where().eq(User$.age, 18);
select.orderByDesc(User$.createTime);
List<Map<String, Object>> result = userService.selectMapList(select);
```

### 3. 复杂条件查询

```java
// SQL: WHERE age = 20 AND gender = 0 AND (city = '广州' OR city = '深圳')
Select select = new Select();
select.column(User$.userName, User$.mobilePhone);
select.where()
    .eq(User$.age, 20)
    .and().eq(User$.gender, 0)
    .and(condition -> condition.eq(UserAddress$.city, "广州").or().eq(UserAddress$.city, "深圳"));
```

### 4. 使用 SQL 函数

```java
Select select = new Select();
select.column(SqlFun.count(User$.id), "count")
      .column(SqlFun.avg(User$.age), "avgAge");
select.where().gt(SqlFun.date_format(User$.createTime$, "%Y-%m-%d"), "2024-06-24");
select.groupBy(User$.gender);
select.orderByDesc("count");
List<Map<String, Object>> result = userService.selectMapList(select);
```

---

## 二、SelectService 接口

### 1. 根据 ID 查询

```java
// 根据单个 ID 查询
User user = userService.selectById(1L);

// 根据多个 ID 查询
List<User> users = userService.selectByIds(1L, 2L, 3L);

// 指定返回类型
UserDTO dto = userService.selectById(UserDTO.class, 1L);
```

### 2. 根据条件查询单条

```java
// 使用字符串条件
User user = userService.selectOneBy("status = ?", UserStatus.NORMAL);

// 使用 Wrapper 条件
User user = userService.selectOneBy(Wrapper.where(Cond.eq(User$.id$, 1001)));

// 指定返回类型
UserDTO dto = userService.selectOneBy(UserDTO.class, Wrapper.where(Cond.eq(User$.id$, 1001)));
```

### 3. 根据条件查询列表

```java
// 使用字符串条件
List<User> list = userService.selectBy("age > ? AND gender = ?", 18, 0);

// 使用 Wrapper 条件
List<User> list = userService.selectBy(condition -> condition.gt(User$.age, 18).and().eq(User$.gender, 0));

// 带分页
List<User> list = userService.selectBy(pageHelper.getPaging(), Wrapper.where(Cond.gt(User$.age, 18)));

// 指定返回类型
List<UserDTO> dtoList = userService.selectBy(UserDTO.class, Wrapper.where(Cond.eq(User$.status$, UserStatus.NORMAL)));
```

### 4. 查询全部

```java
// 查询所有
List<User> list = userService.select();

// 带分页
List<User> list = userService.select(pageHelper.getPaging());

// 指定返回类型
List<UserDTO> dtoList = userService.select(UserDTO.class);
```

### 5. 统计查询

```java
// 统计全部
int count = userService.count();

// 根据条件统计
int count = userService.countBy(Wrapper.where(Cond.eq(User$.status$, UserStatus.NORMAL)));

// 使用 Select 对象统计
int count = userService.count(select);
```

### 6. 查询 Map 结果

```java
// 查询单条 Map
Map<String, Object> map = userService.selectMap(select);

// 查询多条 Map
List<Map<String, Object>> mapList = userService.selectMapList(select);
```

### 7. 分页查询

```java
// 使用 PageHelper
ResultData<User> result = userService.paging(select, pageHelper);

// 指定页码和每页数量
ResultData<User> result = userService.paging(select, 1, 20);

// 指定返回类型
ResultData<UserDTO> result = userService.paging(UserDTO.class, select, pageHelper);
```

---

## 三、Select 对象 API

### 1. 设置字段

| 方法 | 说明 | 示例 |
| :--- | :--- | :--- |
| `column(column1, column2, ...)` | 指定查询字段 | `select.column(User$.id, User$.userName)` |
| `column(fun, alias)` | 使用 SQL 函数 | `select.column(SqlFun.count(User$.id), "count")` |
| `column(tableAlias, column, alias)` | 指定表别名 | `select.column("u", "name", "userName")` |

### 2. 设置表

| 方法 | 说明 | 示例 |
| :--- | :--- | :--- |
| `setTable(class)` | 设置主表 | `select.setTable(User.class)` |
| `setTable(tableName)` | 设置表名 | `select.setTable("t_user")` |

### 3. 表连接

| 方法 | 说明 | 示例 |
| :--- | :--- | :--- |
| `innerJoin(class)` | 内连接 | `select.innerJoin(UserAddress.class)` |
| `leftJoin(class)` | 左连接 | `select.leftJoin(UserAddress.class)` |
| `rightJoin(class)` | 右连接 | `select.rightJoin(UserAddress.class)` |
| `fullJoin(class)` | 全连接 | `select.fullJoin(UserAddress.class)` |

### 4. 连接条件

```java
select.innerJoin(UserAddress.class)
    .on().eq(UserAddress$.userId$, User$.id$)
    .and().gt(UserAddress$.id$, 0);
```

### 5. 排序

| 方法 | 说明 | 示例 |
| :--- | :--- | :--- |
| `orderBy(column)` | 升序 | `select.orderBy(User$.createTime)` |
| `orderByDesc(column)` | 降序 | `select.orderByDesc(User$.createTime)` |

### 6. 分页

```java
select.page(0, 10);  // 第1页，每页10条
```

### 7. 分组

```java
select.groupBy(User$.gender);
select.having().eq("count", 5);
```

---

## 四、Where 条件构建

### 1. 链式条件

```java
select.where()
    .eq(User$.age, 18)                    // age = 18
    .and().gt(User$.createTime$, "2024-01-01")  // AND create_time > '2024-01-01'
    .or().like(User$.userName, "%test%"); // OR user_name LIKE '%test%'
```

### 2. 支持的条件方法

| 方法 | SQL 表达式 | 示例 |
| :--- | :--- | :--- |
| `eq(column, value)` | `column = value` | `eq(User$.age, 18)` |
| `ne(column, value)` | `column != value` | `ne(User$.status, 0)` |
| `gt(column, value)` | `column > value` | `gt(User$.age, 18)` |
| `lt(column, value)` | `column < value` | `lt(User$.age, 30)` |
| `ge(column, value)` | `column >= value` | `ge(User$.age, 18)` |
| `le(column, value)` | `column <= value` | `le(User$.age, 30)` |
| `like(column, value)` | `column LIKE value` | `like(User$.name, "%test%")` |
| `notLike(column, value)` | `column NOT LIKE value` | `notLike(User$.name, "%test%")` |
| `in(column, values)` | `column IN (values)` | `in(User$.status, 1, 2, 3)` |
| `notIn(column, values)` | `column NOT IN (values)` | `notIn(User$.status, 0)` |
| `between(column, start, end)` | `column BETWEEN start AND end` | `between(User$.age, 18, 30)` |
| `isNull(column)` | `column IS NULL` | `isNull(User$.email)` |
| `isNotNull(column)` | `column IS NOT NULL` | `isNotNull(User$.email)` |

---

## 五、SQL 函数

### 聚合函数

```java
SqlFun.count(User$.id)          // COUNT(id)
SqlFun.sum(User$.age)           // SUM(age)
SqlFun.avg(User$.age)           // AVG(age)
SqlFun.max(User$.age)           // MAX(age)
SqlFun.min(User$.age)           // MIN(age)
```

### 日期函数

```java
SqlFun.now()                        // NOW()
SqlFun.date_format(User$.createTime$, "%Y-%m-%d")  // DATE_FORMAT(create_time, '%Y-%m-%d')
SqlFun.year(User$.createTime$)      // YEAR(create_time)
SqlFun.month(User$.createTime$)     // MONTH(create_time)
SqlFun.day(User$.createTime$)       // DAY(create_time)
```

### 字符串函数

```java
SqlFun.concat(User$.userName, "@", User$.email)  // CONCAT(user_name, '@', email)
SqlFun.upper(User$.userName)                     // UPPER(user_name)
SqlFun.lower(User$.userName)                     // LOWER(user_name)
SqlFun.length(User$.userName)                    // LENGTH(user_name)
```
