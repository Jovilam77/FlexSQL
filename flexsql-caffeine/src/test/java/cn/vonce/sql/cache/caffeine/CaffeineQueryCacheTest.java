package cn.vonce.sql.cache.caffeine;

import cn.vonce.sql.cache.QueryCache;
import cn.vonce.sql.cache.QueryCacheConfig;
import cn.vonce.sql.cache.QueryCacheKey;
import cn.vonce.sql.config.CacheMode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * CaffeineQueryCache 单元测试（JUnit，无需 DB）。
 */
public class CaffeineQueryCacheTest {

    /** 注销可插拔本地缓存工厂，避免污染同 JVM 的其它用例。 */
    @After
    public void uninstallFactory() {
        CaffeineQueryCache.uninstall();
    }

    static QueryCacheKey key(String sql, String tenant, String schema, String dataSource) {
        return new QueryCacheKey(String.class, null, sql, tenant, schema, "", dataSource);
    }

    @Test
    public void putGet() {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 600);
        QueryCacheKey k = key("SELECT * FROM t_user WHERE id=1", "tA", null, "ds1");
        cache.put(k, "A", "t_user", "tA");
        Assert.assertEquals("A", cache.get(k));
        Assert.assertNull("不同 SQL 不应命中",
                cache.get(key("SELECT * FROM t_user WHERE id=2", "tA", null, "ds1")));
    }

    @Test
    public void evictByTable() {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 600);
        QueryCacheKey kUser = key("SELECT * FROM t_user", "tA", null, "ds1");
        QueryCacheKey kOrder = key("SELECT * FROM t_order", "tA", null, "ds1");
        cache.put(kUser, "U", "t_user", "tA");
        cache.put(kOrder, "O", "t_order", "tA");
        cache.evictByTable("t_user", null, "tA", "ds1");
        Assert.assertNull(cache.get(kUser));
        Assert.assertEquals("其它表不应被连带失效", "O", cache.get(kOrder));
    }

    @Test
    public void dataSourceIsolated() {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 600);
        QueryCacheKey k1 = key("SELECT * FROM t_user", "tA", null, "ds1");
        QueryCacheKey k2 = key("SELECT * FROM t_user", "tA", null, "ds2");
        cache.put(k1, "A", "t_user", "tA");
        cache.put(k2, "B", "t_user", "tA");
        cache.evictByTable("t_user", null, "tA", "ds1");
        Assert.assertNull(cache.get(k1));
        Assert.assertEquals("跨数据源同表名不应被连带失效", "B", cache.get(k2));
    }

    @Test
    public void tenantIsolated() {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 600);
        QueryCacheKey k1 = key("SELECT * FROM t_user", "tA", null, "ds1");
        QueryCacheKey k2 = key("SELECT * FROM t_user", "tB", null, "ds1");
        cache.put(k1, "A", "t_user", "tA");
        cache.put(k2, "B", "t_user", "tB");
        cache.evictByTable("t_user", null, "tA", "ds1");
        Assert.assertNull(cache.get(k1));
        Assert.assertEquals("跨租户同表名不应被连带失效", "B", cache.get(k2));
    }

    @Test
    public void schemaIsolated() {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 600);
        QueryCacheKey k1 = key("SELECT * FROM t_user", "tA", "sch1", "ds1");
        QueryCacheKey k2 = key("SELECT * FROM t_user", "tA", "sch2", "ds1");
        cache.put(k1, "A", "t_user", "tA");
        cache.put(k2, "B", "t_user", "tA");
        cache.evictByTable("t_user", "sch1", "tA", "ds1");
        Assert.assertNull(cache.get(k1));
        Assert.assertEquals("跨 schema 同表名不应被连带失效", "B", cache.get(k2));
    }

    @Test
    public void maxSizeEviction() throws Exception {
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
        Assert.assertTrue("容量上限应为 2，实际=" + size, size <= 2);
    }

    @Test
    public void ttlExpiry() throws Exception {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 1);
        QueryCacheKey k = key("SELECT * FROM t_user WHERE id=1", "tA", null, "ds1");
        cache.put(k, "A", "t_user", "tA");
        Assert.assertEquals("A", cache.get(k));
        Thread.sleep(1500);
        Assert.assertNull("TTL 到期后应失效", cache.get(k));
    }

    @Test
    public void noTtlKeepsValue() throws Exception {
        CaffeineQueryCache cache = new CaffeineQueryCache(100);
        QueryCacheKey k = key("SELECT * FROM t_user WHERE id=1", "tA", null, "ds1");
        cache.put(k, "A", "t_user", "tA");
        Thread.sleep(200);
        Assert.assertEquals("未设置 TTL 时值不应过期", "A", cache.get(k));
    }

    @Test
    public void clearAll() {
        CaffeineQueryCache cache = new CaffeineQueryCache(100, 600);
        QueryCacheKey k = key("SELECT * FROM t_user", "tA", null, "ds1");
        cache.put(k, "A", "t_user", "tA");
        cache.clear();
        Assert.assertNull(cache.get(k));
        Assert.assertEquals(0L, cache.estimatedSize());
    }

    @Test
    public void notDistributed() {
        Assert.assertFalse(new CaffeineQueryCache(100, 600).isDistributed());
    }

    @Test
    public void customMode() {
        QueryCacheConfig cfg = QueryCacheConfig.custom(new CaffeineQueryCache(100, 600));
        Assert.assertEquals(CacheMode.CUSTOM, cfg.getMode());
        Assert.assertTrue(cfg.getCache() instanceof CaffeineQueryCache);
    }

    @Test
    public void installAsLocalImplementation() {
        CaffeineQueryCache.install();
        try {
            QueryCacheConfig cfg = QueryCacheConfig.local(100, 600, 0);
            Assert.assertEquals(CacheMode.LOCAL, cfg.getMode());
            Assert.assertTrue("install() 后 LOCAL 应产出 Caffeine 实现", cfg.getCache() instanceof CaffeineQueryCache);
        } finally {
            CaffeineQueryCache.uninstall();
        }
    }

    @Test
    public void uninstallRestoresSimple() {
        CaffeineQueryCache.install();
        CaffeineQueryCache.uninstall();
        QueryCache cache = QueryCacheConfig.local(100, 600, 0).getCache();
        Assert.assertFalse("uninstall() 后应回退到内置实现", cache instanceof CaffeineQueryCache);
    }
}
