package cn.vonce.sql.cache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link IndexedQueryCache} 单元测试（main 风格）。
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>单 Bean put → 反向索引建立 → {@code evictById} 命中</li>
 *   <li>List / Collection / Map / String / Number put → <b>不</b>建索引（安全子集语义）</li>
 *   <li>不带 {@code @SqlId} 的 bean put → 跳过索引，反射异常不影响</li>
 *   <li>{@code evictByIds} 批量失效</li>
 *   <li>{@code evictByTable} + {@code clear} 同时清空反向索引</li>
 *   <li>不存在的 table / id → 返回 0，不抛异常</li>
 * </ul>
 */
public class IndexedQueryCacheTest {

    public static boolean singleBeanPutBuildsIndexAndEvictById() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        QueryCacheKey key = newKey("user");
        idx.put(key, new IndexedUser(7L, "alice"), "user", null);

        if (idx.trackedTableCount() != 1) return false;
        if (idx.totalIndexedIds() != 1) return false;

        int n = idx.evictById("user", 7L);
        if (n != 1) return false;
        return base.get(key) == null;
    }

    public static boolean listBeanPutDoesNotIndex() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        QueryCacheKey key = newKey("userList");
        List<IndexedUser> users = new ArrayList<>(Arrays.asList(new IndexedUser(1L, "a"), new IndexedUser(2L, "b")));
        idx.put(key, users, "user", null);

        // 反向索引必须没建（list 不索引）
        if (idx.trackedTableCount() != 0) return false;
        return idx.evictById("user", 1L) == 0;
    }

    public static boolean nonIdBeanPutSkipsIndex() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        QueryCacheKey key = newKey("plain");
        idx.put(key, new NonIdBean(), "plain", null);

        return idx.trackedTableCount() == 0
                && idx.totalIndexedIds() == 0
                && idx.evictById("plain", 0L) == 0;
    }

    public static boolean primitiveAndStringValuesSkipIndex() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        idx.put(newKey("k1"), "hello", "literal", null);
        idx.put(newKey("k2"), Long.valueOf(123), "literal", null);
        idx.put(newKey("k3"), Boolean.TRUE, "literal", null);
        return idx.trackedTableCount() == 0;
    }

    public static boolean evictByIdOnMissingTableReturnsZero() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        idx.put(newKey("t"), new IndexedUser(1L, "x"), "t", null);
        return idx.evictById("nonexistent", 1L) == 0
                && idx.evictById("t", 999L) == 0;
    }

    public static boolean evictByIdsBatch() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        idx.put(newKey("k1"), new IndexedUser(1L, "a"), "u", null);
        idx.put(newKey("k2"), new IndexedUser(2L, "b"), "u", null);
        idx.put(newKey("k3"), new IndexedUser(3L, "c"), "u", null);

        int total = idx.evictByIds("u", Arrays.asList(1L, 2L, 999L));
        // 1L 和 2L 各失效 1 条；999L 不存在 → 总数 2
        return total == 2
                && base.get(newKey("k1")) == null
                && base.get(newKey("k2")) == null
                && base.get(newKey("k3")) != null;
    }

    public static boolean evictByTableClearsIndex() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        idx.put(newKey("k1"), new IndexedUser(1L, "a"), "u", null);
        idx.put(newKey("k2"), new IndexedUser(2L, "b"), "u", null);

        idx.evictByTable("u", null, null, null);

        boolean indexCleared = idx.trackedTableCount() == 0;
        boolean storeCleared = base.get(newKey("k1")) == null && base.get(newKey("k2")) == null;
        boolean evictAfterReturnsZero = idx.evictById("u", 1L) == 0;
        return indexCleared && storeCleared && evictAfterReturnsZero;
    }

    public static boolean clearClearsIndex() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        idx.put(newKey("k1"), new IndexedUser(1L, "a"), "u", null);

        idx.clear();
        return idx.trackedTableCount() == 0 && idx.totalIndexedIds() == 0;
    }

    public static boolean multipleSameIdSharesKey() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        // 同一个 id 同一行被 put 进多个 cache key（比如：默认 + 含租户）
        QueryCacheKey kDefault = newKey("u");
        QueryCacheKey kTenant = newKeyWithTenant("u", "tenantA");
        idx.put(kDefault, new IndexedUser(5L, "x"), "u", null);
        idx.put(kTenant, new IndexedUser(5L, "y"), "u", "tenantA");

        int n = idx.evictById("u", 5L);
        return n == 2
                && base.get(kDefault) == null
                && base.get(kTenant) == null;
    }

    public static boolean delegatesReadAndIsDistributed() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        QueryCacheKey key = newKey("u");
        idx.put(key, new IndexedUser(1L, "a"), "u", null);

        // get 走 delegate
        Object got = idx.get(key);
        boolean readOk = got instanceof IndexedUser;
        boolean notDistributed = !idx.isDistributed();
        return readOk && notDistributed;
    }

    public static void main(String[] args) {
        check("singleBeanPutBuildsIndexAndEvictById", singleBeanPutBuildsIndexAndEvictById());
        check("listBeanPutDoesNotIndex", listBeanPutDoesNotIndex());
        check("nonIdBeanPutSkipsIndex", nonIdBeanPutSkipsIndex());
        check("primitiveAndStringValuesSkipIndex", primitiveAndStringValuesSkipIndex());
        check("evictByIdOnMissingTableReturnsZero", evictByIdOnMissingTableReturnsZero());
        check("evictByIdsBatch", evictByIdsBatch());
        check("evictByTableClearsIndex", evictByTableClearsIndex());
        check("clearClearsIndex", clearClearsIndex());
        check("multipleSameIdSharesKey", multipleSameIdSharesKey());
        check("delegatesReadAndIsDistributed", delegatesReadAndIsDistributed());

        System.out.println("passed, " + passed + " failed, " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("PASS: " + name);
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    private static QueryCacheKey newKey(String sql) {
        return new QueryCacheKey(IndexedUser.class, IndexedUser.class, sql,
                null, null, null, null);
    }

    private static QueryCacheKey newKeyWithTenant(String sql, String tenant) {
        return new QueryCacheKey(IndexedUser.class, IndexedUser.class, sql,
                tenant, null, null, null);
    }
}
