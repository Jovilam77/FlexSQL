# 指标接入（Cache Metrics → 监控体系）

> 适用版本：1.7.2+（`CacheMetrics` + `CacheMetricsReporter` SPI）。

## 一、为什么需要接入

`CacheMetrics` 本身只做 JVM 级 `LongAdder` 计数（零依赖、热路径开销可忽略），
但**不主动推到任何监控体系**。把指标接进 Micrometer / Actuator / Prometheus 等
监控体系后才能在仪表盘看到、看报警、做容量规划。

## 二、内置 slf4j 周期日志（开箱即用）

spring/solon 模块**自带** `CacheMetricsSlf4jReporter`，默认周期 60s 输出 INFO 日志：

```text
[FlexsqlCache] seq=42 hit=1234 miss=567 load=890 put=900 evict=10 hitRate=68.5% miss=567 load=890 top=t_user[hit=500 miss=200 ...], t_order[...], t_product[...]
```

开关：`SqlBeanConfig.setCacheMetricsLogIntervalSeconds(0L)` 关闭，>0 设间隔秒数。
该字段可通过 `@Value("${flexsql.cache.metrics-log-interval:60}")` 间接从 yml 读。

## 三、接到 Micrometer（Spring Boot Actuator / Prometheus）

在用户项目里加 `micrometer-registry-prometheus` 依赖后，写一个 `CacheMetricsReporter` 实现即可：

```java
@Component
public class CacheMetricsMicrometerReporter implements CacheMetricsReporter {

    private final MeterRegistry registry;
    private final AtomicLong hitGauge = new AtomicLong();
    private final AtomicLong missGauge = new AtomicLong();

    public CacheMetricsMicrometerReporter(MeterRegistry registry) {
        this.registry = registry;
        // CacheMetrics 是 static 收集器，Gauge 只需注册一次
        Gauge.builder("flexsql.cache.hit", hitGauge, AtomicLong::get).register(registry);
        Gauge.builder("flexsql.cache.miss", missGauge, AtomicLong::get).register(registry);
    }

    @Override
    public void onSnapshot(QueryCacheStats global, Map<String, QueryCacheStats> byTable) {
        hitGauge.set(global.getHitCount());
        missGauge.set(global.getMissCount());
        // 推到 Pushgateway / Prometheus / CloudWatch / 任意 registry
    }
}
```

然后让它在 Spring 启动期被注册到 `CacheMetrics`：

```java
@Component
public class CacheMetricsBootstrap implements ApplicationRunner {

    private final List<CacheMetricsReporter> reporters;

    public CacheMetricsBootstrap(List<CacheMetricsReporter> reporters) {
        this.reporters = reporters;
    }

    @Override
    public void run(ApplicationArguments args) {
        reporters.forEach(CacheMetrics::addReporter);
    }
}
```

或更简单：在 reporter 构造里直接 `CacheMetrics.addReporter(this)`（单例 Bean）。

**Solon 侧**用 `@Init` 方法或 `BeanLifecycle`：

```java
@Component
public class CacheMetricsMicrometerReporter implements CacheMetricsReporter {
    // 构造完成后
    @PostConstruct
    public void register() { CacheMetrics.addReporter(this); }
    @PreDestroy
    public void unregister() { CacheMetrics.removeReporter(this); }

    @Override
    public void onSnapshot(QueryCacheStats g, Map<String, QueryCacheStats> t) { ... }
}
```

## 四、接到 Dropwizard Metrics

```java
import com.codahale.metrics.MetricRegistry;

public class CacheMetricsDropwizardReporter implements CacheMetricsReporter {
    private final Counter hit, miss, put, evict, load;
    public CacheMetricsDropwizardReporter(MetricRegistry registry) {
        hit = registry.counter("flexsql.cache.hit");
        miss = registry.counter("flexsql.cache.miss");
        // ...
    }
    @Override
    public void onSnapshot(QueryCacheStats g, Map<String, QueryCacheStats> t) {
        // 注意 Dropwizard Counter 是单调递增的，只能加增量。CacheMetrics.snapshot() 返回累计快照，
        // 因此这里适合 Gauge / Histogram（绑定当前值），或维护 last* 字段做 diff。
    }
}
```

## 五、自定义调度周期

`CacheMetrics.reportNow()` 由谁来调用？

| 方式 | 示例 |
|---|---|
| 框架自带 reporter | slf4j reporter 内置 `ScheduledExecutorService` |
| Spring `@Scheduled` | 在 reporter 里 `@Scheduled(fixedRate = 60000) void tick() { CacheMetrics.reportNow(); }`，需要 `@EnableScheduling` |
| Solon `@Scheduled` | 同上 solon 风格 |
| 用户自定义线程 | 完全可控，但记得加 `try/catch` 避免定时线程死亡 |

## 六、统计指标定义

| 指标 | 含义 |
|---|---|
| `hit` | 首次读缓存即取到值 |
| `miss` | 首次读缓存未取到值 |
| `load` | 真正回源（落到数据库）。**并发下 N 个 miss 只会回源 1 次**（击穿保护复用） |
| `put` | 回源后写缓存 |
| `evict` | 写操作触发的失效次数（按表 + schema + 租户 + 数据源计一次） |
| `hitRate` | `hit / (hit + miss)`，长期偏低（< 30%）说明缓存白开销 |

`miss - load` 的差值即"击穿保护复用次数"，差值大说明热点 key 集中。

## 七、关闭指标

```java
CacheMetrics.setEnabled(false); // 全局关闭，所有记录方法短路
```

适合业务初期（缓存冷启动期间指标波动大）或已经接入第三方监控后想关掉 slf4j 输出。