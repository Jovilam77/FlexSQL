package cn.vonce.sql.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;

/**
 * 查询缓存指标收集器（全局、低开销、零第三方依赖）。
 * <p>在 {@link CacheableSqlBeanService} 的命中 / 未命中 / 回源 / 回填 / 失效五个点计数，
 * 用于判断「缓存到底有没有收益」——命中率长期偏低时应关闭缓存或调整缓存对象。</p>
 *
 * <p>实现要点：
 * <ul>
 *   <li>使用 {@link LongAdder} 计数，高并发下几乎无竞争，单次 {@code increment()} 开销可忽略。</li>
 *   <li>同时维护<b>全局</b>与<b>按表</b>两份计数；按表登记上限 {@value #MAX_TRACKED_TABLES} 张，
 *       超出后仅计入全局（避免异常多的表名导致内存增长）。</li>
 *   <li>可用 {@link #setEnabled(boolean)} 整体关闭，关闭后记录方法直接短路返回。</li>
 *   <li>core 不依赖 Micrometer 等任何指标库：需要接入监控体系的，定时调用
 *       {@link #snapshot()} / {@link #snapshotByTable()} 把快照推到自己的 metrics 注册中心即可。</li>
 * </ul></p>
 *
 * <pre>{@code
 * // 全局
 * QueryCacheStats s = CacheMetrics.snapshot();
 * System.out.println(s.getHitRate());
 * // 按表
 * for (Map.Entry<String, QueryCacheStats> e : CacheMetrics.snapshotByTable().entrySet()) { ... }
 * }</pre>
 *
 * @author Jovi
 * @version 1.0
 */
public final class CacheMetrics {

    /** 按表登记的最大表数，超出后新表只计入全局。 */
    public static final int MAX_TRACKED_TABLES = 1024;

    private static volatile boolean enabled = true;

    private static final Counters GLOBAL = new Counters();
    private static final ConcurrentHashMap<String, Counters> BY_TABLE = new ConcurrentHashMap<>();

    /**
     * 已 注册的指标导出器列表。CopyOnWriteArrayList 适合读多写少场景，
     * 这里的写（addReporter/removeReporter）仅发生在启动期/关闭期，热路径无影响。
     */
    private static final List<CacheMetricsReporter> REPORTERS = new CopyOnWriteArrayList<>();

    private CacheMetrics() {
    }

    /** 是否开启指标收集（默认开启）。关闭后所有记录方法短路，开销为零。 */
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** 清零全局与按表计数。 */
    public static void reset() {
        GLOBAL.reset();
        BY_TABLE.clear();
    }

    /** 记录一次命中。table 可为 null（仅计入全局）。 */
    static void recordHit(String table) {
        if (!enabled) {
            return;
        }
        GLOBAL.hit.increment();
        Counters c = tableCounters(table);
        if (c != null) {
            c.hit.increment();
        }
    }

    /** 记录一次未命中（首次读取缓存未取到值）。 */
    static void recordMiss(String table) {
        if (!enabled) {
            return;
        }
        GLOBAL.miss.increment();
        Counters c = tableCounters(table);
        if (c != null) {
            c.miss.increment();
        }
    }

    /** 记录一次真实回源（落到数据库执行）。 */
    static void recordLoad(String table) {
        if (!enabled) {
            return;
        }
        GLOBAL.load.increment();
        Counters c = tableCounters(table);
        if (c != null) {
            c.load.increment();
        }
    }

    /** 记录一次回填。 */
    static void recordPut(String table) {
        if (!enabled) {
            return;
        }
        GLOBAL.put.increment();
        Counters c = tableCounters(table);
        if (c != null) {
            c.put.increment();
        }
    }

    /** 记录一次失效触发。 */
    static void recordEvict(String table) {
        if (!enabled) {
            return;
        }
        GLOBAL.evict.increment();
        Counters c = tableCounters(table);
        if (c != null) {
            c.evict.increment();
        }
    }

    /** 全局快照。 */
    public static QueryCacheStats snapshot() {
        return GLOBAL.toStats();
    }

    /** 按表快照（不可变副本）；表名为 null 的计数不出现在其中，仅在全局快照里。 */
    public static Map<String, QueryCacheStats> snapshotByTable() {
        Map<String, QueryCacheStats> result = new LinkedHashMap<>();
        for (Map.Entry<String, Counters> e : BY_TABLE.entrySet()) {
            result.put(e.getKey(), e.getValue().toStats());
        }
        return Collections.unmodifiableMap(result);
    }

    /** 注册一个指标导出器。重复注册同一实例会被忽略；传入 null 等价于无操作。 */
    public static void addReporter(CacheMetricsReporter reporter) {
        if (reporter == null) {
            return;
        }
        if (!REPORTERS.contains(reporter)) {
            REPORTERS.add(reporter);
        }
    }

    /** 移除已注册的导出器；不存在则静默忽略；传入 null 等价于无操作。 */
    public static void removeReporter(CacheMetricsReporter reporter) {
        if (reporter == null) {
            return;
        }
        REPORTERS.remove(reporter);
    }

    /** 当前已注册的导出器数量（用于测试与诊断）。 */
    public static int reporterCount() {
        return REPORTERS.size();
    }

    /**
     * 立刻取一次快照并分发给所有已注册的导出器。
     * <p>框架侧的周期任务 / 用户手动触发都可以调用本方法。
     * 没有注册导出器时本方法等价于一次空转（但仍然构造快照，开销极低）。</p>
     */
    public static void reportNow() {
        QueryCacheStats global = snapshot();
        Map<String, QueryCacheStats> byTable = snapshotByTable();
        for (CacheMetricsReporter r : REPORTERS) {
            try {
                r.onSnapshot(global, byTable);
            } catch (Throwable ignored) {
                // reporter 异常不应影响其他 reporter，更不应影响主流程；调用方可在外层加日志。
            }
        }
    }

    /**
     * 取（必要时创建）某张表的计数器；表名为 null 或已达登记上限时返回 null（仅计入全局）。
     */
    private static Counters tableCounters(String table) {
        if (table == null) {
            return null;
        }
        Counters c = BY_TABLE.get(table);
        if (c != null) {
            return c;
        }
        if (BY_TABLE.size() >= MAX_TRACKED_TABLES) {
            return null;
        }
        Counters created = new Counters();
        Counters prev = BY_TABLE.putIfAbsent(table, created);
        return prev != null ? prev : created;
    }

    private static final class Counters {
        final LongAdder hit = new LongAdder();
        final LongAdder miss = new LongAdder();
        final LongAdder load = new LongAdder();
        final LongAdder put = new LongAdder();
        final LongAdder evict = new LongAdder();

        QueryCacheStats toStats() {
            return new QueryCacheStats(hit.sum(), miss.sum(), load.sum(), put.sum(), evict.sum());
        }

        void reset() {
            hit.reset();
            miss.reset();
            load.reset();
            put.reset();
            evict.reset();
        }
    }
}
