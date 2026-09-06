package cn.vonce.sql.define;

import cn.vonce.sql.enumerate.DbType;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 方言支持元数据（不可变 POJO）。
 * <p>描述某个 {@link SqlFun} 函数在哪些数据库 / 哪些版本上可用。框架渲染期会读取此信息，
 * 若当前数据库不兼容则按 {@link cn.vonce.sql.enumerate.DialectMode#STRICT} 抛
 * {@link cn.vonce.sql.exception.UnsupportedDialectException}。</p>
 *
 * <p>用 {@link Builder} 构造：</p>
 *
 * <pre>
 *   DialectSupport ds = DialectSupport.builder()
 *           .support(DbType.MySQL, DbVersion.from(5, 0))
 *           .support(DbType.SQLite, DbVersion.from(3, 0))
 *           .unsupport(DbType.Oracle)
 *           .unsupport(DbType.SQLServer)
 *           .build();
 * </pre>
 *
 * <p>支持的判断规则：</p>
 * <ul>
 *   <li>{@link #supports(DbType, DbVersion)}：当且仅当当前方言在 {@code supported} 列表里，且版本不低于该方言的最小版本。</li>
 *   <li>{@link #isUnsupported(DbType)}：{@code unsupported} 列表里的方言，直接禁用。</li>
 *   <li>不声明任何支持的方言（如 build 未调用任何 {@code support/unsupport}）= <b>通用</b>，所有方言都通过校验。</li>
 * </ul>
 *
 * @author Jovi
 */
public final class DialectSupport implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 空支持：所有方言通过校验（标识 "通用 SQL 函数"）。 */
    public static final DialectSupport UNIVERSAL = new DialectSupport(Collections.emptyMap(), Collections.emptySet());

    private final Map<DbType, DbVersion> supported;   // 支持 + 最小版本
    private final Set<DbType> unsupported;             // 显式不支持

    private DialectSupport(Map<DbType, DbVersion> supported, Set<DbType> unsupported) {
        this.supported = Collections.unmodifiableMap(new HashMap<>(supported));
        this.unsupported = Collections.unmodifiableSet(new LinkedHashSet<>(unsupported));
    }

    /**
     * 所有"显式支持（含版本）"的方言表。返回只读视图。
     */
    public Map<DbType, DbVersion> getSupported() {
        return supported;
    }

    /**
     * 所有"显式不支持"的方言。返回只读视图。
     */
    public Set<DbType> getUnsupported() {
        return unsupported;
    }

    /**
     * 是否声明了任何限制（无声明 = 通用）。
     */
    public boolean isUniversal() {
        return supported.isEmpty() && unsupported.isEmpty();
    }

    /**
     * 指定方言是否被支持（含版本检查）。
     *
     * @param dbType  数据库类型
     * @param version 当前数据库版本（可为 null；为 null 时只要 supported 里有该方言即可）
     */
    public boolean supports(DbType dbType, DbVersion version) {
        if (dbType == null) {
            return false;
        }
        if (unsupported.contains(dbType)) {
            return false;
        }
        if (supported.isEmpty()) {
            // 未声明 supported 列表（仅 unsupported）→ 除显式禁用外全支持
            return true;
        }
        if (!supported.containsKey(dbType)) {
            // 用户声明了 supported 列表但未列入此方言 → 视为不支持（防止用户误判）
            return false;
        }
        DbVersion minVersion = supported.get(dbType);
        if (minVersion == null || version == null) {
            // 在列表内，无最低版本限制；或调用方未提供版本 → 放行
            return true;
        }
        return version.atLeast(minVersion);
    }

    /**
     * 显式禁用列表
     */
    public boolean isUnsupported(DbType dbType) {
        return dbType != null && unsupported.contains(dbType);
    }

    /**
     * 给指定方言取最小要求版本（无声明返回 null）。
     */
    public DbVersion minVersion(DbType dbType) {
        return dbType == null ? null : supported.get(dbType);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link DialectSupport} 不可变建造器。
     */
    public static final class Builder {
        private final Map<DbType, DbVersion> supported = new HashMap<>();
        private final Set<DbType> unsupported = new LinkedHashSet<>();

        private Builder() {
        }

        /**
         * 声明某方言支持（含最小版本）。
         */
        public Builder support(DbType dbType, DbVersion since) {
            if (dbType == null) {
                throw new IllegalArgumentException("dbType must not be null");
            }
            supported.put(dbType, since);   // since 可为 null（仅表示"该方言可用，不限版本"）
            return this;
        }

        /**
         * 便捷重载：直接传版本字符串。
         */
        public Builder support(DbType dbType, String since) {
            return support(dbType, since == null ? null : DbVersion.from(since));
        }

        /**
         * 便捷重载：不要求最低版本。
         */
        public Builder support(DbType dbType) {
            return support(dbType, (DbVersion) null);
        }

        /**
         * 显式声明某方言不支持（优先级最高 — 即便其它声明支持也以 unsupport 为准）。
         */
        public Builder unsupport(DbType dbType) {
            if (dbType == null) {
                throw new IllegalArgumentException("dbType must not be null");
            }
            unsupported.add(dbType);
            return this;
        }

        /**
         * 一次性声明多个不支持的方言。
         */
        public Builder unsupport(DbType... dbTypes) {
            if (dbTypes == null) {
                return this;
            }
            for (DbType dbType : dbTypes) {
                unsupport(dbType);
            }
            return this;
        }

        public DialectSupport build() {
            if (supported.isEmpty() && unsupported.isEmpty()) {
                return UNIVERSAL;
            }
            return new DialectSupport(supported, unsupported);
        }
    }
}
