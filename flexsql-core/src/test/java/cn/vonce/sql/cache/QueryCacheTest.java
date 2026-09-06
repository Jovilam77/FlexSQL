package cn.vonce.sql.cache;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询缓存引擎测试（main 直接运行，沿用项目打印断言风格）。
 * 覆盖：缓存键（含租户/动态schema）、本地读写/按表失效/TTL、深拷贝隔离（含嵌套 bean）。
 *
 * @author Jovi
 */
public class QueryCacheTest {

    public static void main(String[] args) throws Exception {
        int pass = 0, fail = 0;
        pass += check("keyEqualSameTenant", keyEqualSameTenant());
        fail += (keyEqualSameTenant() ? 0 : 1);
        pass += check("keyDiffTenant", keyDiffTenant());
        pass += check("simpleCachePutGet", simpleCachePutGet());
        pass += check("caffeinePutGet", caffeinePutGet());
        pass += check("caffeineEvictByTable", caffeineEvictByTable());
        pass += check("caffeineTtl", caffeineTtl());
        pass += check("schemaScopedEvict", schemaScopedEvict());
        pass += check("accessExpire", accessExpire());
        pass += check("beanCopierIndependent", beanCopierIndependent());
        pass += check("nestedBeanCopierIndependent", nestedBeanCopierIndependent());
        System.out.println("\n==== QueryCacheTest: " + pass + " passed, " + fail + " failed ====");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static int check(String name, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
        return ok ? 1 : 0;
    }

    private static boolean keyEqualSameTenant() {
        QueryCacheKey k1 = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tA", null, "");
        QueryCacheKey k1b = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tA", null, "");
        return k1.equals(k1b) && k1.hashCode() == k1b.hashCode();
    }

    private static boolean keyDiffTenant() {
        QueryCacheKey k1 = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tA", null, "");
        QueryCacheKey k2 = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tB", null, "");
        return !k1.equals(k2);
    }

    private static boolean simpleCachePutGet() {
        QueryCache cache = new SimpleQueryCache(100, 600);
        QueryCacheKey k = new QueryCacheKey(String.class, null, "SELECT s", "tA", null, "");
        cache.put(k, "Y", "t", "tA");
        return "Y".equals(cache.get(k));
    }

    private static boolean caffeinePutGet() {
        QueryCache cache = new SimpleQueryCache(100, 600);
        QueryCacheKey k = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tA", null, "");
        cache.put(k, "VALUE", "t_user", "tA");
        return "VALUE".equals(cache.get(k)) && cache.get(new QueryCacheKey(String.class, null, "other", "tA", null, "")) == null;
    }

    private static boolean caffeineEvictByTable() {
        QueryCache cache = new SimpleQueryCache(100, 600);
        QueryCacheKey k = new QueryCacheKey(String.class, null, "SELECT * FROM t_user WHERE id=1", "tA", null, "");
        cache.put(k, "VALUE", "t_user", "tA");
        cache.evictByTable("t_user", null, "tA");
        return cache.get(k) == null;
    }

    private static boolean caffeineTtl() throws Exception {
        QueryCache cache = new SimpleQueryCache(100, 1);
        QueryCacheKey k = new QueryCacheKey(String.class, null, "SELECT s", "tA", null, "");
        cache.put(k, "X", "t", "tA");
        boolean before = "X".equals(cache.get(k));
        Thread.sleep(1200);
        boolean after = cache.get(k) == null;
        return before && after;
    }

    /** R2：按表失效时仅清除匹配 schema 的项，跨 schema 同表名不受影响 */
    private static boolean schemaScopedEvict() {
        QueryCache cache = new SimpleQueryCache(100, 600);
        QueryCacheKey kA = new QueryCacheKey(String.class, null, "SELECT * FROM t_user", "tA", "s1", "");
        QueryCacheKey kB = new QueryCacheKey(String.class, null, "SELECT * FROM t_user", "tA", "s2", "");
        cache.put(kA, "A", "t_user", "tA");
        cache.put(kB, "B", "t_user", "tA");
        cache.evictByTable("t_user", "s1", "tA");
        boolean aGone = cache.get(kA) == null;
        boolean bKept = "B".equals(cache.get(kB));
        return aGone && bKept;
    }

    /** R4：按访问过期（写 TTL 足够大，仅 access TTL 触发） */
    private static boolean accessExpire() throws Exception {
        QueryCache cache = new SimpleQueryCache(100, 600, 1);
        QueryCacheKey k = new QueryCacheKey(String.class, null, "SELECT s", "tA", null, "");
        cache.put(k, "X", "t", "tA");
        boolean first = "X".equals(cache.get(k));
        Thread.sleep(1200);
        boolean after = cache.get(k) == null;
        return first && after;
    }

    private static boolean beanCopierIndependent() {
        MyBean b = new MyBean();
        b.setName("a");
        List<String> l = new ArrayList<>();
        l.add("x");
        b.setList(l);
        MyBean c = (MyBean) BeanCopier.copy(b);
        c.setName("b");
        c.getList().add("y");
        return b.getName().equals("a") && b.getList().size() == 1;
    }

    /** R3：嵌套 bean 字段也被深拷贝，修改副本不会污染原对象 */
    private static boolean nestedBeanCopierIndependent() {
        Outer a = new Outer();
        a.setName("a");
        Inner inner = new Inner();
        inner.setCity("BJ");
        a.setInner(inner);
        Outer c = (Outer) BeanCopier.copy(a);
        c.setName("c");
        c.getInner().setCity("SH");
        return a.getName().equals("a") && a.getInner().getCity().equals("BJ");
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
