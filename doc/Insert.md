# Insert 插入

## 一、Insert 对象使用示例

### 1. 使用实体类插入

```java
// 单条插入
User user = new User();
user.setUserName("Jovi");
user.setPassword("123456");
user.setEmail("imjovi@qq.com");
userService.insert(user);

// 批量插入
List<User> userList = new ArrayList<>();
for (int i = 0; i < 10; i++) {
    User u = new User();
    u.setUserName("user" + i);
    u.setPassword("password" + i);
    userList.add(u);
}
userService.insert(userList);
```

### 2. 使用 Insert 对象插入

```java
// 单条插入
Insert<User> insert = new Insert<>();
insert.setTable(User.class);
insert.column(User::getId, User::getUserName, User::getPassword)
      .values(IdBuilder.snowflake16(), "Jovi", "123456");
userService.insert(insert);

// 批量插入
Insert<User> batchInsert = new Insert<>();
batchInsert.setTable(User.class);
batchInsert.column(User::getId, User::getUserName)
           .values(1L, "User1")
           .values(2L, "User2")
           .values(3L, "User3");
userService.insert(batchInsert);
```

---

## 二、InsertService 接口

### 1. 插入单条数据

```java
User user = new User();
user.setUserName("test");
user.setPassword("123456");

// 插入并返回受影响行数
int count = userService.insert(user);
```

### 2. 批量插入

```java
List<User> userList = new ArrayList<>();
// ... 添加用户

// 批量插入
int count = userService.insert(userList);
```

### 3. 使用 Insert 对象

```java
Insert<User> insert = new Insert<>();
insert.setTable(User.class);
insert.column(User::getUserName, User::getEmail)
      .values("Jovi", "imjovi@qq.com");
      
int count = userService.insert(insert);
```

---

## 三、Insert 对象 API

### 1. 设置表

```java
insert.setTable(User.class);      // 使用实体类
insert.setTable("t_user");        // 使用表名
```

### 2. 设置字段和值

```java
// 方式一：使用方法引用
insert.column(User::getId, User::getUserName)
      .values(1L, "Jovi");

// 方式二：使用常量
insert.column(User$.id, User$.userName)
      .values(1L, "Jovi");

// 方式三：使用实体对象
User user = new User();
user.setUserName("Jovi");
insert.setInsertBean(user);
```

### 3. 批量添加值

```java
insert.column(User::getId, User::getUserName)
      .values(1L, "User1")
      .values(2L, "User2")
      .values(3L, "User3");
```

---

## 四、特殊用法

### 1. 插入时忽略某些字段

```java
User user = new User();
user.setUserName("Jovi");
user.setPassword("123456");
user.setCreateTime(new Date());  // 这个字段会由 @SqlDefaultValue 自动填充

Insert<User> insert = new Insert<>();
insert.setTable(User.class);
insert.setInsertBean(user);
insert.filterFields(User::getCreateTime);  // 过滤掉 createTime 字段
userService.insert(insert);
```

### 2. 使用 ID 生成器

```java
Insert<User> insert = new Insert<>();
insert.setTable(User.class);
insert.column(User::getId, User::getUserName)
      .values(IdBuilder.snowflake16(), "Jovi");  // 使用雪花 ID
```

### 3. 处理枚举类型

```java
User user = new User();
user.setUserName("Jovi");
user.setStatus(UserStatus.NORMAL);  // 枚举类型会自动转换为对应的 code
userService.insert(user);
```
