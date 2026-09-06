package cn.vonce.sql.cache.it;

import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.service.SqlBeanService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 构造一个“假”的 SqlBeanService，用于在集成测试中验证缓存装饰器的自动接线：
 * <ul>
 *   <li>只实现 getBeanClass / getSqlBeanMeta / selectById 三个方法（其余抛 UnsupportedOperationException）；</li>
 *   <li>getSqlBeanMeta 携带一个 SqlBeanConfig（queryCacheEnabled 由入参控制）；</li>
 *   <li>selectById 每次真实调用都让计数器 +1，用于断言“第二次相同调用是否命中缓存（不再回源）”。</li>
 * </ul>
 * 返回的是 JDK 动态代理，因此可被 CacheableSqlBeanService 再次包裹。
 */
public class CacheStubUtil {

    public static SqlBeanService<TestUser, Integer> makeStub(boolean enabled, AtomicInteger callCounter) {
        SqlBeanMeta meta = new SqlBeanMeta();
        meta.setDbType(DbType.MySQL);
        SqlBeanConfig cfg = new SqlBeanConfig();
        cfg.setQueryCacheEnabled(enabled);
        cfg.setQueryCacheMaxSize(1000L);
        cfg.setQueryCacheExpireAfterWriteSeconds(600L);
        meta.setSqlBeanConfig(cfg);

        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getBeanClass":
                    return TestUser.class;
                case "getSqlBeanMeta":
                    return meta;
                case "selectById":
                    callCounter.incrementAndGet();
                    Integer id = (Integer) (args != null && args.length == 2 ? args[1] : (args == null ? null : args[0]));
                    return new TestUser(id, "user-" + id);
                default:
                    throw new UnsupportedOperationException("stub not implemented: " + method.getName());
            }
        };
        return (SqlBeanService<TestUser, Integer>) Proxy.newProxyInstance(
                TestUser.class.getClassLoader(),
                new Class[]{SqlBeanService.class},
                handler);
    }
}
