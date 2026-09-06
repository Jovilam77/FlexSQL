package cn.vonce.sql.solon.config;

import cn.vonce.sql.cache.CacheMetrics;
import cn.vonce.sql.cache.CacheMetricsReporter;
import cn.vonce.sql.cache.QueryCacheStats;
import cn.vonce.sql.config.SqlBeanConfig;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.util.RunUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Solon 端的周期 slf4j reporter。
 * <p>由 {@link AutoConfigSolon} 在 SqlBeanConfig.cacheMetricsLogIntervalSeconds > 0 时
 * 注册到 {@link CacheMetrics#addReporter}；周期触发 {@link CacheMetrics#reportNow()}。
 * 使用 Solon 提供的 {@code RunUtil} 调度（daemon 线程），不依赖任何特定框架的 scheduler。</p>
 *
 * <p>日志门面使用 solon 自带的 slf4j 绑定（solon 自身依赖 slf4j）。</p>
 *
 * @author Jovi
 * @version 1.0
 */
@Component
public class CacheMetricsSlf4jReporter implements CacheMetricsReporter {

    private static final Logger log = LoggerFactory.getLogger("FlexsqlCache");
    private static final int TOP_TABLES_IN_INFO = 3;
    private static final int TOP_TABLES_IN_DEBUG = 10;

    @Inject
    private SqlBeanConfig sqlBeanConfig;

    private final String tagPrefix = "[FlexsqlCache]";
    private final AtomicLong sequence = new AtomicLong();
    private volatile ScheduledFuture<?> future;

    /** 显式无参构造给 Solon 用；用户也可手动 new CacheMetricsSlf4jReporter(new SqlBeanConfig())。 */
    public CacheMetricsSlf4jReporter() {
    }

    /** 测试或非 Solon 环境使用。 */
    public CacheMetricsSlf4jReporter(SqlBeanConfig cfg) {
        this.sqlBeanConfig = cfg;
    }

    /** Solon 生命周期开始。 */
    public void init() {
        long intervalSeconds = (sqlBeanConfig != null) ? sqlBeanConfig.getCacheMetricsLogIntervalSeconds() : 60L;
        if (intervalSeconds <= 0) {
            if (log.isDebugEnabled()) {
                log.debug("{} CacheMetrics 周期日志已关闭（intervalSeconds={}）", tagPrefix, intervalSeconds);
            }
            return;
        }
        CacheMetrics.addReporter(this);
        this.future = RunUtil.scheduleWithFixedDelay(this::tick,
                intervalSeconds * 1000L, intervalSeconds * 1000L);
        if (log.isDebugEnabled()) {
            log.debug("{} CacheMetrics 周期日志已启动，间隔 {}s", tagPrefix, intervalSeconds);
        }
    }

    /** Solon 关闭时调用。 */
    public void dispose() {
        ScheduledFuture<?> f = this.future;
        this.future = null;
        if (f != null) {
            f.cancel(false);
        }
        CacheMetrics.removeReporter(this);
    }

    private void tick() {
        try {
            CacheMetrics.reportNow();
        } catch (Throwable t) {
            log.warn("{} CacheMetrics 周期上报异常：{}", tagPrefix, t.toString());
        }
    }

    @Override
    public void onSnapshot(QueryCacheStats global, Map<String, QueryCacheStats> byTable) {
        long seq = sequence.incrementAndGet();
        if (log.isInfoEnabled()) {
            log.info("{} seq={} hit={} miss={} load={} put={} evict={} hitRate={} top={}",
                    tagPrefix, seq,
                    global.getHitCount(), global.getMissCount(),
                    global.getLoadCount(), global.getPutCount(), global.getEvictCount(),
                    formatPercent(global.getHitRate()),
                    topTables(byTable, TOP_TABLES_IN_INFO));
        }
        if (log.isDebugEnabled()) {
            log.debug("{} 按表统计（top {}）：{}",
                    tagPrefix, TOP_TABLES_IN_DEBUG, topTables(byTable, TOP_TABLES_IN_DEBUG));
        }
    }

    private static String topTables(Map<String, QueryCacheStats> byTable, int n) {
        if (byTable == null || byTable.isEmpty() || n <= 0) {
            return "[]";
        }
        return byTable.entrySet().stream()
                .sorted((a, b) -> Long.compare(
                        b.getValue().getTotalCount(),
                        a.getValue().getTotalCount()))
                .limit(n)
                .map(e -> e.getKey() + "[" + e.getValue() + "]")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static String formatPercent(double rate) {
        if (Double.isNaN(rate)) return "NaN";
        return String.format("%.1f%%", rate * 100);
    }
}