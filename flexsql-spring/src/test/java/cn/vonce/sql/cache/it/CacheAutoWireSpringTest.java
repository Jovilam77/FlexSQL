package cn.vonce.sql.cache.it;

import cn.vonce.sql.cache.CacheableSqlBeanService;
import cn.vonce.sql.service.SqlBeanService;
import cn.vonce.sql.spring.config.CacheableSqlBeanServicePostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring 集成测试：验证 CacheableSqlBeanServicePostProcessor（BeanPostProcessor）能自动把
 * queryCacheEnabled=true 的 SqlBeanService bean 包裹为缓存代理；而关闭时保持原样。
 * <p>使用 @Component（lite 模式）+ @Bean 注册，避免 @Configuration 触发的 CGLIB 在 JDK16+ 下的模块访问限制。</p>
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
        final AtomicInteger enabledCounter = new AtomicInteger();
        final AtomicInteger disabledCounter = new AtomicInteger();

        @Bean
        CacheableSqlBeanServicePostProcessor cacheBpp() {
            return new CacheableSqlBeanServicePostProcessor();
        }

        @Bean
        SqlBeanService enabledSvc() {
            return CacheStubUtil.makeStub(true, enabledCounter);
        }

        @Bean
        SqlBeanService disabledSvc() {
            return CacheStubUtil.makeStub(false, disabledCounter);
        }
    }

    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(TestConfig.class);
        ctx.refresh();

        @SuppressWarnings("unchecked")
        SqlBeanService<TestUser, Integer> enabled = (SqlBeanService<TestUser, Integer>) (SqlBeanService<?, ?>) ctx.getBean("enabledSvc");
        @SuppressWarnings("unchecked")
        SqlBeanService<TestUser, Integer> disabled = (SqlBeanService<TestUser, Integer>) (SqlBeanService<?, ?>) ctx.getBean("disabledSvc");

        check(CacheableSqlBeanService.isCacheProxy(enabled),
                "enabled bean 应被 BPP 包裹为缓存代理");
        check(!CacheableSqlBeanService.isCacheProxy(disabled),
                "disabled bean 不应被包裹（配置关闭）");

        TestConfig tc = ctx.getBean(TestConfig.class);

        enabled.selectById(1);
        enabled.selectById(1);
        check(tc.enabledCounter.get() == 1,
                "enabled：第二次相同 selectById 命中缓存，delegate 仅调用 1 次，实际=" + tc.enabledCounter.get());

        enabled.selectById(2);
        check(tc.enabledCounter.get() == 2,
                "enabled：不同 id 重新回源，累计调用=" + tc.enabledCounter.get());

        disabled.selectById(1);
        disabled.selectById(1);
        check(tc.disabledCounter.get() == 2,
                "disabled：每次都回源 delegate（无缓存），累计调用=" + tc.disabledCounter.get());

        ctx.close();
        System.out.println("Spring 集成测试：" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
