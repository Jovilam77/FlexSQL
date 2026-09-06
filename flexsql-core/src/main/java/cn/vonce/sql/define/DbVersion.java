package cn.vonce.sql.define;

import java.io.Serializable;

/**
 * 数据库版本（不可变 POJO）。
 * <p>用于方言支持元数据 —— 例如 MySQL 8.0+ 才支持 {@code GREATEST}/{@code LEAST} 标准语法。</p>
 *
 * <pre>
 *   DbVersion v1 = DbVersion.from(8, 0);               // 8.0
 *   DbVersion v2 = DbVersion.from("8.0.1");           // 8.0.1
 *   v1.compareTo(v2) // -1
 * </pre>
 *
 * @author Jovi
 */
public final class DbVersion implements Comparable<DbVersion>, Serializable {

    private static final long serialVersionUID = 1L;

    private final int major;
    private final int minor;

    private DbVersion(int major, int minor) {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("major/minor must be non-negative: " + major + "." + minor);
        }
        this.major = major;
        this.minor = minor;
    }

    /**
     * 主版本
     */
    public int getMajor() {
        return major;
    }

    /**
     * 次版本
     */
    public int getMinor() {
        return minor;
    }

    /**
     * 主版本 + 次版本构造。
     */
    public static DbVersion from(int major, int minor) {
        return new DbVersion(major, minor);
    }

    /**
     * 字符串解析：兼容 "8.0"、"8.0.1"、"8" 三种写法（patch 段忽略）。
     *
     * @param text 版本字符串，如 {@code "8.0.1"}
     * @return 解析结果
     * @throws IllegalArgumentException 解析失败
     */
    public static DbVersion from(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("version text must not be empty");
        }
        int dot = text.indexOf('.');
        int major;
        int minor = 0;
        if (dot < 0) {
            try {
                major = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid version text: " + text, e);
            }
        } else {
            try {
                major = Integer.parseInt(text.substring(0, dot));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid major in version: " + text, e);
            }
            String rest = text.substring(dot + 1);
            int secondDot = rest.indexOf('.');
            String minorText = secondDot < 0 ? rest : rest.substring(0, secondDot);
            try {
                minor = Integer.parseInt(minorText);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid minor in version: " + text, e);
            }
        }
        return new DbVersion(major, minor);
    }

    @Override
    public int compareTo(DbVersion other) {
        int c = Integer.compare(this.major, other.major);
        if (c != 0) {
            return c;
        }
        return Integer.compare(this.minor, other.minor);
    }

    /**
     * 当前版本是否大于等于目标版本（忽略 patch 段）。
     */
    public boolean atLeast(DbVersion target) {
        return this.compareTo(target) >= 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DbVersion)) return false;
        DbVersion other = (DbVersion) obj;
        return major == other.major && minor == other.minor;
    }

    @Override
    public int hashCode() {
        return 31 * major + minor;
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
