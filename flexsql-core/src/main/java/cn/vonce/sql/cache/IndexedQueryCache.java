package cn.vonce.sql.cache;

import cn.vonce.sql.uitls.SqlBeanUtil;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按主键精确失效装饰器（可选安全子集，{@link SqlBeanConfig#keyEvictionById} = true 时启用）。
 *
 * <p><b>装饰而非替换</b>：本类包一个 delegate {@link QueryCache}（Simple / Redis / Caffeine 任一）。
 * 3 个具体 cache 实现一行不动；装饰器内部维护一张"主键 → keys"反向索引。{@link #get(QueryCacheKey)}
 * 走 delegate，热路径零开销；{@link #put} 在写完 delegate 后再做一次反射 id 提取与索引登记。</p>
 *
 * <p><b>安全子集语义</b>：
 * <ul>
 *   <li>只有 put 的 value 是<b>单个 bean</b>（含 {@link cn.vonce.sql.annotation.SqlId} 主键字段）时建索引；
 *       list / 集合 / Map 等不建索引——list 类查询缓存项的失效仍走原 {@link #evictByTable} 全表清理。</li>
 *   <li>按主键失效只作用于"selectById 类缓存项"，不会误命中 list 查询（避免"按 id=5 失效把 id=3 list 也清了"）。</li>
 *   <li>所有失效路径都尊重原 cache 的多数据源 / 多 schema / 多租户隔离语义。</li>
 * </ul>
 *
 * <p><b>内存保护</b>：反向索引在 {@link #evictByTable}/{@link #evict}/{@link #clear} 时
 * 与 delegate 同步清理；delegate 内部 LRU/TTL 淘汰某 key 时本索引<b>不会</b>自动同步
 * （delegate 不通知），可能残留少量悬空指针，单 key 失效时调 {@code delegate.evict(...)}
 * 是空操作、不会报错。如对内存特别敏感，请仅在 LOCAL 短 TTL 场景启用。</p>
 *
 * @author Jovi
 * @since 1.7.2
 */
public class IndexedQueryCache implements QueryCache {

    /** 委托的实际 cache（Simple / Redis / Caffeine）。 */
    private final QueryCache delegate;

    /** {@code table -> (id -> 该 id 关联的 cacheKey 集合)}。三层 ConcurrentHashMap，全并发安全。 */
    private final ConcurrentMap<String, ConcurrentMap<Object, Set<QueryCacheKey>>> idIndex = new ConcurrentHashMap<>();

    public IndexedQueryCache(QueryCache delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate QueryCache 不能为 null");
        }
        this.delegate = delegate;
    }

    /** 获取被装饰的 cache（用于测试与 wiring）。 */
    public QueryCache getDelegate() {
        return delegate;
    }

    /** 当前已索引的 table 数（监控/测试）。 */
    public int trackedTableCount() {
        return idIndex.size();
    }

    /** 当前已索引的 (table, id) 组合数（监控/测试）。 */
    public long totalIndexedIds() {
        long total = 0;
        for (ConcurrentMap<Object, Set<QueryCacheKey>> m : idIndex.values()) {
            total += m.size();
        }
        return total;
    }

    @Override
    public Object get(QueryCacheKey key) {
        return delegate.get(key);
    }

    @Override
    public void put(QueryCacheKey key, Object value, String table, Object tenantId) {
        delegate.put(key, value, table, tenantId);
        recordIdIndex(key, value, table);
    }

    /**
     * 按主键精确失效。返回被失效的缓存项数（不存在的 table/id 返回 0）。
     * 调用方应在 SQL 写操作之后立即调用；与 {@link #evictByTable} 互不冲突，
     * 推荐同时调用——{@code evictById} 精确命中 selectById 类缓存项，
     * {@code evictByTable} 兜底 list 类缓存项，零漏失效。
     */
    public int evictById(String table, Object id) {
        if (table == null || id == null) {
            return 0;
        }
        ConcurrentMap<Object, Set<QueryCacheKey>> tableMap = idIndex.get(table);
        if (tableMap == null) {
            return 0;
        }
        Set<QueryCacheKey> keys = tableMap.remove(id);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (QueryCacheKey k : keys) {
            delegate.evict(k);
            count++;
        }
        return count;
    }

    /** 按主键集合批量失效。返回被失效的总缓存项数。 */
    public int evictByIds(String table, Collection<?> ids) {
        if (table == null || ids == null || ids.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Object id : ids) {
            total += evictById(table, id);
        }
        return total;
    }

    @Override
    public void evict(QueryCacheKey key) {
        if (key == null) {
            return;
        }
        delegate.evict(key);
    }

    @Override
    public void evictByTable(String table, String schema, Object tenantId, String dataSource) {
        delegate.evictByTable(table, schema, tenantId, dataSource);
        // 同步清理整张表的反向索引，避免悬空指针随时间堆积
        if (table != null) {
            idIndex.remove(table);
        }
    }

    @Override
    public void clear() {
        delegate.clear();
        idIndex.clear();
    }

    @Override
    public boolean isDistributed() {
        return delegate.isDistributed();
    }

    // ===== 索引登记 =====

    private void recordIdIndex(QueryCacheKey key, Object value, String table) {
        if (table == null || value == null || key == null) {
            return;
        }
        Object id = extractIdValue(value);
        if (id == null) {
            return;
        }
        idIndex.computeIfAbsent(table, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet())
                .add(key);
    }

    /**
     * 从 put 的 value 中提取主键值。语义"安全子集"：
     * <ul>
     *   <li>value 是 {@link Collection} / {@link Map} / 简单类型（String / Number / Boolean） → 返回 null，不建索引</li>
     *   <li>value 是单个 bean → 通过 {@link SqlBeanUtil#getIdField(Class)} 反射拿 @SqlId 字段取值</li>
     *   <li>SqlBeanUtil 抛任何异常 → 返回 null，不建索引（不污染）</li>
     * </ul>
     * 注意：put 是写路径（非热点），不缓存 Class → Field 反射结果以避免内存泄漏与多 ClassLoader 兼容问题；
     * 这里即使每次反射开销也只占总写操作的极小部分。如有用户报告反射热路径开销再考虑缓存。
     */
    private static Object extractIdValue(Object value) {
        if (value instanceof Collection || value instanceof Map) {
            return null;
        }
        Class<?> clazz = value.getClass();
        if (clazz == String.class
                || clazz == Boolean.class
                || Number.class.isAssignableFrom(clazz)
                || clazz.isPrimitive()) {
            return null;
        }
        try {
            Field idField = SqlBeanUtil.getIdField(clazz);
            if (idField == null) {
                return null;
            }
            idField.setAccessible(true);
            Object idValue = idField.get(value);
            return idValue == null ? null : idValue;
        } catch (Throwable t) {
            // 不污染：反射失败（无 @SqlId 注解）一律跳过
            return null;
        }
    }

    // 仅供测试用：返回只读视图的副本，避免外部 ConcurrentModification
    Map<String, Map<Object, List<QueryCacheKey>>> snapshotIdIndex() {
        java.util.LinkedHashMap<String, Map<Object, List<QueryCacheKey>>> snapshot = new java.util.LinkedHashMap<>();
        idIndex.forEach((table, m) -> {
            java.util.LinkedHashMap<Object, List<QueryCacheKey>> inner = new java.util.LinkedHashMap<>();
            m.forEach((id, set) -> inner.put(id, Collections.unmodifiableList(new java.util.ArrayList<>(set))));
            snapshot.put(table, Collections.unmodifiableMap(inner));
        });
        return Collections.unmodifiableMap(snapshot);
    }
}
