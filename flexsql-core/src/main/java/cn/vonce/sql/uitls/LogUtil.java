package cn.vonce.sql.uitls;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 日志工具类
 * 使用 java.util.logging.Logger 作为日志框架，保持轻量级无依赖
 */
public class LogUtil {

    private static final String DEFAULT_LOGGER_NAME = "FlexSQL";

    private LogUtil() {
    }

    /**
     * 获取日志记录器
     *
     * @param clazz 类对象
     * @return Logger
     */
    public static Logger getLogger(Class<?> clazz) {
        return Logger.getLogger(clazz.getName());
    }

    /**
     * 获取默认日志记录器
     *
     * @return Logger
     */
    public static Logger getLogger() {
        return Logger.getLogger(DEFAULT_LOGGER_NAME);
    }

    /**
     * 记录 SEVERE 级别日志
     *
     * @param clazz  类对象
     * @param message 消息
     */
    public static void severe(Class<?> clazz, String message) {
        getLogger(clazz).log(Level.SEVERE, message);
    }

    /**
     * 记录 SEVERE 级别日志（带异常）
     *
     * @param clazz 类对象
     * @param message 消息
     * @param throwable 异常
     */
    public static void severe(Class<?> clazz, String message, Throwable throwable) {
        getLogger(clazz).log(Level.SEVERE, message, throwable);
    }

    /**
     * 记录 WARNING 级别日志
     *
     * @param clazz  类对象
     * @param message 消息
     */
    public static void warning(Class<?> clazz, String message) {
        getLogger(clazz).log(Level.WARNING, message);
    }

    /**
     * 记录 WARNING 级别日志（带异常）
     *
     * @param clazz 类对象
     * @param message 消息
     * @param throwable 异常
     */
    public static void warning(Class<?> clazz, String message, Throwable throwable) {
        getLogger(clazz).log(Level.WARNING, message, throwable);
    }

    /**
     * 记录 INFO 级别日志
     *
     * @param clazz  类对象
     * @param message 消息
     */
    public static void info(Class<?> clazz, String message) {
        getLogger(clazz).log(Level.INFO, message);
    }

    /**
     * 记录 INFO 级别日志（带异常）
     *
     * @param clazz 类对象
     * @param message 消息
     * @param throwable 异常
     */
    public static void info(Class<?> clazz, String message, Throwable throwable) {
        getLogger(clazz).log(Level.INFO, message, throwable);
    }

    /**
     * 记录 CONFIG 级别日志
     *
     * @param clazz  类对象
     * @param message 消息
     */
    public static void config(Class<?> clazz, String message) {
        getLogger(clazz).log(Level.CONFIG, message);
    }

    /**
     * 记录 FINE 级别日志
     *
     * @param clazz  类对象
     * @param message 消息
     */
    public static void fine(Class<?> clazz, String message) {
        getLogger(clazz).log(Level.FINE, message);
    }

    /**
     * 记录 FINER 级别日志
     *
     * @param clazz  类对象
     * @param message 消息
     */
    public static void finer(Class<?> clazz, String message) {
        getLogger(clazz).log(Level.FINER, message);
    }

    /**
     * 记录 FINEST 级别日志
     *
     * @param clazz  类对象
     * @param message 消息
     */
    public static void finest(Class<?> clazz, String message) {
        getLogger(clazz).log(Level.FINEST, message);
    }

    /**
     * 记录异常日志（WARNING级别）
     *
     * @param clazz     类对象
     * @param throwable 异常
     */
    public static void logException(Class<?> clazz, Throwable throwable) {
        getLogger(clazz).log(Level.WARNING, throwable.getMessage(), throwable);
    }

    /**
     * 记录异常日志（指定级别）
     *
     * @param clazz     类对象
     * @param level     日志级别
     * @param throwable 异常
     */
    public static void logException(Class<?> clazz, Level level, Throwable throwable) {
        getLogger(clazz).log(level, throwable.getMessage(), throwable);
    }
}
