package cn.vonce.sql.cache;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 查询缓存引擎测试（JUnit）。
 * 覆盖：缓存键（含租户/动态schema/数据源）、本地读写/按表失效/TTL、深拷贝隔离（含嵌套 bean）。
 *
 * @author Jovi
 */
public class QueryCacheTest {

    @Test
    public void keyEqualSameTenant() {
        QueryCacheKey k1 = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tA", null, "");
        QueryCacheKey k1b = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tA", null, "");
        Assert.assertEquals(k1, k1b);
        Assert.assertEquals(k1.hashCode(), k1b.hashCode());
    }

    @Test
    public void keyDiffTenant() {
        QueryCacheKey k1 = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tA", null, "");
        QueryCacheKey k2 = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tB", null, "");
        Assert.assertNotEquals("租户不同，缓存键必须不同", k1, k2);
    }

    @Test
    public void simpleCachePutGet() {
        QueryCache cache = new SimpleQueryCache(100, 600);
        QueryCacheKey k = new QueryCacheKey(String.class, null, "SELECT s", "tA", null, "");
        cache.put(k, "Y", "t", "tA");
        Assert.assertEquals("Y", cache.get(k));
    }

    @Test
    public void simpleCacheMissOnDifferentSql() {
        QueryCache cache = new SimpleQueryCache(100, 600);
        QueryCacheKey k = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tA", null, "");
        cache.put(k, "VALUE", "t_user", "tA");
        Assert.assertEquals("VALUE", cache.get(k));
        Assert.assertNull("不同 SQL 不应命中",
                cache.get(new QueryCacheKey(String.class, null, "other", "tA", null, "")));
    }

    @Test
    public void evictByTableRemovesEntry() {
        QueryCache cache = new SimpleQueryCache(100, 600);
        QueryCacheKey k = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tA", null, "");
        cache.put(k, "VALUE", "t_user", "tA");
        cache.evictByTable("t_user", null, "tA", null);
        Assert.assertNull(cache.get(k));
    }

    @Test
    public void writeTtlExpiresEntry() throws Exception {
        QueryCache cache = new SimpleQueryCache(100, 1);
        QueryCacheKey k = new QueryCacheKey(String.class, null, "SELECT s", "tA", null, "");
        cache.put(k, "X", "t", "tA");
        Assert.assertEquals("X", cache.get(k));
        Thread.sleep(1200);
        Assert.assertNull("写 TTL 到期后应失效", cache.get(k));
    }

    /** R2：按表失效时仅清除匹配 schema 的项，跨 schema 同表名不受影响 */
    @Test
    public void schemaScopedEvict() {
        QueryCache cache = new SimpleQueryCache(100, 600);
        QueryCacheKey kA = new QueryCacheKey(String.class, null, "SELECT * FROM t_user", "tA", "s1", "");
        QueryCacheKey kB = new QueryCacheKey(String.class, null, "SELECT * FROM t_user", "tA", "s2", "");
        cache.put(kA, "A", "t_user", "tA");
        cache.put(kB, "B", "t_user", "tA");
        cache.evictByTable("t_user", "s1", "tA", null);
        Assert.assertNull("s1 的项应被失效", cache.get(kA));
        Assert.assertEquals("s2 的项应保留", "B", cache.get(kB));
    }

    /** R4：按访问过期（写 TTL 足够大，仅 access TTL 触发） */
    @Test
    public void accessExpire() throws Exception {
        QueryCache cache = new SimpleQueryCache(100, 600, 1);
        QueryCacheKey k = new QueryCacheKey(String.class, null, "SELECT s", "tA", null, "");
        cache.put(k, "X", "t", "tA");
        Assert.assertEquals("X", cache.get(k));
        Thread.sleep(1200);
        Assert.assertNull("访问 TTL 到期后应失效", cache.get(k));
    }

    @Test
    public void keyDiffDataSource() {
        QueryCacheKey k1 = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tA", null, "", "ds1");
        QueryCacheKey k2 = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tA", null, "", "ds2");
        Assert.assertNotEquals("数据源不同，缓存键必须不同", k1, k2);
    }

    /** 数据源维度：按表失效时仅清除匹配数据源的项，跨数据源同表名不受影响（多数据源 / @DbSwitch 场景） */
    @Test
    public void dataSourceScopedEvict() {
        QueryCache cache = new SimpleQueryCache(100, 600);
        QueryCacheKey kA = new QueryCacheKey(String.class, null, "SELECT * FROM t_user", "tA", null, "", "ds1");
        QueryCacheKey kB = new QueryCacheKey(String.class, null, "SELECT * FROM t_user", "tA", null, "", "ds2");
        cache.put(kA, "A", "t_user", "tA");
        cache.put(kB, "B", "t_user", "tA");
        cache.evictByTable("t_user", null, "tA", "ds1");
        Assert.assertNull("ds1 的项应被失效", cache.get(kA));
        Assert.assertEquals("ds2 的项应保留", "B", cache.get(kB));
    }

    @Test
    public void beanCopierIndependent() {
        MyBean b = new MyBean();
        b.setName("a");
        List<String> l = new ArrayList<>();
        l.add("x");
        b.setList(l);
        MyBean c = (MyBean) BeanCopier.copy(b);
        c.setName("b");
        c.getList().add("y");
        Assert.assertEquals("a", b.getName());
        Assert.assertEquals("修改副本不应污染原对象的集合", 1, b.getList().size());
    }

    /** R3：嵌套 bean 字段也被深拷贝，修改副本不会污染原对象 */
    @Test
    public void nestedBeanCopierIndependent() {
        Outer a = new Outer();
        a.setName("a");
        Inner inner = new Inner();
        inner.setCity("BJ");
        a.setInner(inner);
        Outer c = (Outer) BeanCopier.copy(a);
        c.setName("c");
        c.getInner().setCity("SH");
        Assert.assertEquals("a", a.getName());
        Assert.assertEquals("BJ", a.getInner().getCity());
    }

    /**
     * R2（空结果缓存防穿透）：CacheableSqlBeanService 将 null 结果以哨兵对象缓存（非 null），
     * 回读时再还原为 null，从而与「未命中」区分，避免相同查询反复回源。
     * 这里直接反射调用生产代码的 toCacheValue / unwrap 验证逻辑正确性。
     */
    @Test
    public void nullResultCachedAsSentinel() throws Exception {
        Method toCacheValue = CacheableSqlBeanService.class.getDeclaredMethod("toCacheValue", Object.class);
        toCacheValue.setAccessible(true);
        Method unwrap = CacheableSqlBeanService.class.getDeclaredMethod("unwrap", Object.class);
        unwrap.setAccessible(true);
        // null -> 哨兵（非 null），且回读为 null
        Object sentinel = toCacheValue.invoke(null, (Object) null);
        Assert.assertNotNull("null 结果应存为哨兵对象（非 null）", sentinel);
        Assert.assertNull("哨兵回读应还原为 null", unwrap.invoke(null, sentinel));
        // 非空值正常回读
        Object stored = toCacheValue.invoke(null, "HELLO");
        Assert.assertEquals("HELLO", unwrap.invoke(null, stored));
    }

    public static class MyBean {
        private String name;
        private List<String> list;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getList() {
            return list;
        }

        public void setList(List<String> list) {
            this.list = list;
        }
    }

    public static class Outer {
        private String name;
        private Inner inner;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Inner getInner() {
            return inner;
        }

        public void setInner(Inner inner) {
            this.inner = inner;
        }
    }

    public static class Inner {
        private String city;

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }
    }
}
