# Update 更新

## 一、Update 对象使用示例

### 1. 使用实体类更新

```java
// 根据 ID 更新
User user = userService.selectById(1L);
user.setUserName("NewName");
user.setEmail("new@example.com");
int count = userService.updateByBeanId(user);
```

### 2. 使用 Update 对象更新

```java
// 更新指定字段
userService.update(new Update<User>()
    .set(User::getNickName, "Vicky")
    .set(User::getAge, 19)
    .where().eq(User::getId, 1L));
```

### 3. 复杂更新操作

```java
// UPDATE t_user SET age = age + 1, status = 1 WHERE id = 111
userService.update(new Update<User>()
    .set(User::getStatus, UserStatus.NORMAL)
    .setAdd(User::getAge, User::getAge, 1)
    .where().eq(User::getId, 111));
```

---

## 二、UpdateService 接口

### 1. 根据 ID 更新

```java
// 基础更新
int count = userService.updateById(user, 1L);

// 仅更新非空字段
int count = userService.updateById(user, 1L, true, false);

// 过滤指定字段
int count = userService.updateById(user, 1L, true, false, User$.password$);
```

### 2. 根据实体类 ID 更新

```java
// 使用实体类中的 ID 字段
int count = userService.updateByBeanId(user);

// 仅更新非空字段 + 乐观锁
int count = userService.updateByBeanId(user, true, true);
```

### 3. 根据条件更新

```java
// 使用字符串条件
int count = userService.updateBy(user, "status = ?", UserStatus.DISABLE);

// 使用 Wrapper 条件
int count = userService.updateBy(user, Wrapper.where(Cond.eq(User$.status$, UserStatus.DISABLE)));
```

### 4. 使用 Update 对象

```java
Update<User> update = new Update<>();
update.set(User::getNickName, "NewNickName");
update.where().eq(User::getId, 1L);

// 默认不允许更新全部
int count = userService.update(update);

// 允许更新全部
int count = userService.update(update, true);
```

---

## 三、Update 对象 API

### 1. 设置更新字段

```java
// 设置字段值
update.set(User::getUserName, "Jovi");
update.set(User$.userName, "Jovi");

// 字段自增
update.setAdd(User::getAge, User::getAge, 1);  // age = age + 1

// 字段自减
update.setSub(User::getScore, User::getScore, 10);  // score = score - 10
```

### 2. 设置条件

```java
// 链式条件
update.where().eq(User$.id$, 1L);

// Wrapper 条件
update.where(Wrapper.where(Cond.eq(User$.id$, 1L)));

// 字符串条件
update.where("id = ?", 1L);
```

### 3. 高级配置

```java
// 设置更新模板
update.setUpdateBean(user);

// 仅更新非空字段
update.setUpdateNotNull(true);

// 使用乐观锁
update.setOptimisticLock(true);

// 过滤不更新的字段
update.filterFields(User::getPassword);
```

---

## 四、特殊用法

### 1. 乐观锁更新

```java
@Service
public class UserServiceImpl extends MybatisSqlBeanServiceImpl<User, Long> implements UserService {

    @Override
    public int updateWithLock(User user) {
        // 第三个参数为 true 表示使用乐观锁
        return updateByBeanId(user, true, true);
    }
}
```

### 2. 批量更新

```java
@DbTransactional
public void batchUpdate(List<User> users) {
    for (User user : users) {
        updateByBeanId(user);
    }
}
```

### 3. 条件更新

```java
// 更新所有禁用用户的状态为正常
userService.updateBy(user, Wrapper.where(Cond.eq(User$.status$, UserStatus.DISABLE)));
```

---

## 五、注意事项

1. **安全更新**：默认情况下，不带 where 条件的 Update 操作会抛出异常，防止误更新全部数据。
2. **乐观锁**：使用乐观锁时需要在实体类中配置 `@SqlVersion` 注解。
3. **字段过滤**：使用 `filterFields` 可以排除敏感字段（如密码）的更新。
4. **事务保护**：批量更新操作建议在事务中执行。
