package cn.vonce.sql.exception;

import cn.vonce.sql.define.DbVersion;
import cn.vonce.sql.enumerate.DbType;

import java.util.Map;

/**
 * 方言不兼容异常。
 * <p>当某个 {@link cn.vonce.sql.define.SqlFun} 函数标注的方言元数据与当前数据库方言不匹配时抛出。</p>
 *
 * @author Jovi
 */
public class UnsupportedDialectException extends SqlBeanException {

    private static final long serialVersionUID = 1L;

    private final String functionName;
    private final DbType currentDbType;
    private final DbVersion currentVersion;
    private final Map<DbType, DbVersion> supported;

    public UnsupportedDialectException(String functionName,
                                       DbType currentDbType,
                                       DbVersion currentVersion,
                                       Map<DbType, DbVersion> supported,
                                       java.util.Set<DbType> unsupported) {
        super(buildMessage(functionName, currentDbType, currentVersion, supported, unsupported));
        this.functionName = functionName;
        this.currentDbType = currentDbType;
        this.currentVersion = currentVersion;
        this.supported = supported;
    }

    public String getFunctionName() {
        return functionName;
    }

    public DbType getCurrentDbType() {
        return currentDbType;
    }

    public DbVersion getCurrentVersion() {
        return currentVersion;
    }

    public Map<DbType, DbVersion> getSupported() {
        return supported;
    }

    private static String buildMessage(String functionName,
                                       DbType currentDbType,
                                       DbVersion currentVersion,
                                       Map<DbType, DbVersion> supported,
                                       java.util.Set<DbType> unsupported) {
        StringBuilder sb = new StringBuilder();
        sb.append("SqlFun.").append(functionName)
                .append(" 在当前方言 ").append(currentDbType);
        if (currentVersion != null) {
            sb.append(" (v").append(currentVersion).append(")");
        }
        sb.append(" 中不被支持。");
        if (supported != null && !supported.isEmpty()) {
            sb.append("\n支持的方言（最小版本）：");
            int i = 0;
            for (Map.Entry<DbType, DbVersion> e : supported.entrySet()) {
                if (i++ > 0) sb.append("、");
                sb.append(e.getKey());
                if (e.getValue() != null) {
                    sb.append(" >= ").append(e.getValue());
                }
            }
        }
        if (unsupported != null && !unsupported.isEmpty()) {
            sb.append("\n已被显式禁用的方言：").append(unsupported);
        }
        sb.append("\n建议：换用其它方言的等价函数，或使用 RawValue 自写方言特定 SQL。");
        return sb.toString();
    }
}
