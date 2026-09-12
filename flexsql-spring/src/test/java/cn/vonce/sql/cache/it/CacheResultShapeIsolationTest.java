package cn.vonce.sql.cache.it;

import cn.vonce.sql.bean.Select;
import cn.vonce.sql.cache.CacheableSqlBeanService;
import cn.vonce.sql.cache.SimpleQueryCache;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.service.SqlBeanService;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓存装饰器的「返回形态串键」端到端回归测试。
 *
 * <p><b>背景（2026-09-12 修复的 P0）</b>：{@code select} / {@code selectOne} / {@code selectMap} /
 * {@code selectMapList} 共用同一段 SQL，修复前缓存键不含操作名 → 四者同键。
 * 本测试用真实装饰器 {@link CacheableSqlBeanService#wrap} 复现该场景：
 * 先 {@code select} 把 {@code List} 写进缓存，再调 {@code selectOne}，
 * 修复前会拿到那个 List 并在调用点抛 {@code ClassCastException}。</p>
 *
 * <p>与 {@link CacheAutoWireSpringTest} 同风格：用 JDK 动态代理伪造 delegate，
 * 只实现测试需要的若干方法，其余抛 {@code UnsupportedOperationException}。</p>
 *
 * @author Jovi
 */
public class CacheResultShapeIsolationTest {

    /** 每次真实回源都会 +1，用于断言「命中的是缓存还是回源」。 */
    private static final AtomicInteger LOAD_CALLS = new AtomicInteger();

    @SuppressWarnings("unchecked")
    private static SqlBeanService<TestUser, Integer> makeStub() {
        SqlBeanMeta meta = new SqlBeanMeta();
        meta.setDbType(DbType.MySQL);
        meta.setSqlBeanConfig(new SqlBeanConfig());

        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getBeanClass":
                    return TestUser.class;
                case "getSqlBeanMeta":
                    return meta;
                case "select":
                    // 模拟 List<T> 形态
                    LOAD_CALLS.incrementAndGet();
                    List<TestUser> list = new ArrayList<>();
                    list.add(new TestUser(1, "alice"));
                    return list;
                case "selectOne":
                    // 模拟 T 形态
                    LOAD_CALLS.incrementAndGet();
                    return new TestUser(1, "alice");
                case "selectMap":
                    LOAD_CALLS.incrementAndGet();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", 1);
                    return row;
                case "selectMapList":
                    LOAD_CALLS.incrementAndGet();
                    List<Map<String, Object>> rows = new ArrayList<>();
                    rows.add(Collections.<String, Object>singletonMap("id", 1));
                    return rows;
                case "toString":
                    return "CacheResultShapeIsolationTest-stub";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    throw new UnsupportedOperationException("stub not implemented: " + method.getName());
            }
        };
        return (SqlBeanService<TestUser, Integer>) Proxy.newProxyInstance(
                TestUser.class.getClassLoader(),
                new Class[]{SqlBeanService.class},
                handler);
    }

    private static SqlBeanService<TestUser, Integer> cachedService() {
        LOAD_CALLS.set(0);
        SimpleQueryCache cache = new SimpleQueryCache(1024L, 600L);
        return CacheableSqlBeanService.wrap(makeStub(), cache);
    }

    /**
     * 核心回归：同一段 SQL 下，select（List）与 selectOne（单对象）必须各自独立缓存。
     * 修复前该用例会在 {@code svc.selectOne(...)} 处抛 ClassCastException。
     */
    @Test
    public void selectAndSelectOneDoNotShareCacheEntry() {
        SqlBeanService<TestUser, Integer> svc = cachedService();

        // 1) select 回源并写入缓存（List 形态）
        List<TestUser> list = svc.select(new Select());
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("首次 select 应回源", 1, LOAD_CALLS.get());

        // 2) 相同 SQL 的 select 命中缓存，不再回源
        svc.select(new Select());
        Assert.assertEquals("第二次 select 应命中缓存", 1, LOAD_CALLS.get());

        // 3) 相同 SQL 的 selectOne 必须独立回源，返回单个对象（而非上面那个 List）
        TestUser one = svc.selectOne(new Select());
        Assert.assertNotNull(one);
        Assert.assertEquals("alice", one.getName());
        Assert.assertEquals("selectOne 不得命中 select 的缓存项", 2, LOAD_CALLS.get());
    }

    /** selectMap（Map 形态）与 selectOne（T 形态）同样不能串键。 */
    @Test
    public void selectMapAndSelectOneDoNotShareCacheEntry() {
        SqlBeanService<TestUser, Integer> svc = cachedService();

        TestUser one = svc.selectOne(new Select());
        Assert.assertNotNull(one);
        Assert.assertEquals(1, LOAD_CALLS.get());

        Map<String, Object> row = svc.selectMap(new Select());
        Assert.assertEquals(1, row.get("id"));
        Assert.assertEquals("selectMap 不得命中 selectOne 的缓存项", 2, LOAD_CALLS.get());
    }

    /** 同一操作重复调用仍应命中缓存（证明上面的「不串键」不是因为缓存整体失效）。 */
    @Test
    public void sameOperationStillHitsCache() {
        SqlBeanService<TestUser, Integer> svc = cachedService();

        svc.selectOne(new Select());
        svc.selectOne(new Select());
        Assert.assertEquals("同操作同 SQL 应命中缓存", 1, LOAD_CALLS.get());
    }
}
