package cn.vonce.sql.cache;

/**
 * 查询缓存接口（后端无关）
 * <p>核心只缓存「查询结果数据」（即 SqlBeanMapper 映射出来的 bean / bean 列表）。
 * 实现可以是本地内存（Caffeine / 简易 Map）或分布式（Redis）。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public interface QueryCache {

    /**
     * 读取缓存
     *
     * @param key 缓存键（含租户、动态 schema、SQL 等）
     * @return 缓存的结果对象；未命中返回 null
     */
    Object get(QueryCacheKey key);

    /**
     * 写入缓存
     *
     * @param key        缓存键（其 {@code schema} 字段会进入按表失效索引，实现跨 schema 隔离）
     * @param value      结果对象（建议调用方传入深拷贝副本，避免被外部引用篡改）
     * @param table      该查询主表名（用于写时按表失效）；可空
     * @param tenantId   租户 ID（用于写时按表+租户精准失效）；可空
     */
    void put(QueryCacheKey key, Object value, String table, Object tenantId);

    /**
     * 按「表 + 动态schema + 租户 + 数据源」失效。任意写操作（insert/update/delete/copy/backup）成功后调用，
     * 清掉所有 key 含该表（且 schema、租户、数据源均匹配）的缓存项，保证读不到脏数据，
     * 同时避免跨 schema / 跨数据源同表名被过度失效。
     *
     * @param table       表名
     * @param schema      动态 schema（null 或空表示默认 schema）
     * @param tenantId    租户 ID（null 表示不区分租户）
     * @param dataSource  数据源名（null 或空表示默认/单一数据源）
     */
    void evictByTable(String table, String schema, Object tenantId, String dataSource);

    /** 清空全部缓存 */
    void clear();

    /** 是否为分布式缓存（如 Redis）。本地缓存返回 false，分布式返回 true。 */
    default boolean isDistributed() {
        return false;
    }

}
