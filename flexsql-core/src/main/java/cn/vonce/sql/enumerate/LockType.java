package cn.vonce.sql.enumerate;

/**
 * 行锁（悲观锁）类型
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2026年9月5日
 */
public enum LockType {

    /**
     * 不加锁
     */
    NONE,

    /**
     * FOR UPDATE
     * 悲观行锁，对已读取的行加排他锁，直到事务结束。
     * 主流关系型数据库（MySQL、MariaDB、PostgreSQL、Oracle、SQL Server 等）均支持。
     */
    FOR_UPDATE,

    /**
     * FOR UPDATE SKIP LOCKED
     *      * 在 FOR UPDATE 基础上跳过已被其它事务锁定的行，避免并发等待。
     *      * 仅 MySQL 8.0+ / MariaDB 10.3+ / PostgreSQL 9.5+ 支持。
     *      * 低版本数据库会忽略该子句并给出告警。
     */
    FOR_UPDATE_SKIP_LOCKED

}
