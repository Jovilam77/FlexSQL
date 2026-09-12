package cn.vonce.sql.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 查询缓存键
 * <p>键由「实体类 + 返回类型 + 操作 + 拼装好的 SQL（参数已内联）+ 租户 + 动态schema + 数据源 + 分页」构成。
 * 注意：必须包含 {@code tenantId}、{@code schema} 与 {@code dataSource}，否则多租户/动态 schema/多数据源场景下
 * 缓存会串号、造成数据越权泄漏或跨数据源读到错误数据。</p>
 * <p><b>{@code operation} 说明</b>：{@code select} / {@code selectOne} / {@code selectMap} / {@code selectMapList}
 * 等方法会用<b>同一段 SQL</b>（{@code SqlBeanProvider.selectSql}），但返回形态完全不同
 * （{@code List<T>} / {@code T} / {@code Map} / {@code List<Map>}）。
 * 若键里不带操作名，先 {@code select} 缓存了 {@code List}，随后 {@code selectOne} 命中同一键就会把整个
 * {@code List} 直接返回，调用方按单个对象接收时抛 {@code ClassCastException}。因此键必须包含操作标识。</p>
 *
 * @author Jovi
 * @version 1.2
 */
public class QueryCacheKey {

    private final String beanClassName;
    private final String returnTypeName;
    private final String operation;
    private final String sql;
    private final String tenantId;
    private final String schema;
    private final String dataSource;
    private final String paging;

    public QueryCacheKey(Class<?> beanClass, Class<?> returnType, String sql,
                         String tenantId, String schema, String paging) {
        this(beanClass, returnType, sql, tenantId, schema, paging, "");
    }

    public QueryCacheKey(Class<?> beanClass, Class<?> returnType, String sql,
                         String tenantId, String schema, String paging, String dataSource) {
        this(beanClass, returnType, null, sql, tenantId, schema, paging, dataSource);
    }

    /**
     * @param operation 操作名（如 select/selectOne/selectMap/count）；不同返回形态的方法必须区分，
     *                  否则同一段 SQL 会串键。见类注释。
     */
    public QueryCacheKey(Class<?> beanClass, Class<?> returnType, String operation, String sql,
                         String tenantId, String schema, String paging, String dataSource) {
        this.beanClassName = beanClass == null ? "" : beanClass.getName();
        this.returnTypeName = returnType == null ? "" : returnType.getName();
        this.operation = operation == null ? "" : operation;
        this.sql = sql == null ? "" : sql;
        this.tenantId = tenantId == null ? "" : String.valueOf(tenantId);
        this.schema = schema == null ? "" : schema;
        this.dataSource = dataSource == null ? "" : dataSource;
        this.paging = paging == null ? "" : paging;
    }

    public String getBeanClassName() {
        return beanClassName;
    }

    public String getReturnTypeName() {
        return returnTypeName;
    }

    public String getOperation() {
        return operation;
    }

    public String getSql() {
        return sql;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSchema() {
        return schema;
    }

    public String getDataSource() {
        return dataSource;
    }

    public String getPaging() {
        return paging;
    }

    /** 生成用于 Redis 等外部存储的短键（避免超长 SQL 直接做 key） */
    public String toStoreKey() {
        String raw = beanClassName + "|" + returnTypeName + "|" + operation + "|" + sql + "|"
                + tenantId + "|" + schema + "|" + dataSource + "|" + paging;
        return "flexsql:q:" + md5(raw);
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // 退化为 hash（极端情况下 MD5 不可用）
            return "h" + Integer.toHexString(Objects.hashCode(input));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryCacheKey that = (QueryCacheKey) o;
        return Objects.equals(beanClassName, that.beanClassName)
                && Objects.equals(returnTypeName, that.returnTypeName)
                && Objects.equals(operation, that.operation)
                && Objects.equals(sql, that.sql)
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(schema, that.schema)
                && Objects.equals(dataSource, that.dataSource)
                && Objects.equals(paging, that.paging);
    }

    @Override
    public int hashCode() {
        return Objects.hash(beanClassName, returnTypeName, operation, sql, tenantId, schema, dataSource, paging);
    }

    @Override
    public String toString() {
        return toStoreKey();
    }
}
