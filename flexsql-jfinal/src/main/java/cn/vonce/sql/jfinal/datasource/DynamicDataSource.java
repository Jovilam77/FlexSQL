package cn.vonce.sql.jfinal.datasource;

import cn.vonce.sql.java.datasource.ConnectionContextHolder;
import cn.vonce.sql.java.datasource.ConnectionProxy;
import cn.vonce.sql.java.datasource.DataSourceContextHolder;
import cn.vonce.sql.java.datasource.TransactionalContextHolder;
import cn.vonce.sql.uitls.StringUtil;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 动态数据源
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 21:22
 */
public class DynamicDataSource implements DataSource {

    private DataSource defaultTargetDataSource;
    private Map<String, DataSource> targetDataSources = new HashMap<>();

    public void setDefaultTargetDataSource(DataSource defaultTargetDataSource) {
        this.defaultTargetDataSource = defaultTargetDataSource;
    }

    public void setTargetDataSources(Map<String, DataSource> targetDataSources) {
        this.targetDataSources = targetDataSources;
    }

    protected DataSource determineCurrentTarget() {
        String dataSource = DataSourceContextHolder.getDataSource();
        if (StringUtil.isNotBlank(dataSource) && targetDataSources.containsKey(dataSource)) {
            return targetDataSources.get(dataSource);
        }
        return defaultTargetDataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        String xid = TransactionalContextHolder.getXid();
        String ds = DataSourceContextHolder.getDataSource();
        if (StringUtil.isEmpty(xid)) {
            return determineCurrentTarget().getConnection();
        }
        return getConnectionProxy(ds, null, null);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        String xid = TransactionalContextHolder.getXid();
        String ds = DataSourceContextHolder.getDataSource();
        if (StringUtil.isEmpty(xid)) {
            return determineCurrentTarget().getConnection(username, password);
        }
        return getConnectionProxy(ds, username, password);
    }

    private ConnectionProxy getConnectionProxy(String ds, String username, String password) throws SQLException {
        if (StringUtil.isBlank(ds)) {
            ds = "default";
        }
        ConnectionProxy connectionProxy = ConnectionContextHolder.getConnection(ds);
        if (connectionProxy == null) {
            Connection connection;
            if (StringUtil.isBlank(username) && StringUtil.isBlank(password)) {
                connection = determineCurrentTarget().getConnection();
            } else {
                connection = determineCurrentTarget().getConnection(username, password);
            }
            connectionProxy = new ConnectionProxy(ds, connection);
            ConnectionContextHolder.setConnection(ds, connectionProxy);
        }
        return connectionProxy;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return determineCurrentTarget().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return determineCurrentTarget().isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return determineCurrentTarget().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        determineCurrentTarget().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        determineCurrentTarget().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return determineCurrentTarget().getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

}