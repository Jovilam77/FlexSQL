package cn.vonce.sql.java.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FlexSQL 事务状态类
 * 表示当前事务的状态信息，包括连接、保存点、回滚状态等
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
public class FlexTransactionStatus {

    /**
     * 事务定义
     */
    private final FlexTransactionDefinition definition;

    /**
     * 数据库连接
     */
    private final Connection connection;

    /**
     * 是否是新事务
     */
    private final boolean newTransaction;

    /**
     * 是否已回滚
     */
    private boolean rollbackOnly = false;

    /**
     * 是否已完成（提交或回滚）
     */
    private boolean completed = false;

    /**
     * 保存点列表（用于批量释放）
     */
    private final List<Savepoint> savepoints = new ArrayList<>();

    /**
     * 保存点映射（名称 -> Savepoint 对象）
     */
    private final Map<String, Savepoint> savepointMap = new HashMap<>();

    /**
     * 当前保存点名称计数器
     */
    private int savepointCounter = 0;

    /**
     * 构造函数
     *
     * @param definition    事务定义
     * @param connection    数据库连接
     * @param newTransaction 是否是新事务
     */
    public FlexTransactionStatus(FlexTransactionDefinition definition, Connection connection, boolean newTransaction) {
        this.definition = definition;
        this.connection = connection;
        this.newTransaction = newTransaction;
    }

    /**
     * 创建保存点
     *
     * @return 保存点名称
     * @throws SQLException SQL异常
     */
    public String createSavepoint() throws SQLException {
        String savepointName = "FLEXSQL_SAVEPOINT_" + (++savepointCounter);
        Savepoint savepoint = connection.setSavepoint(savepointName);
        savepoints.add(savepoint);
        savepointMap.put(savepointName, savepoint);
        return savepointName;
    }

    /**
     * 创建指定名称的保存点
     *
     * @param savepointName 保存点名称
     * @throws SQLException SQL异常
     */
    public void createSavepoint(String savepointName) throws SQLException {
        Savepoint savepoint = connection.setSavepoint(savepointName);
        savepoints.add(savepoint);
        savepointMap.put(savepointName, savepoint);
    }

    /**
     * 回滚到保存点
     *
     * @param savepointName 保存点名称
     * @throws SQLException SQL异常
     */
    public void rollbackToSavepoint(String savepointName) throws SQLException {
        Savepoint savepoint = savepointMap.get(savepointName);
        if (savepoint != null) {
            connection.rollback(savepoint);
        }
    }

    /**
     * 释放保存点
     *
     * @param savepointName 保存点名称
     * @throws SQLException SQL异常
     */
    public void releaseSavepoint(String savepointName) throws SQLException {
        Savepoint savepoint = savepointMap.remove(savepointName);
        if (savepoint != null) {
            connection.releaseSavepoint(savepoint);
            savepoints.remove(savepoint);
        }
    }

    /**
     * 回滚事务
     */
    public void rollback() {
        if (completed) {
            return;
        }
        try {
            connection.rollback();
            rollbackOnly = true;
            completed = true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to rollback transaction", e);
        }
    }

    /**
     * 提交事务
     */
    public void commit() {
        if (completed) {
            return;
        }
        if (rollbackOnly) {
            rollback();
            return;
        }
        try {
            connection.commit();
            completed = true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to commit transaction", e);
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        if (!completed) {
            // 如果事务未完成，回滚
            try {
                connection.rollback();
            } catch (SQLException e) {
                // 忽略回滚异常
            }
        }
        // 释放所有保存点
        for (Savepoint savepoint : savepoints) {
            try {
                connection.releaseSavepoint(savepoint);
            } catch (SQLException e) {
                // 忽略释放异常
            }
        }
        savepointMap.clear();
        savepoints.clear();
        // 如果是新事务，关闭连接
        if (newTransaction) {
            try {
                connection.close();
            } catch (SQLException e) {
                // 忽略关闭异常
            }
        }
    }

    /**
     * 设置回滚状态
     */
    public void setRollbackOnly() {
        this.rollbackOnly = true;
    }

    /**
     * 是否已回滚
     *
     * @return 是否已回滚
     */
    public boolean isRollbackOnly() {
        return rollbackOnly;
    }

    /**
     * 是否已完成
     *
     * @return 是否已完成
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * 是否是新事务
     *
     * @return 是否是新事务
     */
    public boolean isNewTransaction() {
        return newTransaction;
    }

    /**
     * 获取事务定义
     *
     * @return 事务定义
     */
    public FlexTransactionDefinition getDefinition() {
        return definition;
    }

    /**
     * 获取数据库连接
     *
     * @return 数据库连接
     */
    public Connection getConnection() {
        return connection;
    }

}
