# 查询缓存（FlexSQL Query Cache）

> 适用版本：1.7.2+，依赖 `flexsql-spring` / `flexsql-solon` 即可；`flexsql-caffeine` 为可选后端。

## 一、默认状态

**查询缓存默认关闭**（`CacheMode.OFF`）。需要显式启用才不会产生任何开销与副作用。

启用后会按以下顺序自动应用：

1. 编程式 `SqlBeanServices.setCacheConfig(...)` — 启动早期直接调用（最高优先级）
2. 容器内 `SqlBeanConfig` Bean — spring/solon 启动时拾取（见下）
3. **yml / properties 绑定**（`flexsql.cache.*`）— 用户没写 Bean 时回退到这里
4. **优先级**：编程式 > Bean > yml；Bean 不会读取 yml（避免覆盖用户显式意图）

> 多个 `SqlBeanConfig` Bean 同时存在会启动失败（`IllegalStateException`），避免歧义。
> yml 字段值非法（如 `flexsql.cache.mode: invalid`）会启动失败（`IllegalStateException`），
> 启动期错误比运行时静默降级更易排查。

## 二、模式：本地 vs 分布式（二选一）

| 模式 | 实现 | 适用场景 | 关键约束 |
|---|---|---|---|
| `LOCAL` | 进程内缓存（默认 `SimpleQueryCache` 零依赖 LRU+TTL，可选 `CaffeineQueryCache`） | 单节点部署 | **不要**在多实例部署使用（各节点缓存独立，写失效清不到其它节点，会脏读） |
| `REDIS` | 中心化 Redis（`RedisQueryCache`） | 多实例/分布式部署 | 必须用户提供 `RedisOps` 实现（`flexsql-core` 不带 Redis 客户端） |

> 不要混用 L1+L2（本地 + Redis 叠加）：除非额外加 pub/sub 失效广播，否则分布式下会脏读。

## 三、配置方式（四种）

### 1. 编程式（最直接）

```java
// LOCAL
SqlBeanServices.setCacheConfig(QueryCacheConfig.local(1000, 600, 0));
// redis
SqlBeanServices.setCacheConfig(QueryCacheConfig.redis(myRedisOps, 600));
// 关闭
SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
```

### 2. 容器内 Bean（推荐用于生产）

```java
@Bean
public SqlBeanConfig sqlBeanConfig() {
    SqlBeanConfig cfg = new SqlBeanConfig();
    cfg.setCacheMode(CacheMode.LOCAL);
    cfg.setLocalMaximumSize(1000L);
    cfg.setLocalExpireAfterWrite(600L);
    cfg.setLocalExpireAfterAccess(0L);     // 0 = 不设置
    cfg.setCacheMetricsLogIntervalSeconds(60L); // 0 = 关闭指标周期日志
    return cfg;
}

// redis 模式必须注册 RedisOps Bean
@Bean
public RedisOps redisOps() {
    return new JedisRedisOps(...); // 用户自己实现
}
```

### 3. yml / properties 直接绑定（最简 — 推荐新手）

**用户只写 yml，不写一行 Java 代码**。框架启动时从 `flexsql.cache.*` 读字段，
自动激活配置。**不写任何 `flexsql.cache.*` → 零干扰**，缓存保持默认 OFF。

```yaml
# application.yml（或 app.yml / application.properties）
flexsql:
  cache:
    mode: local                              # off | local | redis
    local:
      maximum-size: 1000                     # 单机最大条目数
      expire-after-write: 600                # 写入后过期秒数（0 = 不设）
      expire-after-access: 0                  # 访问后过期秒数（0 = 不设）
    redis:
      expire-after-write: 600                 # 分布式模式下写入后过期秒数（0 = 用 RedisOps 默认）
    metrics-log-interval-seconds: 60         # slf4j 周期日志间隔（0 = 关闭）
```

字段映射：

| yml key | 含义 | 默认 |
|---|---|---|
| `flexsql.cache.mode` | `off` / `local` / `redis`，**非法值启动失败** | 未设置 = 不激活 |
| `flexsql.cache.local.maximum-size` | LOCAL 模式最大条目数 | 1000 |
| `flexsql.cache.local.expire-after-write` | LOCAL 模式写入后过期（秒） | 0 |
| `flexsql.cache.local.expire-after-access` | LOCAL 模式访问后过期（秒） | 0 |
| `flexsql.cache.redis.expire-after-write` | REDIS 模式写入后过期（秒） | RedisOps 默认 |
| `flexsql.cache.metrics-log-interval-seconds` | slf4j 周期日志间隔 | 60 |

> **优先级提醒**：如果用户同时配置了 yml **又**写了 `@Bean SqlBeanConfig`，以 **Bean 为准**
> （Bean 是显式意图，yml 只在没有 Bean 时生效）。
> `flexsql.cache.mode=redis` 但容器内无 `RedisOps` Bean → WARN 降级 OFF（不阻断启动）。

### 4. yml + Bean 桥接（高级：需要更细颗粒控制时）

如果你需要在 Bean 构造里做条件判断/动态计算，不希望框架自动绑定，可以自己写桥接 Bean：

```java
@Bean
public SqlBeanConfig sqlBeanConfig(
        @Value("${flexsql.cache.mode:off}") String mode,
        @Value("${flexsql.cache.local.maximum-size:1000}") long max,
        @Value("${flexsql.cache.local.expire-after-write:600}") long ttl) {
    SqlBeanConfig cfg = new SqlBeanConfig();
    cfg.setCacheMode(CacheMode.valueOf(mode.toUpperCase()));
    cfg.setLocalMaximumSize(max);
    cfg.setLocalExpireAfterWrite(ttl);
    return cfg;
}
```

```yaml
flexsql:
  cache:
    mode: local
    local:
      maximum-size: 1000
      expire-after-write: 600
```

> 这是旧的"间接"方案。现在更推荐第 3 节"yml 直接绑定"——零 Java 代码、字段由框架统一管理。

## 四、可选：Caffeine 后端

默认 LOCAL 使用内置 `SimpleQueryCache`（LRU+TTL，零依赖）。如需更高命中率/更低 GC 压力，加 `flexsql-caffeine`：

```xml
<dependency>
    <groupId>cn.vonce</groupId>
    <artifactId>flexsql-caffeine</artifactId>
    <version>1.7.2</version>
</dependency>
```

启动时注册一次：

```java
CaffeineQueryCache.install(); // 之后 QueryCacheConfig.local(...) 自动产出 Caffeine
```

Caffeine 版本锁 **2.9.2**（3.x 需 Java 11+）。

## 五、正确性保证（1.7.2 起默认开启）

| 项 | 实现 |
|---|---|
| **多数据源隔离** | 缓存 key 含 `dataSource` 维度 |
| **多租户/动态 Schema 隔离** | 缓存 key 含 `tenantId`、`schema` |
| **空结果防穿透** | `NullSentinel` 哨兵避免反复回源查同一空值 |
| **事务提交后失效** | 通过可插拔 SPI 注册事务钩子（spring：`TransactionSynchronization`；solon：`TranUtils.listen(afterCommit)`），写操作在事务提交后才失效 |
| **并发击穿保护** | Double-Checked Locking，同一 key 同时只回源 1 次 |

## 六、命中率指标

core 内置 `CacheMetrics`（`LongAdder`，零依赖、热路径开销可忽略），框架侧默认带 slf4j 周期 reporter。

```java
// 编程式读取
QueryCacheStats s = CacheMetrics.snapshot();
log.info("hit={} miss={} hitRate={}", s.getHitCount(), s.getMissCount(), s.getHitRate());

// 按表维度
Map<String, QueryCacheStats> byTable = CacheMetrics.snapshotByTable();
byTable.forEach((table, st) -> log.info("{} {}", table, st));

// 自定义导出器（推 Micrometer 等）
CacheMetrics.register((global, byTable) -> myRegistry.gauge("cache.hit", global.getHitCount()));
```

更多接入示例见 [doc/METRICS.md](METRICS.md)。

## 七、API 一览

| API | 作用 |
|---|---|
| `SqlBeanServices.setCacheConfig(QueryCacheConfig)` | 全局激活（编程式优先） |
| `SqlBeanServices.getCacheConfig()` | 读取当前全局配置 |
| `SqlBeanServices.applyFromSqlBeanConfig(SqlBeanConfig, RedisOps)` | spring/solon 内部调用；用户极少直接使用 |
| `SqlBeanServices.caching(SqlBeanService)` | 按全局配置包裹（OFF 时返回原对象） |
| `SqlBeanServices.caching(SqlBeanService, QueryCache)` | 自定义实现包裹，不受全局开关影响 |
| `QueryCacheConfig.off()` / `.local(...)` / `.redis(...)` / `.custom(...)` | 4 种工厂 |
| `CacheMetrics.snapshot()` / `.snapshotByTable()` / `.reset()` / `.setEnabled(false)` | 指标查询与控制 |
| `CacheMetrics.addReporter(...)` / `.removeReporter(...)` / `.reportNow()` | 自定义导出器 |

## 八、常见问题

**Q：开了缓存但代码改了，怎么强制失效？**
A：调用 `SqlBeanServices.getCacheConfig().getCache().clear()`；或等待 TTL 到期；或重启应用。

**Q：分布式部署用了 LOCAL 怎么办？**
A：会脏读。把 mode 改成 `REDIS`，并注册 `RedisOps` Bean。

**Q：redis 模式但没有 `RedisOps` Bean 怎么办？**
A：WARN 日志 + 自动降级 OFF，不阻断启动。

**Q：业务已经有自己的 Redis 客户端实现，不想再写一个？**
A：`RedisOps` 是 `flexsql-core` 里的接口（5 个方法：`get`/`set`/`delete`/`sadd`/`smembers`/`srem`），写一个 ~30 行的适配器即可。