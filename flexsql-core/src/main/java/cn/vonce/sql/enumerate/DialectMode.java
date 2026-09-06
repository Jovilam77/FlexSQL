package cn.vonce.sql.enumerate;

/**
 * 方言不兼容时的处理模式。
 *
 * @author Jovi
 */
public enum DialectMode {

    /** 严格模式（默认）：渲染期发现数据库方言不匹配即抛 {@link cn.vonce.sql.exception.UnsupportedDialectException}。 */
    STRICT,

    /** 警告模式：渲染期仍输出 SQL，但通过日志告知用户方言不匹配。可能数据库层面炸 SQL syntax error。 */
    WARN,

    /** 关闭模式：不校验方言，由用户自负其责。 */
    OFF
}
