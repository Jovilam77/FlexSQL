package cn.vonce.sql.cache;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link QueryCacheKey} 的「操作维度」回归测试。
 *
 * <p><b>背景（2026-09-12 修复的 P0）</b>：{@code select} / {@code selectOne} / {@code selectMap} /
 * {@code selectMapList} 四个方法都用<b>同一段 SQL</b>（{@code SqlBeanProvider.selectSql}），
 * 但返回形态分别是 {@code List<T>} / {@code T} / {@code Map} / {@code List<Map>}。
 * 修复前缓存键只含「实体 + 返回类型 + SQL + 租户 + schema + 数据源 + 分页」，
 * 于是四者完全同键：先 {@code select} 缓存了 {@code List}，随后 {@code selectOne}
 * 命中同一键就把整个 List 返回 → 调用方 {@code ClassCastException}（或静默拿到错类型数据）。</p>
 *
 * <p>因此键必须包含操作标识。本测试锁死这一点，并保证原有各维度（租户/schema/数据源/分页/SQL）
 * 的隔离能力没有被破坏。</p>
 *
 * @author Jovi
 */
public class QueryCacheKeyOperationTest {

    private static QueryCacheKey key(String operation) {
        return new QueryCacheKey(String.class, null, operation, "SELECT id FROM t_user",
                "tenantA", "schemaA", "", "dsA");
    }

    @Test
    public void sameSqlDifferentOperationMustNotCollide() {
        QueryCacheKey asList = key("select");
        QueryCacheKey asOne = key("selectOne");
        QueryCacheKey asMap = key("selectMap");
        QueryCacheKey asMapList = key("selectMapList");

        Assert.assertNotEquals("select 与 selectOne 不能同键", asList, asOne);
        Assert.assertNotEquals("select 与 selectMap 不能同键", asList, asMap);
        Assert.assertNotEquals("select 与 selectMapList 不能同键", asList, asMapList);
        Assert.assertNotEquals("selectOne 与 selectMap 不能同键", asOne, asMap);
        Assert.assertNotEquals("selectOne 与 selectMapList 不能同键", asOne, asMapList);
        Assert.assertNotEquals("selectMap 与 selectMapList 不能同键", asMap, asMapList);
    }

    @Test
    public void hashCodeAndStoreKeyIncludeOperation() {
        QueryCacheKey asList = key("select");
        QueryCacheKey asOne = key("selectOne");

        // hashCode 若漏掉 operation，在 HashMap/ConcurrentHashMap 中会退化为同键
        Assert.assertNotEquals(asList.hashCode(), asOne.hashCode());
        // Redis 通道用 toStoreKey 做真实 key，同样必须区分
        Assert.assertNotEquals(asList.toStoreKey(), asOne.toStoreKey());
    }

    @Test
    public void sameOperationSameContextProducesEqualKeys() {
        Assert.assertEquals(key("select"), key("select"));
        Assert.assertEquals(key("select").toStoreKey(), key("select").toStoreKey());
        Assert.assertEquals(key("selectOne").hashCode(), key("selectOne").hashCode());
    }

    @Test
    public void operationIsExposed() {
        Assert.assertEquals("selectOne", key("selectOne").getOperation());
    }

    @Test
    public void contextDimensionsStillIsolate() {
        String sql = "SELECT id FROM t_user";
        QueryCacheKey base = new QueryCacheKey(String.class, null, "select", sql, "t1", "s1", "", "ds1");

        Assert.assertNotEquals("租户必须隔离",
                base, new QueryCacheKey(String.class, null, "select", sql, "t2", "s1", "", "ds1"));
        Assert.assertNotEquals("动态 schema 必须隔离",
                base, new QueryCacheKey(String.class, null, "select", sql, "t1", "s2", "", "ds1"));
        Assert.assertNotEquals("数据源必须隔离",
                base, new QueryCacheKey(String.class, null, "select", sql, "t1", "s1", "", "ds2"));
        Assert.assertNotEquals("SQL 必须隔离",
                base, new QueryCacheKey(String.class, null, "select", "SELECT id FROM t_other", "t1", "s1", "", "ds1"));
        Assert.assertNotEquals("返回类型必须隔离",
                base, new QueryCacheKey(Integer.class, null, "select", sql, "t1", "s1", "", "ds1"));
        Assert.assertNotEquals("分页必须隔离",
                base, new QueryCacheKey(String.class, null, "select", sql, "t1", "s1", "1,10", "ds1"));
    }

    /**
     * 向后兼容：旧的构造器（无 operation）仍可用，operation 视为空串。
     * 旧构造器仅供外部/测试直接构造键使用，框架内部一律走带 operation 的构造器。
     */
    @Test
    public void legacyConstructorsRemainCompatible() {
        QueryCacheKey a = new QueryCacheKey(String.class, null, "SELECT 1", "t1", null, "");
        QueryCacheKey b = new QueryCacheKey(String.class, null, "SELECT 1", "t1", null, "");

        Assert.assertEquals("", a.getOperation());
        Assert.assertEquals(a, b);
        Assert.assertEquals(a.hashCode(), b.hashCode());
        Assert.assertEquals(a.toStoreKey(), b.toStoreKey());
    }
}
