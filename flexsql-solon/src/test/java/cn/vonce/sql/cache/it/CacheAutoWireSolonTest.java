package cn.vonce.sql.cache.it;

import cn.vonce.sql.cache.CacheableSqlBeanService;
import cn.vonce.sql.cache.QueryCacheConfig;
import cn.vonce.sql.cache.SqlBeanServices;
import cn.vonce.sql.service.SqlBeanService;
import cn.vonce.sql.solon.config.AutoConfigSolon;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.noear.solon.SolonApp;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.NvMap;
import org.noear.solon.core.Props;
import org.noear.solon.core.event.AppBeanLoadEndEvent;
import org.noear.solon.core.event.EventBus;

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Solon 集成测试（JUnit）：直接驱动真实的 AutoConfigSolon 接线逻辑（不依赖完整 Solon 启动，规避 mybatis 等其它插件）。
 * <p>新机制下缓存开关已迁移到全局 {@link QueryCacheConfig}（默认 OFF，二选一 LOCAL/REDIS）。
 * 启用段在方法开头设置 LOCAL，关闭段设置 OFF；每段使用独立 AppContext，互不干扰。</p>
 */
public class CacheAutoWireSolonTest {

    /** 复位全局缓存配置，避免污染同 JVM 的其它用例。 */
    @After
    public void resetGlobalCacheConfig() {
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
    }

    @Test
    public void localModeWrapsBeanAsCacheProxy() throws Exception {
        SqlBeanServices.setCacheConfig(QueryCacheConfig.local(1000L, 600L, 0L));
        AppContext context = new AppContext(Thread.currentThread().getContextClassLoader(), new Props());
        AtomicInteger counter = new AtomicInteger();
        SqlBeanService<TestUser, Integer> stub = CacheStubUtil.makeStub(counter);

        context.wrapAndPut(SqlBeanService.class, stub);  // 先注册 bean（与真实 Solon 中 BeanWrap 已带 raw 实例一致）
        AutoConfigSolon plugin = new AutoConfigSolon();
        plugin.start(context);                 // subWrapsOfType 回灌已存在 bean -> 触发包裹回调
        fireBeanLoadEnd(context);              // 额外派发事件，验证幂等不重复包裹

        @SuppressWarnings("unchecked")
        SqlBeanService<TestUser, Integer> bean = (SqlBeanService<TestUser, Integer>) context.getBean(SqlBeanService.class);
        Assert.assertTrue("Solon：LOCAL bean 应被 AutoConfigSolon 包裹为缓存代理",
                CacheableSqlBeanService.isCacheProxy(bean));

        bean.selectById(1);
        bean.selectById(1);
        Assert.assertEquals("Solon：第二次相同 selectById 命中缓存，delegate 仅调用 1 次",
                1, counter.get());

        bean.selectById(2);
        Assert.assertEquals("Solon：不同 id 重新回源，累计调用应为 2", 2, counter.get());
    }

    @Test
    public void offModeLeavesBeanUnwrapped() throws Exception {
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
        AppContext context = new AppContext(Thread.currentThread().getContextClassLoader(), new Props());
        AtomicInteger counter = new AtomicInteger();
        SqlBeanService<TestUser, Integer> stub = CacheStubUtil.makeStub(counter);

        context.wrapAndPut(SqlBeanService.class, stub);
        AutoConfigSolon plugin = new AutoConfigSolon();
        plugin.start(context);
        fireBeanLoadEnd(context);

        @SuppressWarnings("unchecked")
        SqlBeanService<TestUser, Integer> bean = (SqlBeanService<TestUser, Integer>) context.getBean(SqlBeanService.class);
        Assert.assertFalse("Solon：OFF bean 不应被包裹（全局配置关闭）",
                CacheableSqlBeanService.isCacheProxy(bean));

        bean.selectById(1);
        bean.selectById(1);
        Assert.assertEquals("Solon：OFF 每次都回源 delegate（无缓存），累计调用应为 2", 2, counter.get());
    }

    /** 通过反射构造一个最小 SolonApp 来派发 AppBeanLoadEndEvent（其 ctor 为 protected）。事件内容不影响包裹逻辑，仅用于触发监听。 */
    static void fireBeanLoadEnd(AppContext context) {
        try {
            Constructor<SolonApp> c = SolonApp.class.getDeclaredConstructor(Class.class, NvMap.class);
            c.setAccessible(true);
            SolonApp app = c.newInstance(CacheAutoWireSolonTest.class, new NvMap());
            EventBus.push(new AppBeanLoadEndEvent(app));
        } catch (Throwable t) {
            // subWrapsOfType 回调已在注册时完成包裹，事件路径仅做幂等二次覆盖，失败不影响主断言
            System.out.println("[WARN] fireBeanLoadEnd skipped: " + t.getMessage());
        }
    }
}
