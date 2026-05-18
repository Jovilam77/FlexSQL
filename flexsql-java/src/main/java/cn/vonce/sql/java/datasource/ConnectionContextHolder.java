package cn.vonce.sql.java.datasource;

import cn.vonce.sql.uitls.StringUtil;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 数据连接持有者
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2022/12/15 14:19
 */
public class ConnectionContextHolder {

    private static final Logger logger = Logger.getLogger(ConnectionContextHolder.class.getName());

    private static final ThreadLocal<HashMap<String, ConnectionProxy>> contextHolder = ThreadLocal.withInitial(HashMap::new);

    /**
     * 设置当前数据源代理
     *
     * @param ds
     * @param connectionProxy
     */
    public static void setConnection(String ds, ConnectionProxy connectionProxy) {
        HashMap<String, ConnectionProxy> map = contextHolder.get();
        if (map != null) {
            try {
                connectionProxy.setAutoCommit(false);
            } catch (SQLException e) {
                logger.warning("Failed to set auto commit to false: " + e.getMessage());
            }
            map.put(ds, connectionProxy);
        }
    }

    /**
     * 获取当前数据源代理
     *
     * @return
     */
    public static ConnectionProxy getConnection(String ds) {
        return contextHolder.get().get(ds);
    }

    /**
     * 提交或回滚
     *
     * @param isOk
     */
    public static void commitOrRollback(boolean isOk) {
        String ds = DataSourceContextHolder.getDataSource();
        if (StringUtil.isBlank(ds)) {
            ds = "default";
        }
        commitOrRollback(ds, isOk);
    }

    /**
     * 提交或回滚
     *
     * @param ds
     * @param isOk
     */
    public static void commitOrRollback(String ds, boolean isOk) {
        try {
            HashMap<String, ConnectionProxy> map = contextHolder.get();
            if (map != null) {
                ConnectionProxy connectionProxy = map.get(ds);
                if (connectionProxy != null) {
                    if (isOk) {
                        connectionProxy.commit();
                    } else {
                        connectionProxy.rollback();
                    }
                    connectionProxy.close();
                    map.remove(ds);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to commit or rollback: " + e.getMessage());
        }
    }

    /**
     * 设置只读
     *
     * @param readOnly
     */
    public static void setReadOnly(boolean readOnly) {
        for (Map.Entry<String, ConnectionProxy> entry : contextHolder.get().entrySet()) {
            setReadOnly(entry.getValue(), readOnly);
        }
    }

    /**
     * 设置只读
     *
     * @param ds
     * @param readOnly
     */
    public static void setReadOnly(String ds, boolean readOnly) {
        ConnectionProxy connectionProxy = contextHolder.get().get(ds);
        if (connectionProxy != null) {
            setReadOnly(connectionProxy, readOnly);
        }
    }

    /**
     * 提交或回滚
     *
     * @param connectionProxy
     * @param readOnly
     */
    public static void setReadOnly(ConnectionProxy connectionProxy, boolean readOnly) {
        try {
            connectionProxy.setReadOnly(readOnly);
        } catch (SQLException e) {
            logger.warning("Failed to set read only: " + e.getMessage());
        }
    }

    /**
     * 清除所有连接
     * 防止 ThreadLocal 内存泄漏
     */
    public static void clearAll() {
        contextHolder.remove();
    }

}
