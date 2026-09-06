package cn.vonce.sql.cache;

import java.util.Map;

/**
 * CacheMetrics / QueryCacheStats 单元测试（main 风格，无需 DB）。
 * <p>注意：CacheMetrics 是 JVM 全局静态计数器，每个用例开头都会 reset，且末尾复位为开启状态，
 * 避免影响其它测试。</p>
 */
public class CacheMetricsTest {

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

    static boolean resetIsClean() {
        CacheMetrics.reset();
        QueryCacheStats s = CacheMetrics.snapshot();
        return s.getHitCount() == 0 && s.getMissCount() == 0 && s.getLoadCount() == 0
                && s.getPutCount() == 0 && s.getEvictCount() == 0
                && s.getTotalCount() == 0 && s.getHitRate() == 0D;
    }

    static boolean hitMissRate() {
        CacheMetrics.reset();
        for (int i = 0; i < 3; i++) {
            CacheMetrics.recordHit("t_user");
        }
        CacheMetrics.recordMiss("t_user");
        QueryCacheStats s = CacheMetrics.snapshot();
        boolean global = s.getHitCount() == 3 && s.getMissCount() == 1 && s.getTotalCount() == 4;
        return global && Math.abs(s.getHitRate() - 0.75D) < 1e-9;
    }

    static boolean loadPutEvict() {
        CacheMetrics.reset();
        CacheMetrics.recordMiss("t_user");
        CacheMetrics.recordMiss("t_user");
        CacheMetrics.recordLoad("t_user");
        CacheMetrics.recordPut("t_user");
        CacheMetrics.recordEvict("t_user");
        QueryCacheStats s = CacheMetrics.snapshot();
        // 两次 miss 只回源一次：差值体现并发击穿保护复用
        return s.getMissCount() == 2 && s.getLoadCount() == 1
                && s.getPutCount() == 1 && s.getEvictCount() == 1;
    }

    static boolean byTable() {
        CacheMetrics.reset();
        CacheMetrics.recordHit("t_user");
        CacheMetrics.recordHit("t_user");
        CacheMetrics.recordMiss("t_user");
        CacheMetrics.recordMiss("t_order");
        Map<String, QueryCacheStats> byTable = CacheMetrics.snapshotByTable();
        QueryCacheStats user = byTable.get("t_user");
        QueryCacheStats order = byTable.get("t_order");
        boolean ok = byTable.size() == 2 && user != null && order != null;
        ok = ok && user.getHitCount() == 2 && user.getMissCount() == 1;
        ok = ok && order.getHitCount() == 0 && order.getMissCount() == 1;
        // 全局是各表之和
        QueryCacheStats g = CacheMetrics.snapshot();
        return ok && g.getHitCount() == 2 && g.getMissCount() == 2;
    }

    static boolean nullTableOnlyGlobal() {
        CacheMetrics.reset();
        CacheMetrics.recordHit(null);
        QueryCacheStats g = CacheMetrics.snapshot();
        return g.getHitCount() == 1 && !CacheMetrics.snapshotByTable().containsKey(null);
    }

    static boolean disabledShortCircuit() {
        CacheMetrics.reset();
        CacheMetrics.setEnabled(false);
        try {
            CacheMetrics.recordHit("t_user");
            CacheMetrics.recordMiss("t_user");
            CacheMetrics.recordEvict("t_user");
            QueryCacheStats s = CacheMetrics.snapshot();
            return !CacheMetrics.isEnabled() && s.getTotalCount() == 0 && s.getEvictCount() == 0;
        } finally {
            CacheMetrics.setEnabled(true);
        }
    }

    static boolean snapshotIsImmutableView() {
        CacheMetrics.reset();
        CacheMetrics.recordHit("t_user");
        Map<String, QueryCacheStats> snapshot = CacheMetrics.snapshotByTable();
        try {
            snapshot.clear();
            return false; // 不可修改，走到这里说明没抛异常
        } catch (UnsupportedOperationException e) {
            return CacheMetrics.snapshotByTable().containsKey("t_user");
        }
    }

    static boolean toStringContainsRate() {
        CacheMetrics.reset();
        CacheMetrics.recordHit("t_user");
        CacheMetrics.recordMiss("t_user");
        String text = CacheMetrics.snapshot().toString();
        return text.contains("hitRate=50.0%");
    }

    public static void main(String[] args) {
        check("resetIsClean", resetIsClean());
        check("hitMissRate", hitMissRate());
        check("loadPutEvict", loadPutEvict());
        check("byTable", byTable());
        check("nullTableOnlyGlobal", nullTableOnlyGlobal());
        check("disabledShortCircuit", disabledShortCircuit());
        check("snapshotIsImmutableView", snapshotIsImmutableView());
        check("toStringContainsRate", toStringContainsRate());

        // 复位，避免影响其它测试
        CacheMetrics.setEnabled(true);
        CacheMetrics.reset();

        System.out.println("\nCacheMetricsTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
