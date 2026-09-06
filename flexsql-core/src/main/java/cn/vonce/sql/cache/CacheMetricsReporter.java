package cn.vonce.sql.cache;

import java.util.Map;

/**
 * 缓存指标导出 SPI（极简、零依赖）。
 * <p>core 自身不引任何监控库（Micrometer/Dropwizard 等）。
 * 用户/框架侧实现该接口并通过 {@link CacheMetrics#addReporter} 注册，
 * 框架会以固定周期（用户决定）调用 {@link CacheMetrics#reportNow()} 触发。</p>
 *
 * <p>典型实现：
 * <ul>
 *   <li>slf4j 周期日志（spring/solon 模块默认带）</li>
 *   <li>Micrometer Counter / Gauge 注册到 Actuator</li>
 *   <li>推到 Prometheus Pushgateway</li>
 *   <li>写入自定义 metrics 服务</li>
 * </ul></p>
 *
 * <p>实现要点：
 * <ul>
 *   <li>方法内请勿长时间阻塞（框架可能在周期任务线程上调用）。</li>
 *   <li>{@code global} 与 {@code byTable} 都是不可变快照；多线程读安全。</li>
 *   <li>{@code byTable} 中表名可能上千张，按访问热度排序处理更有价值。</li>
 * </ul></p>
 *
 * @author Jovi
 * @version 1.0
 */
@FunctionalInterface
public interface CacheMetricsReporter {

    /**
     * 收到一次指标快照。
     *
     * @param global  全局累计快照（不可变）
     * @param byTable 按表快照（不可变、有序；访问次数为 0 的表通常不出现）
     */
    void onSnapshot(QueryCacheStats global, Map<String, QueryCacheStats> byTable);
}