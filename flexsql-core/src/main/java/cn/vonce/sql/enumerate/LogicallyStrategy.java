package cn.vonce.sql.enumerate;

/**
 * 逻辑删除策略
 * 
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2026/5/18
 */
public enum LogicallyStrategy {
    
    /**
     * 默认过滤已删除的数据（默认行为）
     * 查询、更新、删除操作时会自动添加 `deleted=0` 的条件
     */
    FILTER,
    
    /**
     * 不过滤已删除的数据
     * 查询、更新、删除操作时不会自动添加逻辑删除条件，需要手动处理
     */
    NOT_FILTER
}