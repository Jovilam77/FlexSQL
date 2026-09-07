package cn.vonce.sql.java.datasource;

import cn.vonce.sql.mapper.ResultSetDelegate;
import cn.vonce.sql.mapper.SqlBeanMapper;
import cn.vonce.sql.uitls.StringUtil;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * FlexSQL JDBC 模板类
 * 封装 JDBC 操作，包括 PreparedStatement 创建、参数绑定、ResultSet 映射、资源清理等
 * 参考 Spring JdbcTemplate 的设计，但完全独立于 Spring 框架
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
public class FlexJdbcTemplate {

    private static final Logger logger = Logger.getLogger(FlexJdbcTemplate.class.getName());

    private final DataSource dataSource;
    private final SqlBeanMapper sqlBeanMapper;

    /**
     * 使用默认数据源创建模板
     */
    public FlexJdbcTemplate() {
        this(FlexDataSourceManager.getInstance().getDefaultDataSource());
    }

    /**
     * 使用指定数据源创建模板
     *
     * @param dataSource 数据源
     */
    public FlexJdbcTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
        this.sqlBeanMapper = new SqlBeanMapper();
    }

    // ==================== 查询操作 ====================

    /**
     * 查询单个对象
     *
     * @param sql        SQL 语句
     * @param returnType 返回类型
     * @param args       参数
     * @param <T>        返回类型泛型
     * @return 查询结果
     */
    public <T> T queryForObject(String sql, Class<T> returnType, Object... args) {
        List<T> list = queryForList(sql, returnType, args);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询列表
     *
     * @param sql        SQL 语句
     * @param returnType 返回类型
     * @param args       参数
     * @param <T>        返回类型泛型
     * @return 查询结果列表
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> queryForList(String sql, Class<T> returnType, Object... args) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(sql);
            setParameters(pstmt, args);

            if (logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine("FlexSQL SQL: " + sql);
            }

            rs = pstmt.executeQuery();

            List<String> columnNameList = sqlBeanMapper.getColumnNameList(new ResultSetDelegate<>(rs));
            List<T> list = new ArrayList<>();

            while (rs.next()) {
                Object result;
                if (Map.class.equals(returnType)) {
                    result = sqlBeanMapper.mapHandleResultSet(new ResultSetDelegate<>(rs));
                } else {
                    result = sqlBeanMapper.beanHandleResultSet(returnType, new ResultSetDelegate<>(rs), columnNameList);
                }
                list.add((T) result);
            }
            return list;
        } catch (SQLException e) {
            throw translateException(sql, args, e);
        } finally {
            close(rs);
            close(pstmt);
            closeConnection(conn);
        }
    }

    /**
     * 查询基本类型（单值）
     *
     * @param sql  SQL 语句
     * @param args 参数
     * @return 查询结果
     */
    public Object queryForScalar(String sql, Object... args) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(sql);
            setParameters(pstmt, args);

            rs = pstmt.executeQuery();
            if (rs.next()) {
                return sqlBeanMapper.baseHandleResultSet(new ResultSetDelegate<>(rs));
            }
            return null;
        } catch (SQLException e) {
            throw translateException(sql, args, e);
        } finally {
            close(rs);
            close(pstmt);
            closeConnection(conn);
        }
    }

    /**
     * 查询基本类型列表
     *
     * @param sql  SQL 语句
     * @param args 参数
     * @param <T>  返回类型泛型
     * @return 查询结果列表
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> queryForScalarList(String sql, Object... args) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(sql);
            setParameters(pstmt, args);

            rs = pstmt.executeQuery();
            List<T> list = new ArrayList<>();

            while (rs.next()) {
                Object value = sqlBeanMapper.baseHandleResultSet(new ResultSetDelegate<>(rs));
                list.add((T) value);
            }
            return list;
        } catch (SQLException e) {
            throw translateException(sql, args, e);
        } finally {
            close(rs);
            close(pstmt);
            closeConnection(conn);
        }
    }

    /**
     * 查询行数
     *
     * @param sql  SQL 语句
     * @param args 参数
     * @return 行数
     */
    public int queryForRowCount(String sql, Object... args) {
        Object result = queryForScalar(sql, args);
        return result != null ? ((Number) result).intValue() : 0;
    }

    /**
     * 查询单个 Map
     *
     * @param sql  SQL 语句
     * @param args 参数
     * @return 查询结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> queryForMap(String sql, Object... args) {
        List<Map<String, Object>> list = queryForMapList(sql, args);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询 Map 列表
     *
     * @param sql  SQL 语句
     * @param args 参数
     * @return 查询结果列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> queryForMapList(String sql, Object... args) {
        return (List<Map<String, Object>>) (List<?>) queryForList(sql, Map.class, args);
    }

    // ==================== 更新操作 ====================

    /**
     * 执行更新（INSERT/UPDATE/DELETE）
     *
     * @param sql  SQL 语句
     * @param args 参数
     * @return 影响的行数
     */
    public int update(String sql, Object... args) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(sql);
            setParameters(pstmt, args);

            if (logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine("FlexSQL SQL: " + sql);
            }

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            throw translateException(sql, args, e);
        } finally {
            close(pstmt);
            closeConnection(conn);
        }
    }

    /**
     * 执行插入并返回自增ID
     *
     * @param sql  SQL 语句
     * @param args 参数
     * @return 自增ID
     */
    public long insertAndGetKey(String sql, Object... args) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            setParameters(pstmt, args);

            if (logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine("FlexSQL SQL: " + sql);
            }

            pstmt.executeUpdate();

            rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            throw translateException(sql, args, e);
        } finally {
            close(rs);
            close(pstmt);
            closeConnection(conn);
        }
    }

    /**
     * 批量执行更新
     *
     * @param sql       SQL 语句
     * @param batchArgs 参数数组（每行一组参数）
     * @return 影响的行数数组
     */
    public int[] batchUpdate(String sql, List<Object[]> batchArgs) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(sql);

            for (Object[] args : batchArgs) {
                setParameters(pstmt, args);
                pstmt.addBatch();
            }

            if (logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine("FlexSQL Batch SQL: " + sql + ", batch size: " + batchArgs.size());
            }

            return pstmt.executeBatch();
        } catch (SQLException e) {
            throw translateException(sql, null, e);
        } finally {
            close(pstmt);
            closeConnection(conn);
        }
    }

    // ==================== 执行操作 ====================

    /**
     * 执行任意 SQL 语句
     *
     * @param sql  SQL 语句
     * @param args 参数
     * @return 是否执行成功
     */
    public boolean execute(String sql, Object... args) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(sql);
            setParameters(pstmt, args);

            if (logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine("FlexSQL SQL: " + sql);
            }

            return pstmt.execute();
        } catch (SQLException e) {
            throw translateException(sql, args, e);
        } finally {
            close(pstmt);
            closeConnection(conn);
        }
    }

    /**
     * 执行 SQL 脚本（多条语句）
     *
     * @param sqlScript SQL 脚本
     */
    public void executeScript(String sqlScript) {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = getConnection();
            stmt = conn.createStatement();

            String[] sqls = sqlScript.split(";");
            for (String sql : sqls) {
                sql = sql.trim();
                if (StringUtil.isNotEmpty(sql)) {
                    if (logger.isLoggable(java.util.logging.Level.FINE)) {
                        logger.fine("FlexSQL SQL: " + sql);
                    }
                    stmt.execute(sql);
                }
            }
        } catch (SQLException e) {
            throw translateException(sqlScript, null, e);
        } finally {
            close(stmt);
            closeConnection(conn);
        }
    }

    // ==================== 事务操作 ====================

    /**
     * 在事务中执行回调
     *
     * @param action 回调函数
     * @param <T>    返回类型
     * @return 回调结果
     */
    public <T> T executeInTransaction(FlexTransactionCallback<T> action) {
        Connection conn = null;
        String ds = DataSourceContextHolder.getDataSource();
        if (ds == null) {
            ds = "default";
        }
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            // 将连接绑定到当前线程（使用 ConnectionProxy 包装）
            ConnectionContextHolder.setConnection(ds, new ConnectionProxy(ds, conn));

            try {
                T result = action.doInTransaction(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Transaction rolled back", e);
            }
        } catch (SQLException e) {
            throw translateException("Transaction operation", null, e);
        } finally {
            // 使用 commitOrRollback 处理提交/回滚并关闭连接
            ConnectionContextHolder.commitOrRollback(ds, true);
            ConnectionContextHolder.clearAll();
        }
    }

    /**
     * 事务回调接口
     */
    @FunctionalInterface
    public interface FlexTransactionCallback<T> {
        T doInTransaction(Connection connection) throws Exception;
    }

    // ==================== 内部方法 ==================== 

    /**
     * 获取连接
     * 优先级：
     * 1. FlexSQL 事务上下文（ConnectionContextHolder）- 多数据源场景
     * 2. 直接从数据源获取新连接
     */
    protected Connection getConnection() throws SQLException {
        String ds = DataSourceContextHolder.getDataSource();
        if (ds == null) {
            ds = "default";
        }
        ConnectionProxy proxy = ConnectionContextHolder.getConnection(ds);
        if (proxy != null) {
            return proxy;
        }
        return dataSource.getConnection();
    }

    /**
     * 关闭连接
     */
    protected void closeConnection(Connection conn) {
        // 如果是 ConnectionProxy，由事务管理器负责关闭
        if (conn != null && !(conn instanceof ConnectionProxy)) {
            close(conn);
        }
    }

    /**
     * 设置 PreparedStatement 参数
     */
    private void setParameters(PreparedStatement pstmt, Object... args) throws SQLException {
        if (args == null || args.length == 0) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                pstmt.setNull(i + 1, Types.NULL);
            } else if (arg instanceof String) {
                pstmt.setString(i + 1, (String) arg);
            } else if (arg instanceof Integer) {
                pstmt.setInt(i + 1, (Integer) arg);
            } else if (arg instanceof Long) {
                pstmt.setLong(i + 1, (Long) arg);
            } else if (arg instanceof Double) {
                pstmt.setDouble(i + 1, (Double) arg);
            } else if (arg instanceof Float) {
                pstmt.setFloat(i + 1, (Float) arg);
            } else if (arg instanceof Boolean) {
                pstmt.setBoolean(i + 1, (Boolean) arg);
            } else if (arg instanceof java.util.Date) {
                pstmt.setTimestamp(i + 1, new java.sql.Timestamp(((java.util.Date) arg).getTime()));
            } else if (arg instanceof java.sql.Date) {
                pstmt.setDate(i + 1, (java.sql.Date) arg);
            } else if (arg instanceof java.sql.Timestamp) {
                pstmt.setTimestamp(i + 1, (java.sql.Timestamp) arg);
            } else if (arg instanceof BigDecimal) {
                pstmt.setBigDecimal(i + 1, (BigDecimal) arg);
            } else if (arg instanceof byte[]) {
                pstmt.setBytes(i + 1, (byte[]) arg);
            } else {
                pstmt.setObject(i + 1, arg);
            }
        }
    }

    /**
     * 关闭资源
     */
    private void close(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                logger.warning("Failed to close resource: " + e.getMessage());
            }
        }
    }

    /**
     * 转换 SQL 异常
     */
    private RuntimeException translateException(String sql, Object[] args, SQLException e) {
        StringBuilder sb = new StringBuilder("FlexSQL SQL error");
        if (sql != null) {
            sb.append(": ").append(sql);
        }
        if (args != null && args.length > 0) {
            sb.append(", args: ").append(java.util.Arrays.toString(args));
        }
        return new RuntimeException(sb.toString(), e);
    }

    /**
     * 获取数据源
     */
    public DataSource getDataSource() {
        return dataSource;
    }

}