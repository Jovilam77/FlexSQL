package cn.vonce.sql.exception;

/**
 * Sql语句助手 异常
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @version 1.0
 * @date 2018年4月15日下午5:16:51
 */
public class SqlBeanException extends RuntimeException {

    public SqlBeanException(String message) {
        super(message);
    }

    /**
     * 构造函数，支持原因异常
     *
     * @param message 异常消息
     * @param cause   原因异常
     */
    public SqlBeanException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数，仅支持原因异常
     *
     * @param cause 原因异常
     */
    public SqlBeanException(Throwable cause) {
        super(cause);
    }

    /**
     *
     */
    private static final long serialVersionUID = 1L;

}
