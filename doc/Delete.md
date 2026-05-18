# Delete 删除

## 一、Delete 对象使用示例

### 1. 基础删除

```java
// 根据条件删除
Delete delete = new Delete();
delete.where().gt(User$.id$, 100).and().lt(User$.id$, 200);
userService.delete(delete);
```

### 2. 使用 Wrapper 条件

```java
// 删除状态为禁用的用户
Delete delete = new Delete();
delete.where(Wrapper.where(Cond.eq(User$.status$, UserStatus.DISABLE)));
userService.delete(delete);
```

---

## 二、DeleteService 接口

### 1. 根据 ID 删除

```java
// 删除单个 ID
int count = userService.deleteById(1L);

// 删除多个 ID
int count = userService.deleteById(1L, 2L, 3L);
```

### 2. 根据条件删除

```java
// 使用字符串条件
int count = userService.deleteBy("status = ?", UserStatus.DISABLE);

// 使用 Wrapper 条件
int count = userService.deleteBy(Wrapper.where(Cond.eq(User$.status$, UserStatus.DISABLE)));
```

### 3. 使用 Delete 对象

```java
Delete delete = new Delete();
delete.where().eq(User$.status$, UserStatus.DISABLE);

// 默认不允许删除全部（where 条件为空会抛异常）
int count = userService.delete(delete);

// 允许删除全部
int count = userService.delete(delete, true);
```

---

## 三、逻辑删除

### 1. 逻辑删除说明

逻辑删除不会真正删除数据，而是将标记字段设置为已删除状态。需要在实体类中使用 `@SqlLogically` 注解标记逻辑删除字段。

### 2. 逻辑删除方法

```java
// 根据 ID 逻辑删除
int count = userService.logicallyDeleteById(1L);
int count = userService.logicallyDeleteById(1L, 2L, 3L);

// 根据条件逻辑删除
int count = userService.logicallyDeleteBy("status = ?", UserStatus.DISABLE);
int count = userService.logicallyDeleteBy(Wrapper.where(Cond.eq(User$.status$, UserStatus.DISABLE)));
```

### 3. 逻辑删除策略

| 策略 | 说明 |
| :--- | :--- |
| `LogicallyStrategy.FILTER` | 默认过滤已删除数据（查询时自动添加条件） |
| `LogicallyStrategy.NOT_FILTER` | 不过滤已删除数据 |

---

## 四、Delete 对象 API

### 1. 设置表

```java
delete.setTable(User.class);      // 使用实体类
delete.setTable("t_user");        // 使用表名
```

### 2. 设置条件

```java
// 方式一：链式条件
delete.where().eq(User$.status$, UserStatus.DISABLE);

// 方式二：Wrapper
delete.where(Wrapper.where(Cond.eq(User$.status$, UserStatus.DISABLE)));

// 方式三：字符串表达式
delete.where("status = ?", UserStatus.DISABLE);
```

### 3. 强制删除全部

```java
// 第二个参数为 true 时允许删除全部
userService.delete(delete, true);
```

---

## 五、注意事项

1. **安全删除**：默认情况下，不带 where 条件的 Delete 操作会抛出异常，防止误删全部数据。
2. **逻辑删除优先**：如果实体类配置了 `@SqlLogically` 注解，建议使用逻辑删除方法。
3. **事务保护**：删除操作建议在事务中执行，以便在出现问题时回滚。

```java
@DbTransactional
public void batchDelete(List<Long> ids) {
    userService.deleteById(ids.toArray(new Long[0]));
}
```
