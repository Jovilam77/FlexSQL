package cn.vonce.sql.solon.config;

import cn.vonce.sql.cache.CacheMetrics;
import cn.vonce.sql.cache.CacheMetricsReporter;
import cn.vonce.sql.cache.QueryCacheStats;
import cn.vonce.sql.config.SqlBeanConfig;
import org.noear.solon.core.util.RunUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Solon 端的周期 slf4j reporter。
 * <p>由 {@link AutoConfigSolon} 在 {@link SqlBeanConfig#cacheMetricsLogIntervalSeconds} 大于 0 时
 * 通过 {@code new CacheMetricsSlf4jReporter(cfg).start()} 注册到 {@link CacheMetrics#addReporter}；
 * 周期触发 {@link CacheMetrics#reportNow()}。使用 Solon 提供的 {@code RunUtil} 调度
 * （daemon 线程），不依赖任何特定框架的 scheduler。</p>
 *
 * <p><b>本类不再使用 {@code @Component}</b>——由 {@link AutoConfigSolon} 统一管理生命周期，
 * 这样无论是 Bean 配置（{@code @Bean SqlBeanConfig}）还是 yml 配置
 * （{@code flexsql.cache.*}）都能拿到正确的 cfg（yml 翻译出的 cfg <b>不</b>注册成 Bean，
 * solon 自动的 {@code @Inject} 抓不到）。</p>
 *
 * <p>调度线程在 JVM 退出时自然结束（daemon），无显式 stop 钩子。</p>
 *
 * @author Jovi
 * @version 1.1
 */
public class CacheMetricsSlf4jReporter implements CacheMetricsReporter {

    private static final Logger log = LoggerFactory.getLogger("FlexsqlCache");
    private static final int TOP_TABLES_IN_INFO = 3;
    private static final int TOP_TABLES_IN_DEBUG = 10;

    private final SqlBeanConfig sqlBeanConfig;
    private final String tagPrefix = "[FlexsqlCache]";
    private final AtomicLong sequence = new AtomicLong();
    private volatile ScheduledFuture<?> future;

    /**
     * 无 cfg 构造（interval 默认 60s）。提供给可能想 "裸用" 的扩展路径。
     */
    public CacheMetricsSlf4jReporter() {
        this(new SqlBeanConfig());
    }

    public CacheMetricsSlf4jReporter(SqlBeanConfig cfg) {
        this.sqlBeanConfig = (cfg != null) ? cfg : new SqlBeanConfig();
    }

    /**
     * 启动周期上报。在 {@link AutoConfigSolon} 的 {@code AppBeanLoadEndEvent} 中调用一次即可。
     * 如果间隔 ≤ 0 直接返回，不注册 reporter（缓存指标周期日志关闭）。
     */
    public void start() {
        long intervalSeconds = sqlBeanConfig.getCacheMetricsLogIntervalSeconds();
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

    /**
     * 停止周期上报。一般不需要主动调用（daemon 线程随 JVM 退出），但提供
     * 给"容器重启"测试或集成测试清理用。
     */
    public void stop() {
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
