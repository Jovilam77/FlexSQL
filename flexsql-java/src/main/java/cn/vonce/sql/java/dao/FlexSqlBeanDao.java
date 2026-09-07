package cn.vonce.sql.java.dao;

import cn.vonce.sql.bean.*;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.java.datasource.FlexDataSourceManager;
import cn.vonce.sql.java.datasource.FlexJdbcTemplate;
import cn.vonce.sql.page.ResultData;
import cn.vonce.sql.provider.SqlBeanProvider;
import cn.vonce.sql.uitls.ReflectUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.List;

/**
     * FlexSQL 原生 JDBC DAO 实现
     * 使用 FlexJdbcTemplate 执行 SQL，完全不依赖第三方 ORM 框架
     * 通过 FlexDataSourceManager 获取数据源，支持动态连接池检测
     * 
     * 支持注入自定义的 JdbcTemplate（如 SpringFlexJdbcTemplate），
     * 以获得框架特定的事务感知能力。
     *
     * @param <T>
     * @author Jovi
     * @version 1.0
     * @email imjovi@qq.com
     * @date 2024/8/12 15:03
     */
public class FlexSqlBeanDao<T> {

    private FlexJdbcTemplate jdbcTemplate;
    private final SqlBeanMeta sqlBeanMeta;

    /**
     * 使用默认数据源创建 DAO
     *
     * @param sqlBeanMeta SqlBeanMeta
     */
    public FlexSqlBeanDao(SqlBeanMeta sqlBeanMeta) {
        this(FlexDataSourceManager.getInstance().getDefaultDataSource(), sqlBeanMeta);
    }

    /**
     * 使用指定数据源创建 DAO
     *
     * @param dataSource  数据源
     * @param sqlBeanMeta SqlBeanMeta
     */
    public FlexSqlBeanDao(DataSource dataSource, SqlBeanMeta sqlBeanMeta) {
        this.jdbcTemplate = new FlexJdbcTemplate(dataSource);
        this.sqlBeanMeta = sqlBeanMeta;
    }

    /**
     * 使用指定 JdbcTemplate 创建 DAO
     * 支持注入框架特定的 JdbcTemplate（如 SpringFlexJdbcTemplate）
     *
     * @param jdbcTemplate JdbcTemplate
     * @param sqlBeanMeta  SqlBeanMeta
     */
    public FlexSqlBeanDao(FlexJdbcTemplate jdbcTemplate, SqlBeanMeta sqlBeanMeta) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlBeanMeta = sqlBeanMeta;
    }

    /**
     * 设置 JdbcTemplate
     * 允许后续注入框架特定的 JdbcTemplate
     *
     * @param jdbcTemplate JdbcTemplate
     */
    public void setJdbcTemplate(FlexJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== CRUD 操作 ====================

    /**
     * 根据ID查询
     *
     * @param clazz 实体类
     * @param id    ID
     * @return 查询结果
     */
    public T selectById(Class<?> clazz, Object id) {
        String sql = SqlBeanProvider.selectByIdSql(sqlBeanMeta, clazz, clazz, id);
        return jdbcTemplate.queryForObject(sql, (Class<T>) clazz);
    }

    /**
     * 根据ID查询（返回指定类型）
     *
     * @param clazz      实体类
     * @param returnType 返回类型
     * @param id         ID
     * @param <R>        返回类型泛型
     * @return 查询结果
     */
    public <R> R selectById(Class<?> clazz, Class<R> returnType, Object id) {
        String sql = SqlBeanProvider.selectByIdSql(sqlBeanMeta, clazz, returnType, id);
        return jdbcTemplate.queryForObject(sql, returnType);
    }

    /**
     * 查询所有
     *
     * @param clazz 实体类
     * @return 查询结果列表
     */
    public List<T> selectAll(Class<?> clazz) {
        String sql = SqlBeanProvider.selectAllSql(sqlBeanMeta, clazz, clazz, null);
        return jdbcTemplate.queryForList(sql, (Class<T>) clazz);
    }

    /**
     * 查询所有（返回指定类型）
     *
     * @param clazz      实体类
     * @param returnType 返回类型
     * @param <R>        返回类型泛型
     * @return 查询结果列表
     */
    public <R> List<R> selectAll(Class<?> clazz, Class<R> returnType) {
        String sql = SqlBeanProvider.selectAllSql(sqlBeanMeta, clazz, returnType, null);
        return jdbcTemplate.queryForList(sql, returnType);
    }

    /**
     * 根据条件查询
     *
     * @param clazz  实体类
     * @param select 查询条件
     * @return 查询结果列表
     */
    public List<T> select(Class<?> clazz, Select select) {
        String sql = SqlBeanProvider.selectSql(sqlBeanMeta, clazz, clazz, select);
        return jdbcTemplate.queryForList(sql, (Class<T>) clazz);
    }

    /**
     * 根据条件查询（返回指定类型）
     *
     * @param clazz      实体类
     * @param returnType 返回类型
     * @param select     查询条件
     * @param <R>        返回类型泛型
     * @return 查询结果列表
     */
    public <R> List<R> select(Class<R> returnType, Class<?> clazz, Select select) {
        String sql = SqlBeanProvider.selectSql(sqlBeanMeta, clazz, returnType, select);
        return jdbcTemplate.queryForList(sql, returnType);
    }

    /**
     * 根据条件查询单个
     *
     * @param clazz  实体类
     * @param select 查询条件
     * @return 查询结果
     */
    public T selectOne(Class<?> clazz, Select select) {
        String sql = SqlBeanProvider.selectSql(sqlBeanMeta, clazz, clazz, select);
        return jdbcTemplate.queryForObject(sql, (Class<T>) clazz);
    }

    /**
     * 根据条件查询单个（返回指定类型）
     *
     * @param clazz      实体类
     * @param returnType 返回类型
     * @param select     查询条件
     * @param <R>        返回类型泛型
     * @return 查询结果
     */
    public <R> R selectOne(Class<R> returnType, Class<?> clazz, Select select) {
        String sql = SqlBeanProvider.selectSql(sqlBeanMeta, clazz, returnType, select);
        return jdbcTemplate.queryForObject(sql, returnType);
    }

    /**
     * 根据条件查询数量
     *
     * @param clazz  实体类
     * @param select 查询条件
     * @return 查询结果数量
     */
    public int count(Class<?> clazz, Class<?> returnType, Select select) {
        String sql = SqlBeanProvider.countSql(sqlBeanMeta, clazz, returnType, select);
        return jdbcTemplate.queryForRowCount(sql);
    }

    /**
     * 插入数据
     *
     * @param entity 实体对象
     * @return 影响的行数
     */
    public int insert(T entity) {
        String sql = SqlBeanProvider.insertBeanSql(sqlBeanMeta, entity.getClass(), entity);
        return jdbcTemplate.update(sql);
    }

    /**
     * 插入数据（返回自增ID）
     *
     * @param entity 实体对象
     * @return 自增ID
     */
    public long insertAndGetKey(T entity) {
        String sql = SqlBeanProvider.insertBeanSql(sqlBeanMeta, entity.getClass(), entity);
        return jdbcTemplate.insertAndGetKey(sql);
    }

    /**
     * 更新数据（根据ID）
     *
     * @param clazz          实体类
     * @param bean           实体对象
     * @param id             ID
     * @param updateNotNull  是否只更新非空字段
     * @param optimisticLock 是否乐观锁
     * @param filterColumns  过滤字段
     * @return 影响的行数
     */
    public int updateById(Class<?> clazz, Object bean, Object id, boolean updateNotNull, boolean optimisticLock, Column[] filterColumns) {
        String sql = SqlBeanProvider.updateByIdSql(sqlBeanMeta, clazz, bean, id, updateNotNull, optimisticLock, filterColumns);
        return jdbcTemplate.update(sql);
    }

    /**
     * 更新数据（根据实体ID）
     *
     * @param clazz          实体类
     * @param bean           实体对象
     * @param updateNotNull  是否只更新非空字段
     * @param optimisticLock 是否乐观锁
     * @param filterColumns  过滤字段
     * @return 影响的行数
     */
    public int updateByBeanId(Class<?> clazz, Object bean, boolean updateNotNull, boolean optimisticLock, Column[] filterColumns) {
        String sql = SqlBeanProvider.updateByBeanIdSql(sqlBeanMeta, clazz, bean, updateNotNull, optimisticLock, filterColumns);
        return jdbcTemplate.update(sql);
    }

    /**
     * 根据条件更新数据
     *
     * @param clazz  实体类
     * @param update 更新条件
     * @param ignore 是否忽略where条件检查
     * @return 影响的行数
     */
    public int update(Class<?> clazz, Update update, boolean ignore) {
        String sql = SqlBeanProvider.updateSql(sqlBeanMeta, clazz, update, ignore);
        return jdbcTemplate.update(sql);
    }

    /**
     * 根据ID删除数据
     *
     * @param clazz 实体类
     * @param id    ID
     * @return 影响的行数
     */
    public int deleteById(Class<?> clazz, Object id) {
        String sql = SqlBeanProvider.deleteByIdSql(sqlBeanMeta, clazz, id);
        return jdbcTemplate.update(sql);
    }

    /**
     * 根据条件删除数据
     *
     * @param clazz  实体类
     * @param delete 删除条件
     * @param ignore 是否忽略where条件检查
     * @return 影响的行数
     */
    public int delete(Class<?> clazz, Delete delete, boolean ignore) {
        String sql = SqlBeanProvider.deleteSql(sqlBeanMeta, clazz, delete, ignore);
        return jdbcTemplate.update(sql);
    }

    /**
     * 逻辑删除（根据ID）
     *
     * @param clazz 实体类
     * @param id    ID
     * @return 影响的行数
     */
    public int logicallyDeleteById(Class<?> clazz, Object id) {
        String sql = SqlBeanProvider.logicallyDeleteByIdSql(sqlBeanMeta, clazz, id);
        return jdbcTemplate.update(sql);
    }

    /**
     * 逻辑删除（根据条件）
     *
     * @param clazz   实体类
     * @param wrapper 删除条件
     * @return 影响的行数
     */
    public int logicallyDeleteByWrapper(Class<?> clazz, cn.vonce.sql.helper.Wrapper wrapper) {
        String sql = SqlBeanProvider.logicallyDeleteBySql(sqlBeanMeta, clazz, wrapper);
        return jdbcTemplate.update(sql);
    }

    // ==================== 数据库操作 ====================

    /**
     * 根据条件将数据复制插入到指定结构的表中
     *
     * @param clazz           实体类
     * @param wrapper         条件包装器
     * @param targetSchema    目标schema
     * @param targetTableName 目标表名
     * @param columns         指定的列
     * @return 影响的行数
     */
    public int copy(Class<?> clazz, cn.vonce.sql.helper.Wrapper wrapper, String targetSchema, String targetTableName, cn.vonce.sql.bean.Column... columns) {
        String tableName = SqlBeanUtil.getTable(clazz).getName();
        String schema = SqlBeanUtil.getTable(clazz).getSchema();
        String fromTable = (schema != null ? schema + "." : "") + tableName;
        String toTable = (targetSchema != null ? targetSchema + "." : "") + targetTableName;

        // 构建查询条件
        cn.vonce.sql.bean.Select select = new cn.vonce.sql.bean.Select();
        select.where(wrapper);
        if (columns != null && columns.length > 0) {
            select.column(columns);
        }
        String selectSql = SqlBeanProvider.selectSql(sqlBeanMeta, clazz, clazz, select);
        String insertSql = "INSERT INTO " + toTable + " " + selectSql;
        return jdbcTemplate.update(insertSql);
    }

    /**
     * 创建表
     *
     * @param clazz 实体类
     * @return 是否创建成功
     */
    public boolean create(Class<?> clazz) {
        String sql = SqlBeanProvider.createTableSql(sqlBeanMeta, clazz);
        return jdbcTemplate.execute(sql);
    }

    /**
     * 删除表
     *
     * @param clazz 实体类
     * @return 是否删除成功
     */
    public boolean drop(Class<?> clazz) {
        String sql = SqlBeanProvider.dropTableSql(sqlBeanMeta, clazz);
        return jdbcTemplate.execute(sql);
    }

    /**
     * 检查表是否存在
     *
     * @param clazz 实体类
     * @return 是否存在
     */
    public boolean tableExists(Class<?> clazz) {
        try {
            String tableName = SqlBeanUtil.getTable(clazz).getName();
            String schema = SqlBeanUtil.getTable(clazz).getSchema();

            DbType dbType = sqlBeanMeta.getDbType();
            String sql = null;

            // 根据数据库类型生成检查表是否存在的SQL
            switch (dbType) {
                case MySQL:
                case MariaDB:
                    sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
                    return jdbcTemplate.queryForRowCount(sql, schema != null ? schema : "", tableName) > 0;
                case Postgresql:
                    sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?";
                    return jdbcTemplate.queryForRowCount(sql, schema != null ? schema : "public", tableName) > 0;
                case SQLServer:
                    sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
                    return jdbcTemplate.queryForRowCount(sql, schema != null ? schema : "dbo", tableName) > 0;
                case Oracle:
                    sql = "SELECT COUNT(*) FROM ALL_TABLES WHERE OWNER = ? AND TABLE_NAME = ?";
                    return jdbcTemplate.queryForRowCount(sql, schema != null ? schema.toUpperCase() : "", tableName.toUpperCase()) > 0;
                case H2:
                    sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
                    return jdbcTemplate.queryForRowCount(sql, schema != null ? schema : "PUBLIC", tableName) > 0;
                default:
                    // 默认尝试查询表
                    sql = "SELECT COUNT(*) FROM " + tableName;
                    try {
                        jdbcTemplate.queryForRowCount(sql);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 自定义SQL操作 ====================

    /**
     * 执行SQL
     *
     * @param sql SQL语句
     * @return 影响的行数
     */
    public int executeSql(String sql) {
        return jdbcTemplate.update(sql);
    }

    /**
     * 执行自定义SQL查询
     *
     * @param sql        SQL语句
     * @param returnType 返回类型
     * @param args       参数
     * @param <R>        返回类型泛型
     * @return 查询结果列表
     */
    public <R> List<R> queryForList(String sql, Class<R> returnType, Object... args) {
        return jdbcTemplate.queryForList(sql, returnType, args);
    }

    /**
     * 执行自定义SQL查询（单个结果）
     *
     * @param sql        SQL语句
     * @param returnType 返回类型
     * @param args       参数
     * @param <R>        返回类型泛型
     * @return 查询结果
     */
    public <R> R queryForObject(String sql, Class<R> returnType, Object... args) {
        return jdbcTemplate.queryForObject(sql, returnType, args);
    }

    /**
     * 执行自定义SQL更新
     *
     * @param sql  SQL语句
     * @param args 参数
     * @return 影响的行数
     */
    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }

    /**
     * 查询行数
     *
     * @param sql  SQL语句
     * @param args 参数
     * @return 行数
     */
    public int queryForRowCount(String sql, Object... args) {
        return jdbcTemplate.queryForRowCount(sql, args);
    }

    /**
     * 查询单值
     *
     * @param sql  SQL语句
     * @param args 参数
     * @return 查询结果
     */
    public Object queryForScalar(String sql, Object... args) {
        return jdbcTemplate.queryForScalar(sql, args);
    }

    // ==================== Getter ====================

    public FlexJdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public SqlBeanMeta getSqlBeanMeta() {
        return sqlBeanMeta;
    }

}