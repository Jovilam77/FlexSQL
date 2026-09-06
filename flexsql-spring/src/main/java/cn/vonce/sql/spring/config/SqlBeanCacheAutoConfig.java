package cn.vonce.sql.spring.config;

import cn.vonce.sql.cache.CacheableSqlBeanService;
import cn.vonce.sql.cache.RedisOps;
import cn.vonce.sql.cache.SqlBeanServices;
import cn.vonce.sql.config.CacheMode;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.java.datasource.DataSourceContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 查询缓存自动接线配置。
 * <p>注册 {@link CacheableSqlBeanServicePostProcessor}，使其在 SqlBeanService bean 初始化后按
 * 全局缓存配置自动包裹为带查询缓存的代理。</p>
 * <p>同时通过可插拔 SPI 向 core 注册「当前数据源名解析器」与「事务同步钩子」，使缓存键能区分多数据源、
 * 且写操作在事务提交后才失效（避免脏数据窗口）。这两个能力由具体框架提供，core 本身不依赖 Spring/Solon。</p>
 *
 * <p><b>Bean 自动拾取规则</b>：
 * <ul>
 *   <li>检测到 {@link SqlBeanConfig} Bean → 把 cache 字段翻译为 {@link cn.vonce.sql.cache.QueryCacheConfig}，
 *       通过 {@link SqlBeanServices#applyFromSqlBeanConfig} 应用到全局。</li>
 *   <li>检测到 {@link RedisOps} Bean（用户自定义实现）→ 作为 REDIS 模式的实现拾取。</li>
 *   <li>多个 {@link SqlBeanConfig} Bean → fail-fast 抛异常（避免歧义）。</li>
 *   <li>用户已编程式调用 {@link SqlBeanServices#setCacheConfig} → Bean 不覆盖（编程式优先）。</li>
 *   <li>没有任何 Bean → 默认 OFF，零干扰。</li>
 * </ul>
 *
 * @author Jovi
 * @version 1.1
 */
@Configuration
public class SqlBeanCacheAutoConfig {

    /**
     * Spring 4.1 不支持 {@code ObjectProvider}（4.3 才有），改用 {@code List<T>} 注入
     * （spring 4.0+ 已支持集合 / 数组注入）。
     */
    @Bean
    public CacheableSqlBeanServicePostProcessor cacheableSqlBeanServicePostProcessor(
            List<SqlBeanConfig> sqlBeanConfigs,
            List<RedisOps> redisOpsList) {
        // 当前数据源名（多数据源 / @DbSwitch 场景），供缓存键区分数据源，避免跨数据源串数据。
        CacheableSqlBeanService.setDataSourceResolver(DataSourceContextHolder::getDataSource);
        // 事务同步：在事务提交后才执行缓存失效，避免「提交前失效导致并发读回填旧值」。
        CacheableSqlBeanService.setTransactionSynchronization(new CacheableSqlBeanService.CacheTransactionSynchronization() {
            @Override
            public boolean isActive() {
                return TransactionSynchronizationManager.isActualTransactionActive();
            }

            @Override
            public void executeAfterCommit(Runnable action) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
            }
        });

        // ===== Bean 拾取：把 SqlBeanConfig.cache* 应用到全局 QueryCacheConfig =====
        if (sqlBeanConfigs != null && sqlBeanConfigs.size() > 1) {
            throw new IllegalStateException(
                    "FlexSQL 检测到 " + sqlBeanConfigs.size() + " 个 SqlBeanConfig Bean，请只保留一个。" +
                            "多个 SqlBeanConfig 会造成 cache 配置歧义。");
        }
        SqlBeanConfig cfg = (sqlBeanConfigs == null || sqlBeanConfigs.isEmpty()) ? null : sqlBeanConfigs.get(0);
        RedisOps redisOps = (redisOpsList == null || redisOpsList.isEmpty()) ? null : redisOpsList.get(0);
        if (cfg != null) {
            // REDIS 模式但缺 RedisOps：WARN 并降级 OFF（不阻断启动）
            if (cfg.getCacheMode() == CacheMode.REDIS && redisOps == null) {
                System.err.println("[FlexSQL WARN] SqlBeanConfig.cacheMode=REDIS 但容器内未找到 RedisOps Bean，" +
                        "已降级为 OFF。请实现 RedisOps 接口并注册为 Bean。");
            }
            SqlBeanServices.applyFromSqlBeanConfig(cfg, redisOps);
        }

        return new CacheableSqlBeanServicePostProcessor();
    }
}