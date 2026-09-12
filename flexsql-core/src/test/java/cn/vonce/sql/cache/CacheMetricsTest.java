package cn.vonce.sql.cache;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

/**
 * CacheMetrics / QueryCacheStats 单元测试（JUnit）。
 * <p>注意：CacheMetrics 是 JVM 全局静态计数器，每个用例开头都会 reset，{@link #restoreMetricsState()}
 * 再复位为开启状态，避免影响其它测试。</p>
 */
public class CacheMetricsTest {

    /** 复位全局静态状态：开启计数 + 清零，避免污染同 JVM 的其它用例。 */
    @After
    public void restoreMetricsState() {
        CacheMetrics.setEnabled(true);
        CacheMetrics.reset();
    }

    @Test
    public void resetIsClean() {
        CacheMetrics.reset();
        QueryCacheStats s = CacheMetrics.snapshot();
        Assert.assertEquals(0L, s.getHitCount());
        Assert.assertEquals(0L, s.getMissCount());
        Assert.assertEquals(0L, s.getLoadCount());
        Assert.assertEquals(0L, s.getPutCount());
        Assert.assertEquals(0L, s.getEvictCount());
        Assert.assertEquals(0L, s.getTotalCount());
        Assert.assertEquals(0D, s.getHitRate(), 1e-9);
    }

    @Test
    public void hitMissRate() {
        CacheMetrics.reset();
        for (int i = 0; i < 3; i++) {
            CacheMetrics.recordHit("t_user");
        }
        CacheMetrics.recordMiss("t_user");
        QueryCacheStats s = CacheMetrics.snapshot();
        Assert.assertEquals(3L, s.getHitCount());
        Assert.assertEquals(1L, s.getMissCount());
        Assert.assertEquals(4L, s.getTotalCount());
        Assert.assertEquals(0.75D, s.getHitRate(), 1e-9);
    }

    @Test
    public void loadPutEvict() {
        CacheMetrics.reset();
        CacheMetrics.recordMiss("t_user");
        CacheMetrics.recordMiss("t_user");
        CacheMetrics.recordLoad("t_user");
        CacheMetrics.recordPut("t_user");
        CacheMetrics.recordEvict("t_user");
        QueryCacheStats s = CacheMetrics.snapshot();
        // 两次 miss 只回源一次：差值体现并发击穿保护复用
        Assert.assertEquals(2L, s.getMissCount());
        Assert.assertEquals(1L, s.getLoadCount());
        Assert.assertEquals(1L, s.getPutCount());
        Assert.assertEquals(1L, s.getEvictCount());
    }

    @Test
    public void byTable() {
        CacheMetrics.reset();
        CacheMetrics.recordHit("t_user");
        CacheMetrics.recordHit("t_user");
        CacheMetrics.recordMiss("t_user");
        CacheMetrics.recordMiss("t_order");
        Map<String, QueryCacheStats> byTable = CacheMetrics.snapshotByTable();
        Assert.assertEquals(2, byTable.size());
        QueryCacheStats user = byTable.get("t_user");
        QueryCacheStats order = byTable.get("t_order");
        Assert.assertNotNull(user);
        Assert.assertNotNull(order);
        Assert.assertEquals(2L, user.getHitCount());
        Assert.assertEquals(1L, user.getMissCount());
        Assert.assertEquals(0L, order.getHitCount());
        Assert.assertEquals(1L, order.getMissCount());
        // 全局是各表之和
        QueryCacheStats g = CacheMetrics.snapshot();
        Assert.assertEquals(2L, g.getHitCount());
        Assert.assertEquals(2L, g.getMissCount());
    }

    @Test
    public void nullTableOnlyGlobal() {
        CacheMetrics.reset();
        CacheMetrics.recordHit(null);
        Assert.assertEquals(1L, CacheMetrics.snapshot().getHitCount());
        Assert.assertFalse(CacheMetrics.snapshotByTable().containsKey(null));
    }

    @Test
    public void disabledShortCircuit() {
        CacheMetrics.reset();
        CacheMetrics.setEnabled(false);
        try {
            CacheMetrics.recordHit("t_user");
            CacheMetrics.recordMiss("t_user");
            CacheMetrics.recordEvict("t_user");
            QueryCacheStats s = CacheMetrics.snapshot();
            Assert.assertFalse(CacheMetrics.isEnabled());
            Assert.assertEquals(0L, s.getTotalCount());
            Assert.assertEquals(0L, s.getEvictCount());
        } finally {
            CacheMetrics.setEnabled(true);
        }
    }

    @Test
    public void snapshotIsImmutableView() {
        CacheMetrics.reset();
        CacheMetrics.recordHit("t_user");
        Map<String, QueryCacheStats> snapshot = CacheMetrics.snapshotByTable();
        try {
            snapshot.clear();
            Assert.fail("快照应不可修改（clear 应抛 UnsupportedOperationException）");
        } catch (UnsupportedOperationException expected) {
            Assert.assertTrue(CacheMetrics.snapshotByTable().containsKey("t_user"));
        }
    }

    @Test
    public void toStringContainsRate() {
        CacheMetrics.reset();
        CacheMetrics.recordHit("t_user");
        CacheMetrics.recordMiss("t_user");
        Assert.assertTrue(CacheMetrics.snapshot().toString().contains("hitRate=50.0%"));
    }

    @Test
    public void reporterRegistration() {
        CacheMetrics.reset();
        int before = CacheMetrics.reporterCount();
        CacheMetricsReporter r1 = (g, t) -> {
        };
        CacheMetricsReporter r2 = (g, t) -> {
        };
        try {
            CacheMetrics.addReporter(r1);
            CacheMetrics.addReporter(r1); // 重复注册应被忽略
            CacheMetrics.addReporter(r2);
            CacheMetrics.addReporter(null); // null 应忽略
            Assert.assertEquals("重复/null 注册应被忽略", before + 2, CacheMetrics.reporterCount());
            CacheMetrics.removeReporter(r1);
            CacheMetrics.removeReporter(null); // null 应忽略
            Assert.assertEquals(before + 1, CacheMetrics.reporterCount());
        } finally {
            CacheMetrics.removeReporter(r1);
            CacheMetrics.removeReporter(r2);
        }
        Assert.assertEquals("清理后应回到初始数量", before, CacheMetrics.reporterCount());
    }

    @Test
    public void reportNowDelivers() {
        CacheMetrics.reset();
        CacheMetrics.recordHit("t_user");
        CacheMetrics.recordMiss("t_user");
        CacheMetrics.recordLoad("t_user");
        final QueryCacheStats[] capturedGlobal = new QueryCacheStats[1];
        final Map<String, QueryCacheStats>[] capturedTable = new Map[1];
        CacheMetricsReporter r = (g, t) -> {
            capturedGlobal[0] = g;
            capturedTable[0] = t;
        };
        CacheMetrics.addReporter(r);
        try {
            CacheMetrics.reportNow();
            Assert.assertNotNull("应把全局快照推送给 reporter", capturedGlobal[0]);
            Assert.assertNotNull("应把分表快照推送给 reporter", capturedTable[0]);
            Assert.assertEquals(1L, capturedGlobal[0].getHitCount());
            Assert.assertEquals(1L, capturedGlobal[0].getMissCount());
            Assert.assertEquals(1L, capturedGlobal[0].getLoadCount());
            Assert.assertEquals(1L, capturedTable[0].get("t_user").getHitCount());
        } finally {
            CacheMetrics.removeReporter(r);
        }
    }

    @Test
    public void reporterExceptionIsolated() {
        CacheMetrics.reset();
        final boolean[] secondCalled = {false};
        CacheMetricsReporter bad = (g, t) -> {
            throw new RuntimeException("boom");
        };
        CacheMetricsReporter good = (g, t) -> secondCalled[0] = true;
        CacheMetrics.addReporter(bad);
        CacheMetrics.addReporter(good);
        try {
            CacheMetrics.reportNow(); // bad 抛异常，good 必须仍被调用
            Assert.assertTrue("单个 reporter 抛异常不应影响后续 reporter", secondCalled[0]);
        } finally {
            CacheMetrics.removeReporter(bad);
            CacheMetrics.removeReporter(good);
        }
    }

    @Test
    public void reportNowWithoutReporterIsSafe() {
        CacheMetrics.reset();
        CacheMetrics.reportNow(); // 无 reporter 时不应抛异常
        Assert.assertNotNull(CacheMetrics.snapshot());
    }
}
