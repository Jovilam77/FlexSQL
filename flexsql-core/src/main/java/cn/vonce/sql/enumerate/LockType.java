package cn.vonce.sql.enumerate;

/**
 * 行锁（悲观锁）类型
 * <p>
 * 仅表示「锁的类型」，具体的等待行为（NOWAIT / SKIP LOCKED）与锁定范围（OF 表列表）
 * 由 {@link LockWaitMode} 与 Select 的 lockOfTables 共同决定。
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
     * FOR SHARE
     * 共享行锁（读锁），允许其它事务读取被锁定的行，但阻止其加排他锁/修改。
     * 仅 MySQL 8.0+ / PostgreSQL 支持；低版本数据库会忽略该子句并给出告警。
     */
    FOR_SHARE

}
