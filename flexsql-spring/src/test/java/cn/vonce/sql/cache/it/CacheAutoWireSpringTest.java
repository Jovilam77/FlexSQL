package cn.vonce.sql.cache.it;

import cn.vonce.sql.cache.CacheableSqlBeanService;
import cn.vonce.sql.cache.QueryCacheConfig;
import cn.vonce.sql.cache.SqlBeanServices;
import cn.vonce.sql.service.SqlBeanService;
import cn.vonce.sql.spring.config.CacheableSqlBeanServicePostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring 集成测试：验证 CacheableSqlBeanServicePostProcessor 能根据全局 {@link QueryCacheConfig}
 * 把 SqlBeanService bean 包裹为缓存代理（OFF 原样返回，LOCAL 包裹 + 命中缓存）。
 * <p>新机制下缓存开关已从 per-service {@code SqlBeanConfig.queryCacheEnabled} 迁移到全局
 * {@link QueryCacheConfig}（默认 OFF，二选一 LOCAL/REDIS），因此测试拆为两段（off / local），
 * 各自使用独立 {@link AnnotationConfigApplicationContext}。</p>
 * <p>直接以 main 方式运行（与项目内 QueryCacheTest 风格一致）。</p>
 */
public class CacheAutoWireSpringTest {

    static int passed = 0;
    static int failed = 0;

    static void check(boolean cond, String msg) {
        if (cond) {
            passed++;
            System.out.println("[PASS] " + msg);
        } else {
            failed++;
            System.out.println("[FAIL] " + msg);
        }
    }

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

    public static void main(String[] args) {
        // 段1：全局 OFF，BPP 应原样返回 bean
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
        AnnotationConfigApplicationContext ctxOff = new AnnotationConfigApplicationContext();
        ctxOff.register(TestConfig.class);
        ctxOff.refresh();

        @SuppressWarnings("unchecked")
        SqlBeanService<TestUser, Integer> offBean = (SqlBeanService<TestUser, Integer>) (SqlBeanService<?, ?>) ctxOff.getBean("testSvc");
        check(!CacheableSqlBeanService.isCacheProxy(offBean),
                "OFF：bean 不应被包裹（全局配置关闭）");
        AtomicInteger offCounter = ctxOff.getBean(TestConfig.class).counter;
        offBean.selectById(1);
        offBean.selectById(1);
        check(offCounter.get() == 2,
                "OFF：每次回源 delegate（无缓存），累计调用=" + offCounter.get());
        ctxOff.close();

        // 段2：全局 LOCAL，BPP 应包裹为缓存代理 + 命中缓存
        SqlBeanServices.setCacheConfig(QueryCacheConfig.local(1000L, 600L, 0L));
        AnnotationConfigApplicationContext ctxLocal = new AnnotationConfigApplicationContext();
        ctxLocal.register(TestConfig.class);
        ctxLocal.refresh();

        @SuppressWarnings("unchecked")
        SqlBeanService<TestUser, Integer> localBean = (SqlBeanService<TestUser, Integer>) (SqlBeanService<?, ?>) ctxLocal.getBean("testSvc");
        check(CacheableSqlBeanService.isCacheProxy(localBean),
                "LOCAL：bean 应被 BPP 包裹为缓存代理");
        AtomicInteger localCounter = ctxLocal.getBean(TestConfig.class).counter;
        localBean.selectById(1);
        localBean.selectById(1);
        check(localCounter.get() == 1,
                "LOCAL：第二次相同 selectById 命中缓存，delegate 仅调用 1 次，实际=" + localCounter.get());
        localBean.selectById(2);
        check(localCounter.get() == 2,
                "LOCAL：不同 id 重新回源，累计调用=" + localCounter.get());
        ctxLocal.close();

        // 复位全局配置
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());

        System.out.println("Spring 集成测试：" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}