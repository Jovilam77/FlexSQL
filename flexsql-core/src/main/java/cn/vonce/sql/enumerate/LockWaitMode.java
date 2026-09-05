package cn.vonce.sql.enumerate;

/**
 * 行锁等待模式
 * <p>
 * 配合 {@link LockType} 使用，决定获取不到锁时的行为；
 * 对不支持该模式的低版本数据库，生成器会忽略对应子句并输出告警。
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2026年9月5日
 */
public enum LockWaitMode {

    /**
     * 默认：一直等待直到获取到锁
     */
    WAIT,

    /**
     * NOWAIT：不等待，若锁不可立即获取则立刻报错返回。
     * MySQL 8.0+ / PostgreSQL 9.5+ / Oracle（全版本）/ SQL Server 支持。
     */
    NOWAIT,

    /**
     * SKIP LOCKED：跳过已被其它事务锁定的行，避免并发等待。
     * MySQL 8.0+ / MariaDB 10.3+ / PostgreSQL 9.5+ / Oracle 11g+ 支持。
     */
    SKIP_LOCKED

}
