package cn.vonce.sql.java.datasource;

import cn.vonce.sql.exception.SqlBeanException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * FlexSQL 事务管理器
 * 负责事务的开始、提交、回滚、保存点管理等操作
 * 参考 Spring 的 PlatformTransactionManager 设计，但完全独立于 Spring 框架
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
public class FlexTransactionManager {

    private static final Logger logger = Logger.getLogger(FlexTransactionManager.class.getName());

    /**
     * 单例实例
     */
    private static volatile FlexTransactionManager instance;

    /**
     * 当前线程的事务状态
     */
    private final ThreadLocal<FlexTransactionStatus> transactionStatus = new ThreadLocal<>();

    /**
     * 数据源管理器
     */
    private final FlexDataSourceManager dataSourceManager;

    /**
     * 私有构造函数
     */
    private FlexTransactionManager() {
        this.dataSourceManager = FlexDataSourceManager.getInstance();
    }

    /**
     * 获取单例实例
     *
     * @return FlexTransactionManager 实例
     */
    public static FlexTransactionManager getInstance() {
        if (instance == null) {
            synchronized (FlexTransactionManager.class) {
                if (instance == null) {
                    instance = new FlexTransactionManager();
                }
            }
        }
        return instance;
    }

    /**
     * 开启事务
     *
     * @param definition 事务定义
     * @return 事务状态
     */
    public FlexTransactionStatus beginTransaction(FlexTransactionDefinition definition) {
        // 获取当前线程的事务状态
        FlexTransactionStatus existingStatus = transactionStatus.get();

        int propagation = definition.getPropagationBehavior();

        switch (propagation) {
            case FlexTransactionDefinition.PROPAGATION_REQUIRED:
                if (existingStatus != null && !existingStatus.isCompleted()) {
                    // 当前已有事务，加入当前事务
                    return existingStatus;
                }
                // 创建新事务
                return createTransaction(definition);

            case FlexTransactionDefinition.PROPAGATION_SUPPORTS:
                if (existingStatus != null && !existingStatus.isCompleted()) {
                    return existingStatus;
                }
                // 没有事务，以非事务方式执行（返回null表示无事务）
                return null;

            case FlexTransactionDefinition.PROPAGATION_MANDATORY:
                if (existingStatus != null && !existingStatus.isCompleted()) {
                    return existingStatus;
                }
                throw new SqlBeanException("No existing transaction found for propagation 'mandatory'");

            case FlexTransactionDefinition.PROPAGATION_REQUIRES_NEW:
                // 挂起当前事务，创建新事务
                if (existingStatus != null) {
                    // 保存当前事务状态（挂起）
                    // 简单实现：直接创建新事务，原事务在新事务结束后恢复
                }
                return createTransaction(definition);

            case FlexTransactionDefinition.PROPAGATION_NOT_SUPPORTED:
                // 挂起当前事务，以非事务方式执行
                if (existingStatus != null) {
                    // 挂起当前事务
                }
                return null;

            case FlexTransactionDefinition.PROPAGATION_NEVER:
                if (existingStatus != null && !existingStatus.isCompleted()) {
                    throw new SqlBeanException("Existing transaction found for propagation 'never'");
                }
                return null;

            case FlexTransactionDefinition.PROPAGATION_NESTED:
                if (existingStatus != null && !existingStatus.isCompleted()) {
                    // 创建嵌套事务（使用保存点）
                    try {
                        existingStatus.createSavepoint();
                    } catch (SQLException e) {
                        throw new SqlBeanException("Failed to create savepoint for nested transaction", e);
                    }
                    return existingStatus;
                }
                // 没有事务，创建新事务
                return createTransaction(definition);

            default:
                throw new SqlBeanException("Unknown propagation behavior: " + propagation);
        }
    }

    /**
     * 创建新事务
     *
     * @param definition 事务定义
     * @return 事务状态
     */
    private FlexTransactionStatus createTransaction(FlexTransactionDefinition definition) {
        try {
            // 获取连接
            DataSource dataSource = dataSourceManager.getCurrentDataSource();
            Connection connection = dataSource.getConnection();

            // 获取数据源名称
            String ds = DataSourceContextHolder.getDataSource();
            if (ds == null) {
                ds = "default";
            }

            // 设置隔离级别
            int isolationLevel = definition.getIsolationLevel();
            if (isolationLevel != FlexTransactionDefinition.ISOLATION_DEFAULT) {
                connection.setTransactionIsolation(isolationLevel);
            }

            // 设置只读
            if (definition.isReadOnly()) {
                connection.setReadOnly(true);
            }

            // 关闭自动提交
            connection.setAutoCommit(false);

            // 将连接绑定到当前线程（使用 ConnectionProxy 包装）
            ConnectionContextHolder.setConnection(ds, new ConnectionProxy(ds, connection));

            // 创建事务状态
            FlexTransactionStatus status = new FlexTransactionStatus(definition, connection, true);

            // 将事务状态绑定到当前线程
            transactionStatus.set(status);

            logger.info("FlexSQL: Transaction started - " + (definition.getName() != null ? definition.getName() : "anonymous"));

            return status;
        } catch (SQLException e) {
            throw new SqlBeanException("Failed to start transaction", e);
        }
    }

    /**
     * 提交事务
     *
     * @param status 事务状态
     */
    public void commit(FlexTransactionStatus status) {
        if (status == null) {
            return;
        }
        try {
            status.commit();
            logger.info("FlexSQL: Transaction committed");
        } finally {
            // 只有新事务才释放资源
            if (status.isNewTransaction()) {
                status.release();
                transactionStatus.remove();
                ConnectionContextHolder.clearAll();
            }
        }
    }

    /**
     * 回滚事务
     *
     * @param status 事务状态
     */
    public void rollback(FlexTransactionStatus status) {
        if (status == null) {
            return;
        }
        try {
            status.rollback();
            logger.info("FlexSQL: Transaction rolled back");
        } finally {
            // 只有新事务才释放资源
            if (status.isNewTransaction()) {
                status.release();
                transactionStatus.remove();
                ConnectionContextHolder.clearAll();
            }
        }
    }

    /**
     * 回滚到保存点
     *
     * @param status        事务状态
     * @param savepointName 保存点名称
     */
    public void rollbackToSavepoint(FlexTransactionStatus status, String savepointName) {
        if (status == null) {
            return;
        }
        try {
            status.rollbackToSavepoint(savepointName);
            logger.info("FlexSQL: Transaction rolled back to savepoint - " + savepointName);
        } catch (SQLException e) {
            throw new SqlBeanException("Failed to rollback to savepoint", e);
        }
    }

    /**
     * 设置回滚状态
     *
     * @param status 事务状态
     */
    public void setRollbackOnly(FlexTransactionStatus status) {
        if (status != null) {
            status.setRollbackOnly();
        }
    }

    /**
     * 获取当前事务状态
     *
     * @return 当前事务状态
     */
    public FlexTransactionStatus getCurrentTransactionStatus() {
        return transactionStatus.get();
    }

    /**
     * 是否存在活动事务
     *
     * @return 是否存在活动事务
     */
    public boolean isTransactionActive() {
        FlexTransactionStatus status = transactionStatus.get();
        return status != null && !status.isCompleted();
    }

    /**
     * 在事务中执行回调
     *
     * @param definition 事务定义
     * @param action     回调函数
     * @param <T>        返回类型
     * @return 回调结果
     */
    public <T> T execute(FlexTransactionDefinition definition, FlexTransactionCallback<T> action) {
        FlexTransactionStatus status = beginTransaction(definition);
        try {
            T result = action.doInTransaction(status);
            commit(status);
            return result;
        } catch (Exception e) {
            rollback(status);
            throw new RuntimeException("Transaction failed", e);
        }
    }

    /**
     * 在默认事务中执行回调
     *
     * @param action 回调函数
     * @param <T>    返回类型
     * @return 回调结果
     */
    public <T> T execute(FlexTransactionCallback<T> action) {
        FlexTransactionDefinition definition = new FlexTransactionDefinition.DefaultTransactionDefinition();
        return execute(definition, action);
    }

    /**
     * 事务回调接口
     */
    @FunctionalInterface
    public interface FlexTransactionCallback<T> {
        T doInTransaction(FlexTransactionStatus status) throws Exception;
    }

}