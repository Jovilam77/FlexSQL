package cn.vonce.sql.cache.it;

import cn.vonce.sql.cache.CacheableSqlBeanService;
import cn.vonce.sql.cache.QueryCacheConfig;
import cn.vonce.sql.cache.SqlBeanServices;
import cn.vonce.sql.service.SqlBeanService;
import cn.vonce.sql.spring.config.CacheableSqlBeanServicePostProcessor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring 集成测试（JUnit）：验证 CacheableSqlBeanServicePostProcessor 能根据全局 {@link QueryCacheConfig}
 * 把 SqlBeanService bean 包裹为缓存代理（OFF 原样返回，LOCAL 包裹 + 命中缓存）。
 * <p>新机制下缓存开关已从 per-service {@code SqlBeanConfig.queryCacheEnabled} 迁移到全局
 * {@link QueryCacheConfig}（默认 OFF，二选一 LOCAL/REDIS），因此测试拆为两段（off / local），
 * 各自使用独立 {@link AnnotationConfigApplicationContext}。</p>
 */
public class CacheAutoWireSpringTest {

    @Component
    static class TestConfig {
        final AtomicInteger counter = new AtomicInteger();

        @Bean
        CacheableSqlBeanServicePostProcessor cacheBpp() {
            return new CacheableSqlBeanServicePostProcessor();
        }

        @Bean
        SqlBeanService testSvc() {
            return CacheStubUtil.makeStub(counter);
        }
    }

    /** 复位全局缓存配置，避免污染同 JVM 的其它用例。 */
    @After
    public void resetGlobalCacheConfig() {
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
    }

    @Test
    public void offModeLeavesBeanUnwrapped() {
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(TestConfig.class);
        ctx.refresh();
        try {
            @SuppressWarnings("unchecked")
            SqlBeanService<TestUser, Integer> bean = (SqlBeanService<TestUser, Integer>) (SqlBeanService<?, ?>) ctx.getBean("testSvc");
            Assert.assertFalse("OFF：bean 不应被包裹（全局配置关闭）",
                    CacheableSqlBeanService.isCacheProxy(bean));

            AtomicInteger counter = ctx.getBean(TestConfig.class).counter;
            bean.selectById(1);
            bean.selectById(1);
            Assert.assertEquals("OFF：每次回源 delegate（无缓存），累计调用应为 2", 2, counter.get());
        } finally {
            ctx.close();
        }
    }

    @Test
    public void localModeWrapsBeanAndHitsCache() {
        SqlBeanServices.setCacheConfig(QueryCacheConfig.local(1000L, 600L, 0L));
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(TestConfig.class);
        ctx.refresh();
        try {
            @SuppressWarnings("unchecked")
            SqlBeanService<TestUser, Integer> bean = (SqlBeanService<TestUser, Integer>) (SqlBeanService<?, ?>) ctx.getBean("testSvc");
            Assert.assertTrue("LOCAL：bean 应被 BPP 包裹为缓存代理",
                    CacheableSqlBeanService.isCacheProxy(bean));

            AtomicInteger counter = ctx.getBean(TestConfig.class).counter;
            bean.selectById(1);
            bean.selectById(1);
            Assert.assertEquals("LOCAL：第二次相同 selectById 命中缓存，delegate 仅调用 1 次", 1, counter.get());

            bean.selectById(2);
            Assert.assertEquals("LOCAL：不同 id 重新回源，累计调用应为 2", 2, counter.get());
        } finally {
            ctx.close();
        }
    }
}
