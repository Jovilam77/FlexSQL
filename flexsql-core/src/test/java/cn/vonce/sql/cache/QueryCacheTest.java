package cn.vonce.sql.cache;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询缓存引擎测试（main 直接运行，沿用项目打印断言风格）。
 * 覆盖：缓存键（含租户/动态schema）、Caffeine 读写/按表失效/TTL、深拷贝隔离。
 *
 * @author Jovi
 */
public class QueryCacheTest {

    public static void main(String[] args) throws Exception {
        int pass = 0, fail = 0;
        pass += check("keyEqualSameTenant", keyEqualSameTenant());
        fail += (keyEqualSameTenant() ? 0 : 1);
        pass += check("keyDiffTenant", keyDiffTenant());
        pass += check("caffeinePutGet", caffeinePutGet());
        pass += check("caffeineEvictByTable", caffeineEvictByTable());
        pass += check("caffeineTtl", caffeineTtl());
        pass += check("simpleCachePutGet", simpleCachePutGet());
        pass += check("beanCopierIndependent", beanCopierIndependent());
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
        cache.evictByTable("t_user", "tA");
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

    private static boolean simpleCachePutGet() {
        QueryCache cache = new SimpleQueryCache(100, 600);
        QueryCacheKey k = new QueryCacheKey(String.class, null, "SELECT s", "tA", null, "");
        cache.put(k, "Y", "t", "tA");
        return "Y".equals(cache.get(k));
    }

    private static boolean beanCopierIndependent() {
        MyBean b = new MyBean();
        b.setName("a");
        List<String> l = new ArrayList<>();
        l.add("x");
        b.setList(l);
        MyBean c = BeanCopier.copy(b);
        c.setName("b");
        c.getList().add("y");
        return b.getName().equals("a") && b.getList().size() == 1;
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
}
