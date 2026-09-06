package cn.vonce.sql.cache.caffeine;

import cn.vonce.sql.config.CacheMode;
import cn.vonce.sql.cache.QueryCache;
import cn.vonce.sql.cache.QueryCacheConfig;
import cn.vonce.sql.cache.QueryCacheKey;

/**
 * CaffeineQueryCache 单元测试（main 风格，无需 DB，与项目既有测试约定一致）。
 */
public class CaffeineQueryCacheTest {

    static int pass = 0;
    static int fail = 0;

    static int check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("  PASS " + name);
        } else {
            fail++;
            System.out.println("  FAIL " + name);
        }
        return ok ? 1 : 0;
    }

    static QueryCacheKey key(String sql, String tenant, String schema, String dataSource) {
        return new QueryCacheKey(String.class, null, sql, tenant, schema, "", dataSource);
    }

    static boolean putGet() {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 600);
        QueryCacheKey k = key("SELECT * FROM t_user WHERE id=1", "tA", null, "ds1");
        cache.put(k, "A", "t_user", "tA");
        return "A".equals(cache.get(k))
                && cache.get(key("SELECT * FROM t_user WHERE id=2", "tA", null, "ds1")) == null;
    }

    static boolean evictByTable() {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 600);
        QueryCacheKey kUser = key("SELECT * FROM t_user", "tA", null, "ds1");
        QueryCacheKey kOrder = key("SELECT * FROM t_order", "tA", null, "ds1");
        cache.put(kUser, "U", "t_user", "tA");
        cache.put(kOrder, "O", "t_order", "tA");
        cache.evictByTable("t_user", null, "tA", "ds1");
        return cache.get(kUser) == null && "O".equals(cache.get(kOrder));
    }

    static boolean dataSourceIsolated() {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 600);
        QueryCacheKey k1 = key("SELECT * FROM t_user", "tA", null, "ds1");
        QueryCacheKey k2 = key("SELECT * FROM t_user", "tA", null, "ds2");
        cache.put(k1, "A", "t_user", "tA");
        cache.put(k2, "B", "t_user", "tA");
        cache.evictByTable("t_user", null, "tA", "ds1");
        return cache.get(k1) == null && "B".equals(cache.get(k2));
    }

    static boolean tenantIsolated() {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 600);
        QueryCacheKey k1 = key("SELECT * FROM t_user", "tA", null, "ds1");
        QueryCacheKey k2 = key("SELECT * FROM t_user", "tB", null, "ds1");
        cache.put(k1, "A", "t_user", "tA");
        cache.put(k2, "B", "t_user", "tB");
        cache.evictByTable("t_user", null, "tA", "ds1");
        return cache.get(k1) == null && "B".equals(cache.get(k2));
    }

    static boolean schemaIsolated() {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 600);
        QueryCacheKey k1 = key("SELECT * FROM t_user", "tA", "sch1", "ds1");
        QueryCacheKey k2 = key("SELECT * FROM t_user", "tA", "sch2", "ds1");
        cache.put(k1, "A", "t_user", "tA");
        cache.put(k2, "B", "t_user", "tA");
        cache.evictByTable("t_user", "sch1", "tA", "ds1");
        return cache.get(k1) == null && "B".equals(cache.get(k2));
    }

    static boolean maxSizeEviction() throws Exception {
        CaffeineQueryCache cache = new CaffeineQueryCache(2, 0);
        for (int i = 0; i < 50; i++) {
            cache.put(key("SELECT * FROM t_user WHERE id=" + i, "tA", null, "ds1"), "V" + i, "t_user", "tA");
        }
        // Caffeine 的容量淘汰在维护阶段异步收敛：单次 cleanUp() 后立刻读 estimatedSize()
        // 可能仍读到未落定的值（实测会偶发 >2），故做有上限的等待（最多约 100ms）再断言。
        long size = cache.estimatedSize();
        for (int i = 0; i < 10 && size > 2; i++) {
            cache.cleanUp();
            Thread.sleep(10);
            size = cache.estimatedSize();
        }
        return size <= 2;
    }

    static boolean ttlExpiry() throws Exception {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 1);
        QueryCacheKey k = key("SELECT * FROM t_user WHERE id=1", "tA", null, "ds1");
        cache.put(k, "A", "t_user", "tA");
        boolean hitBefore = "A".equals(cache.get(k));
        Thread.sleep(1500);
        return hitBefore && cache.get(k) == null;
    }

    static boolean noTtlKeepsValue() throws Exception {
        CaffeineQueryCache cache = new CaffeineQueryCache(100);
        QueryCacheKey k = key("SELECT * FROM t_user WHERE id=1", "tA", null, "ds1");
        cache.put(k, "A", "t_user", "tA");
        Thread.sleep(200);
        return "A".equals(cache.get(k));
    }

    static boolean clearAll() {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 600);
        QueryCacheKey k = key("SELECT * FROM t_user", "tA", null, "ds1");
        cache.put(k, "A", "t_user", "tA");
        cache.clear();
        return cache.get(k) == null && cache.estimatedSize() == 0;
    }

    static boolean notDistributed() {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 600);
        return !cache.isDistributed();
    }

    static boolean customMode() {
        QueryCacheConfig cfg = QueryCacheConfig.custom(new CaffeineQueryCache(100, 600));
        return cfg.getMode() == CacheMode.CUSTOM && cfg.getCache() instanceof CaffeineQueryCache;
    }

    static boolean installAsLocalImplementation() {
        CaffeineQueryCache.install();
        try {
            QueryCacheConfig cfg = QueryCacheConfig.local(100, 600, 0);
            boolean ok = cfg.getMode() == CacheMode.LOCAL && cfg.getCache() instanceof CaffeineQueryCache;
            return ok;
        } finally {
            CaffeineQueryCache.uninstall();
        }
    }

    static boolean uninstallRestoresSimple() {
        CaffeineQueryCache.install();
        CaffeineQueryCache.uninstall();
        QueryCache cache = QueryCacheConfig.local(100, 600, 0).getCache();
        return !(cache instanceof CaffeineQueryCache);
    }

    public static void main(String[] args) throws Exception {
        check("putGet", putGet());
        check("evictByTable", evictByTable());
        check("dataSourceIsolated", dataSourceIsolated());
        check("tenantIsolated", tenantIsolated());
        check("schemaIsolated", schemaIsolated());
        check("maxSizeEviction", maxSizeEviction());
        check("ttlExpiry", ttlExpiry());
        check("noTtlKeepsValue", noTtlKeepsValue());
        check("clearAll", clearAll());
        check("notDistributed", notDistributed());
        check("customMode", customMode());
        check("installAsLocalImplementation", installAsLocalImplementation());
        check("uninstallRestoresSimple", uninstallRestoresSimple());

        System.out.println("\nCaffeineQueryCacheTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
