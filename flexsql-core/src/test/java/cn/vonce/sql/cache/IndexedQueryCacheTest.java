package cn.vonce.sql.cache;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link IndexedQueryCache} 单元测试（JUnit）。
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

    @Test
    public void singleBeanPutBuildsIndexAndEvictById() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        QueryCacheKey key = newKey("user");
        idx.put(key, new IndexedUser(7L, "alice"), "user", null);

        Assert.assertEquals("单 bean 应建立 1 张表的索引", 1, idx.trackedTableCount());
        Assert.assertEquals("应记录 1 个 id", 1, idx.totalIndexedIds());

        Assert.assertEquals("evictById 应命中 1 条", 1, idx.evictById("user", 7L));
        Assert.assertNull("被失效的缓存项应已从底层缓存移除", base.get(key));
    }

    @Test
    public void listBeanPutDoesNotIndex() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        QueryCacheKey key = newKey("userList");
        List<IndexedUser> users = new ArrayList<>(Arrays.asList(new IndexedUser(1L, "a"), new IndexedUser(2L, "b")));
        idx.put(key, users, "user", null);

        // 反向索引必须没建（list 不索引）
        Assert.assertEquals("List 结果不建反向索引", 0, idx.trackedTableCount());
        Assert.assertEquals("List 结果 evictById 应为 0", 0, idx.evictById("user", 1L));
    }

    @Test
    public void nonIdBeanPutSkipsIndex() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        QueryCacheKey key = newKey("plain");
        idx.put(key, new NonIdBean(), "plain", null);

        Assert.assertEquals(0, idx.trackedTableCount());
        Assert.assertEquals(0, idx.totalIndexedIds());
        Assert.assertEquals(0, idx.evictById("plain", 0L));
    }

    @Test
    public void primitiveAndStringValuesSkipIndex() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        idx.put(newKey("k1"), "hello", "literal", null);
        idx.put(newKey("k2"), Long.valueOf(123), "literal", null);
        idx.put(newKey("k3"), Boolean.TRUE, "literal", null);
        Assert.assertEquals("非 bean 值一律不建索引", 0, idx.trackedTableCount());
    }

    @Test
    public void evictByIdOnMissingTableReturnsZero() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        idx.put(newKey("t"), new IndexedUser(1L, "x"), "t", null);
        Assert.assertEquals("未知 table → 0（且不抛异常）", 0, idx.evictById("nonexistent", 1L));
        Assert.assertEquals("未知 id → 0", 0, idx.evictById("t", 999L));
    }

    @Test
    public void evictByIdsBatch() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        idx.put(newKey("k1"), new IndexedUser(1L, "a"), "u", null);
        idx.put(newKey("k2"), new IndexedUser(2L, "b"), "u", null);
        idx.put(newKey("k3"), new IndexedUser(3L, "c"), "u", null);

        int total = idx.evictByIds("u", Arrays.asList(1L, 2L, 999L));
        // 1L 和 2L 各失效 1 条；999L 不存在 → 总数 2
        Assert.assertEquals(2, total);
        Assert.assertNull(base.get(newKey("k1")));
        Assert.assertNull(base.get(newKey("k2")));
        Assert.assertNotNull("未在失效列表中的 id 应保留", base.get(newKey("k3")));
    }

    @Test
    public void evictByTableClearsIndex() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        idx.put(newKey("k1"), new IndexedUser(1L, "a"), "u", null);
        idx.put(newKey("k2"), new IndexedUser(2L, "b"), "u", null);

        idx.evictByTable("u", null, null, null);

        Assert.assertEquals("按表失效应同时清空反向索引", 0, idx.trackedTableCount());
        Assert.assertNull(base.get(newKey("k1")));
        Assert.assertNull(base.get(newKey("k2")));
        Assert.assertEquals("索引清空后 evictById 应为 0", 0, idx.evictById("u", 1L));
    }

    @Test
    public void clearClearsIndex() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        idx.put(newKey("k1"), new IndexedUser(1L, "a"), "u", null);

        idx.clear();
        Assert.assertEquals(0, idx.trackedTableCount());
        Assert.assertEquals(0, idx.totalIndexedIds());
    }

    @Test
    public void multipleSameIdSharesKey() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        // 同一个 id 同一行被 put 进多个 cache key（比如：默认 + 含租户）
        QueryCacheKey kDefault = newKey("u");
        QueryCacheKey kTenant = newKeyWithTenant("u", "tenantA");
        idx.put(kDefault, new IndexedUser(5L, "x"), "u", null);
        idx.put(kTenant, new IndexedUser(5L, "y"), "u", "tenantA");

        Assert.assertEquals("同一 id 的多个缓存键都应被失效", 2, idx.evictById("u", 5L));
        Assert.assertNull(base.get(kDefault));
        Assert.assertNull(base.get(kTenant));
    }

    @Test
    public void delegatesReadAndIsDistributed() {
        SimpleQueryCache base = new SimpleQueryCache(100, 60);
        IndexedQueryCache idx = new IndexedQueryCache(base);
        QueryCacheKey key = newKey("u");
        idx.put(key, new IndexedUser(1L, "a"), "u", null);

        // get 走 delegate
        Assert.assertTrue("get 应委托给底层缓存", idx.get(key) instanceof IndexedUser);
        Assert.assertFalse("本地缓存不应标记为分布式", idx.isDistributed());
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
