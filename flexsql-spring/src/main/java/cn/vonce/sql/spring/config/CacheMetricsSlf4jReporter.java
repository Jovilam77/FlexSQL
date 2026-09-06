package cn.vonce.sql.spring.config;

import cn.vonce.sql.cache.CacheMetrics;
import cn.vonce.sql.cache.CacheMetricsReporter;
import cn.vonce.sql.cache.QueryCacheStats;
import cn.vonce.sql.config.SqlBeanConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring 端的周期日志 reporter。
 * <p>由 {@link SqlBeanCacheAutoConfig} 在 {@code cacheMetricsLogIntervalSeconds > 0} 时注册到
 * {@link CacheMetrics#addReporter}；周期触发 {@link CacheMetrics#reportNow()}，
 * 当前实现是简单地把全局快照输出 INFO 日志，按表 top-N 单独输出 DEBUG 日志。</p>
 *
 * <p>日志门面使用 spring-core 传递依赖的 commons-logging（jcl），最终通过
 * jcl-over-slf4j 桥接到用户实际的日志实现（logback/log4j 等），无需额外依赖。</p>
 *
 * <p>线程模型：{@link ScheduledExecutorService} 单线程 daemon，1s 优雅关闭。
 * 不依赖 Spring 的 {@code @Scheduled}（避免引入 {@code @EnableScheduling}）。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public class CacheMetricsSlf4jReporter implements CacheMetricsReporter, InitializingBean, DisposableBean {

    private static final Log log = LogFactory.getLog("FlexsqlCache");
    private static final int TOP_TABLES_IN_INFO = 3;
    private static final int TOP_TABLES_IN_DEBUG = 10;

    private final long intervalSeconds;
    private final String tagPrefix;
    private volatile ScheduledExecutorService scheduler;
    private final AtomicLong sequence = new AtomicLong();

    public CacheMetricsSlf4jReporter(SqlBeanConfig cfg) {
        this.intervalSeconds = cfg.getCacheMetricsLogIntervalSeconds();
        this.tagPrefix = "[FlexsqlCache]";
    }

    @Override
    public void afterPropertiesSet() {
        if (intervalSeconds <= 0) {
            if (log.isDebugEnabled()) {
                log.debug(tagPrefix + " CacheMetrics 周期日志已关闭（intervalSeconds=" + intervalSeconds + "）");
            }
            return;
        }
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "flexsql-cache-metrics");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        if (log.isDebugEnabled()) {
            log.debug(tagPrefix + " CacheMetrics 周期日志已启动，间隔 " + intervalSeconds + "s");
        }
    }

    @Override
    public void destroy() {
        ScheduledExecutorService s = this.scheduler;
        this.scheduler = null;
        if (s != null) {
            s.shutdown();
            try {
                if (!s.awaitTermination(1, TimeUnit.SECONDS)) {
                    s.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                s.shutdownNow();
            }
        }
    }

    private void tick() {
        try {
            CacheMetrics.reportNow();
        } catch (Throwable t) {
            // 周期任务里出现任何异常都不能让定时线程死亡
            log.warn(tagPrefix + " CacheMetrics 周期上报异常：" + t);
        }
    }

    @Override
    public void onSnapshot(QueryCacheStats global, Map<String, QueryCacheStats> byTable) {
        long seq = sequence.incrementAndGet();
        if (log.isInfoEnabled()) {
            log.info(tagPrefix + " seq=" + seq
                    + " hit=" + global.getHitCount()
                    + " miss=" + global.getMissCount()
                    + " load=" + global.getLoadCount()
                    + " put=" + global.getPutCount()
                    + " evict=" + global.getEvictCount()
                    + " hitRate=" + formatPercent(global.getHitRate())
                    + " load=" + global.getLoadCount() + " miss=" + global.getMissCount()
                    + " top=" + topTables(byTable, TOP_TABLES_IN_INFO));
        }
        if (log.isDebugEnabled()) {
            log.debug(tagPrefix + " 按表统计（top " + TOP_TABLES_IN_DEBUG + "）："
                    + topTables(byTable, TOP_TABLES_IN_DEBUG));
        }
    }

    /** 按访问总量排序，取前 n 张表，简短摘要 [table[stats], ...]。 */
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