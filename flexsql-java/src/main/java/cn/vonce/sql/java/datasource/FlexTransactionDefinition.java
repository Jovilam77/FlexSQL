package cn.vonce.sql.java.datasource;

/**
 * FlexSQL 事务定义接口
 * 定义事务的传播行为、隔离级别、超时时间等属性
 * 参考 Spring 的 TransactionDefinition 设计，但完全独立于 Spring 框架
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
public interface FlexTransactionDefinition {

    /**
     * 事务传播行为：如果当前没有事务，则创建一个新事务；如果当前有事务，则加入当前事务
     */
    int PROPAGATION_REQUIRED = 0;

    /**
     * 事务传播行为：如果当前没有事务，则以非事务方式执行；如果当前有事务，则加入当前事务
     */
    int PROPAGATION_SUPPORTS = 1;

    /**
     * 事务传播行为：如果当前没有事务，则抛出异常；如果当前有事务，则加入当前事务
     */
    int PROPAGATION_MANDATORY = 2;

    /**
     * 事务传播行为：总是创建一个新事务；如果当前有事务，则挂起当前事务
     */
    int PROPAGATION_REQUIRES_NEW = 3;

    /**
     * 事务传播行为：以非事务方式执行；如果当前有事务，则挂起当前事务
     */
    int PROPAGATION_NOT_SUPPORTED = 4;

    /**
     * 事务传播行为：以非事务方式执行；如果当前有事务，则抛出异常
     */
    int PROPAGATION_NEVER = 5;

    /**
     * 事务传播行为：如果当前没有事务，则创建一个新事务；如果当前有事务，则嵌套执行（保存点）
     */
    int PROPAGATION_NESTED = 6;

    /**
     * 事务隔离级别：使用数据库默认隔离级别
     */
    int ISOLATION_DEFAULT = -1;

    /**
     * 事务隔离级别：读未提交
     */
    int ISOLATION_READ_UNCOMMITTED = java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;

    /**
     * 事务隔离级别：读已提交
     */
    int ISOLATION_READ_COMMITTED = java.sql.Connection.TRANSACTION_READ_COMMITTED;

    /**
     * 事务隔离级别：可重复读
     */
    int ISOLATION_REPEATABLE_READ = java.sql.Connection.TRANSACTION_REPEATABLE_READ;

    /**
     * 事务隔离级别：串行化
     */
    int ISOLATION_SERIALIZABLE = java.sql.Connection.TRANSACTION_SERIALIZABLE;

    /**
     * 事务超时时间：默认（不超时）
     */
    int TIMEOUT_DEFAULT = -1;

    /**
     * 获取事务传播行为
     *
     * @return 传播行为
     */
    int getPropagationBehavior();

    /**
     * 获取事务隔离级别
     *
     * @return 隔离级别
     */
    int getIsolationLevel();

    /**
     * 获取事务超时时间（秒）
     *
     * @return 超时时间
     */
    int getTimeout();

    /**
     * 是否只读事务
     *
     * @return 是否只读
     */
    boolean isReadOnly();

    /**
     * 获取事务名称
     *
     * @return 事务名称
     */
    String getName();

    /**
     * 默认事务定义实现
     */
    class DefaultTransactionDefinition implements FlexTransactionDefinition {

        private int propagationBehavior = PROPAGATION_REQUIRED;
        private int isolationLevel = ISOLATION_DEFAULT;
        private int timeout = TIMEOUT_DEFAULT;
        private boolean readOnly = false;
        private String name;

        public DefaultTransactionDefinition() {
        }

        public DefaultTransactionDefinition(int propagationBehavior) {
            this.propagationBehavior = propagationBehavior;
        }

        public DefaultTransactionDefinition(int propagationBehavior, int isolationLevel) {
            this.propagationBehavior = propagationBehavior;
            this.isolationLevel = isolationLevel;
        }

        @Override
        public int getPropagationBehavior() {
            return propagationBehavior;
        }

        public void setPropagationBehavior(int propagationBehavior) {
            this.propagationBehavior = propagationBehavior;
        }

        @Override
        public int getIsolationLevel() {
            return isolationLevel;
        }

        public void setIsolationLevel(int isolationLevel) {
            this.isolationLevel = isolationLevel;
        }

        @Override
        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        @Override
        public boolean isReadOnly() {
            return readOnly;
        }

        public void setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
        }

        @Override
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        /**
         * 创建构建器
         */
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final DefaultTransactionDefinition definition = new DefaultTransactionDefinition();

            public Builder propagation(int propagation) {
                definition.propagationBehavior = propagation;
                return this;
            }

            public Builder isolation(int isolation) {
                definition.isolationLevel = isolation;
                return this;
            }

            public Builder timeout(int timeout) {
                definition.timeout = timeout;
                return this;
            }

            public Builder readOnly(boolean readOnly) {
                definition.readOnly = readOnly;
                return this;
            }

            public Builder name(String name) {
                definition.name = name;
                return this;
            }

            public DefaultTransactionDefinition build() {
                return definition;
            }
        }
    }

}