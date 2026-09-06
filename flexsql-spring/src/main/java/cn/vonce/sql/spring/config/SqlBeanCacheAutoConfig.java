package cn.vonce.sql.spring.config;

import cn.vonce.sql.cache.CacheableSqlBeanService;
import cn.vonce.sql.java.datasource.DataSourceContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 查询缓存自动接线配置。
 * <p>注册 {@link CacheableSqlBeanServicePostProcessor}，使其在 SqlBeanService bean 初始化后按
 * {@code SqlBeanConfig.queryCacheEnabled} 自动包裹为带查询缓存的代理。</p>
 * <p>同时通过可插拔 SPI 向 core 注册「当前数据源名解析器」与「事务同步钩子」，使缓存键能区分多数据源、
 * 且写操作在事务提交后才失效（避免脏数据窗口）。这两个能力由具体框架提供，core 本身不依赖 Spring/Solon。</p>
 *
 * @author Jovi
 * @version 1.1
 */
@Configuration
public class SqlBeanCacheAutoConfig {

    @Bean
    public CacheableSqlBeanServicePostProcessor cacheableSqlBeanServicePostProcessor() {
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
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
            }
        });
        return new CacheableSqlBeanServicePostProcessor();
    }
}
