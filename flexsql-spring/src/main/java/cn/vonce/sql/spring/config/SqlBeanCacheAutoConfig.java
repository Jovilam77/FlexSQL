package cn.vonce.sql.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 查询缓存自动接线配置。
 * <p>注册 {@link CacheableSqlBeanServicePostProcessor}，使其在 SqlBeanService bean 初始化后按
 * {@code SqlBeanConfig.queryCacheEnabled} 自动包裹为带查询缓存的代理。</p>
 * <p>通过 {@code UseSpringJdbc} / {@code UseMybatis} 的 @Import 生效；缓存默认关闭，对既有行为零侵入。</p>
 *
 * @author Jovi
 * @version 1.0
 */
@Configuration
public class SqlBeanCacheAutoConfig {

    @Bean
    public CacheableSqlBeanServicePostProcessor cacheableSqlBeanServicePostProcessor() {
        return new CacheableSqlBeanServicePostProcessor();
    }
}
