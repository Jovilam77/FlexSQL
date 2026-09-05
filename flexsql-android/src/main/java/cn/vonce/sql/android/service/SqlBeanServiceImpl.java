package cn.vonce.sql.android.service;

import android.util.Log;
import cn.vonce.sql.android.helper.DatabaseHelper;
import cn.vonce.sql.android.helper.SQLiteTemplate;
import cn.vonce.sql.android.mapper.SqlBeanMapper;
import cn.vonce.sql.bean.*;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.define.ColumnFun;
import cn.vonce.sql.define.ConditionHandle;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.helper.Wrapper;
import cn.vonce.sql.page.PageHelper;
import cn.vonce.sql.page.ResultData;
import cn.vonce.sql.provider.SqlBeanProvider;
import cn.vonce.sql.service.DbManageService;
import cn.vonce.sql.service.SqlBeanService;
import cn.vonce.sql.uitls.DateUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;

import java.util.*;


/**
 * 通用的业务实现
 *
 * @param <T>
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2019年5月22日下午16:20:12
 */
public class SqlBeanServiceImpl<T, ID> implements SqlBeanService<T, ID>, DbManageService<T> {


    private SQLiteTemplate sqliteTemplate;

    private SqlBeanMeta sqlBeanMeta;

    @Override
    public SqlBeanMeta getSqlBeanMeta() {
        if (sqlBeanMeta == null) {
            sqlBeanMeta = new SqlBeanMeta();
            sqlBeanMeta.setDbType(DbType.SQLite);
            sqlBeanMeta.setSqlBeanConfig(new SqlBeanConfig());
        }
        return sqlBeanMeta;
    }

    public Class<?> clazz;

    public SqlBeanServiceImpl() {
    }

    public SqlBeanServiceImpl(Class<?> clazz, DatabaseHelper databaseHelper) {
        this.clazz = clazz;
        sqliteTemplate = new SQLiteTemplate(databaseHelper.getWritableDatabase());
    }

    public SQLiteTemplate getSQLiteTemplate() {
        return sqliteTemplate;
    }

    @Override
    public Class<?> getBeanClass() {
        return clazz;
    }

    @Override
    public void dropTable() {
        List<String> nameList = sqliteTemplate.query(SqlBeanProvider.selectTableListSql(getSqlBeanMeta(), null, SqlBeanUtil.getTable(clazz).getName()), new SqlBeanMapper<String>(clazz, String.class));
        if (nameList == null || nameList.isEmpty()) {
            return;
        }
        sqliteTemplate.execSQL(SqlBeanProvider.dropTableSql(getSqlBeanMeta(), clazz));
    }

    @Override
    public void createTable() {
        sqliteTemplate.execSQL(SqlBeanProvider.createTableSql(getSqlBeanMeta(), clazz));
    }

    @Override
    public void dropAndCreateTable() {
        dropTable();
        createTable();
    }

    @Override
    public List<TableInfo> getTableList() {
        return this.getTableList(null);
    }

    @Override
    public List<TableInfo> getTableList(String tableName) {
        return sqliteTemplate.query(SqlBeanProvider.selectTableListSql(getSqlBeanMeta(), null, tableName), new SqlBeanMapper<TableInfo>(clazz, TableInfo.class));
    }

    @Override
    public List<TableInfo> getTableList(String schema, String tableName) {
        return this.getTableList(tableName);
    }

    @Override
    public List<ColumnInfo> getColumnInfoList() {
        return this.getColumnInfoList(null);
    }

    @Override
    public List<ColumnInfo> getColumnInfoList(String tableName) {
        return sqliteTemplate.query(SqlBeanProvider.selectColumnListSql(getSqlBeanMeta(), null, tableName), new SqlBeanMapper<ColumnInfo>(clazz, ColumnInfo.class));
    }

    @Override
    public List<ColumnInfo> getColumnInfoList(String schema, String tableName) {
        return this.getColumnInfoList(tableName);
    }

    @Override
    public String backup() {
        String targetTableName = SqlBeanUtil.getTable(clazz).getName() + "_" + DateUtil.dateToString(new Date(), "yyyyMMddHHmmssSSS");
        sqliteTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, null, null, targetTableName, null));
        return targetTableName;
    }

    @Override
    public void backup(String targetTableName) {
        sqliteTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, null, null, targetTableName, null));
    }

    @Override
    public void backup(String targetSchema, String targetTableName) {
        sqliteTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, null, targetSchema, targetTableName, null));
    }

    @Override
    public void backup(Wrapper wrapper, String targetSchema, String targetTableName) {
        sqliteTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, null));
    }

    @Override
    public void backup(ConditionHandle<T> cond, String targetSchema, String targetTableName) {
        this.backup(this.conditionHandle(cond), targetSchema, targetTableName);
    }

    @Override
    public void backup(Wrapper wrapper, String targetTableName, Column... columns) {
        sqliteTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, columns));
    }

    @Override
    public void backup(ConditionHandle<T> cond, String targetTableName, Column... columns) {
        this.backup(this.conditionHandle(cond), targetTableName, columns);
    }

    @Override
    public <R> void backup(Wrapper wrapper, String targetTableName, ColumnFun<T, R>... columns) {
        sqliteTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, SqlBeanUtil.funToColumn(columns)));
    }

    @Override
    public <R> void backup(ConditionHandle<T> cond, String targetTableName, ColumnFun<T, R>... columns) {
        this.backup(this.conditionHandle(cond), targetTableName, columns);
    }

    @Override
    public void backup(Wrapper wrapper, String targetSchema, String targetTableName, Column... columns) {
        sqliteTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, columns));
    }

    @Override
    public void backup(ConditionHandle<T> cond, String targetSchema, String targetTableName, Column... columns) {
        this.backup(this.conditionHandle(cond), targetSchema, targetTableName, columns);
    }

    @Override
    public <R> void backup(Wrapper wrapper, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        sqliteTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, SqlBeanUtil.funToColumn(columns)));
    }

    @Override
    public <R> void backup(ConditionHandle<T> cond, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        this.backup(this.conditionHandle(cond), targetSchema, targetTableName, columns);
    }

    @Override
    public int copy(Wrapper wrapper, String targetTableName) {
        return sqliteTemplate.update(SqlBeanProvider.copySql(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, null));
    }

    @Override
    public int copy(ConditionHandle<T> cond, String targetTableName) {
        return this.copy(this.conditionHandle(cond), targetTableName);
    }

    @Override
    public int copy(Wrapper wrapper, String targetSchema, String targetTableName) {
        return sqliteTemplate.update(SqlBeanProvider.copySql(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, null));
    }

    @Override
    public int copy(ConditionHandle<T> cond, String targetSchema, String targetTableName) {
        return this.copy(this.conditionHandle(cond), targetSchema, targetTableName);
    }

    @Override
    public int copy(Wrapper wrapper, String targetTableName, Column... columns) {
        return sqliteTemplate.update(SqlBeanProvider.copySql(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, columns));
    }

    @Override
    public int copy(ConditionHandle<T> cond, String targetTableName, Column... columns) {
        return this.copy(this.conditionHandle(cond), targetTableName, columns);
    }

    @Override
    public <R> int copy(Wrapper wrapper, String targetTableName, ColumnFun<T, R>... columns) {
        return sqliteTemplate.update(SqlBeanProvider.copySql(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, SqlBeanUtil.funToColumn(columns)));
    }

    @Override
    public <R> int copy(ConditionHandle<T> cond, String targetTableName, ColumnFun<T, R>... columns) {
        return this.copy(this.conditionHandle(cond), targetTableName, columns);
    }

    @Override
    public int copy(Wrapper wrapper, String targetSchema, String targetTableName, Column[] columns) {
        return sqliteTemplate.update(SqlBeanProvider.copySql(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, columns));
    }

    @Override
    public int copy(ConditionHandle<T> cond, String targetSchema, String targetTableName, Column... columns) {
        return this.copy(this.conditionHandle(cond), targetSchema, targetTableName, columns);
    }

    @Override
    public <R> int copy(Wrapper wrapper, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        return sqliteTemplate.update(SqlBeanProvider.copySql(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, SqlBeanUtil.funToColumn(columns)));
    }

    @Override
    public <R> int copy(ConditionHandle<T> cond, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        return this.copy(this.conditionHandle(cond), targetSchema, targetTableName, columns);
    }

    @Override
    public int alter(Table table, List<ColumnInfo> columnInfoList) {
        List<String> sqlList = SqlBeanProvider.buildAlterSql(getSqlBeanMeta(), clazz != null ? clazz : this.clazz, columnInfoList);
        int count = 0;
        if (sqlList != null && sqlList.size() > 0) {
            for (String sql : sqlList) {
                count += sqliteTemplate.update(sql);
            }
        }
        return count;
    }

    @Override
    public int alter(Alter alter) {
        List<Alter> alterList = new ArrayList<>();
        alterList.add(alter);
        return alter(alterList);
    }

    @Override
    public int alter(List<Alter> alterList) {
        List<String> sqlList = SqlBeanProvider.alterSql(getSqlBeanMeta().getDbType(), alterList);
        int count = 0;
        if (sqlList != null && sqlList.size() > 0) {
            for (String sql : sqlList) {
                count += sqliteTemplate.update(sql);
            }
        }
        return count;
    }

    @Override
    public T selectById(ID id) {
        if (id == null) {
            return null;
        }
        try {
            return sqliteTemplate.queryForObject(SqlBeanProvider.selectByIdSql(getSqlBeanMeta(), clazz, null, id), new SqlBeanMapper<T>(clazz, clazz));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public <R> R selectById(Class<R> returnType, ID id) {
        if (id == null) {
            return null;
        }
        try {
            return sqliteTemplate.queryForObject(SqlBeanProvider.selectByIdSql(getSqlBeanMeta(), clazz, returnType, id), new SqlBeanMapper<R>(clazz, returnType));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<T> selectByIds(ID... ids) {
        if (ids == null || ids.length == 0) {
            throw new SqlBeanException("selectByIds方法ids参数至少拥有一个值");
        }
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectByIdsSql(getSqlBeanMeta(), clazz, null, ids), new SqlBeanMapper<T>(clazz, clazz));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public <R> List<R> selectByIds(Class<R> returnType, ID... ids) {
        if (ids == null || ids.length == 0) {
            throw new SqlBeanException("selectByIds方法ids参数至少拥有一个值");
        }
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectByIdsSql(getSqlBeanMeta(), clazz, returnType, ids), new SqlBeanMapper<R>(clazz, returnType));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }


    @Override
    public T selectOne(Select select) {
        try {
            return sqliteTemplate.queryForObject(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SqlBeanMapper<T>(clazz, clazz));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }


    @Override
    public <R> R selectOne(Class<R> returnType, Select select) {
        try {
            return sqliteTemplate.queryForObject(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, returnType, select), new SqlBeanMapper<R>(clazz, returnType));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public Map<String, Object> selectMap(Select select) {
        try {
            return sqliteTemplate.queryForObject(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SqlBeanMapper<Map<String, Object>>(clazz, Map.class));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public T selectOneBy(String where, Object... args) {
        try {
            return sqliteTemplate.queryForObject(SqlBeanProvider.selectBySql(getSqlBeanMeta(), clazz, null, null, where, args), new SqlBeanMapper<T>(clazz, clazz));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public <R> R selectOneBy(Class<R> returnType, String where, Object... args) {
        try {
            return sqliteTemplate.queryForObject(SqlBeanProvider.selectBySql(getSqlBeanMeta(), clazz, returnType, null, where, args), new SqlBeanMapper<R>(clazz, returnType));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public T selectOneBy(Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        return sqliteTemplate.queryForObject(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SqlBeanMapper<T>(clazz, clazz));
    }

    @Override
    public T selectOneBy(ConditionHandle<T> cond) {
        return this.selectOneBy(this.conditionHandle(cond));
    }

    @Override
    public <R> R selectOneBy(Class<R> returnType, Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        try {
            return sqliteTemplate.queryForObject(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, returnType, select), new SqlBeanMapper<R>(clazz, clazz));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public <R> R selectOneBy(Class<R> returnType, ConditionHandle<T> cond) {
        return this.selectOneBy(returnType, this.conditionHandle(cond));
    }

    @Override
    public <R> List<R> selectBy(Class<R> returnType, String where, Object... args) {
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectBySql(getSqlBeanMeta(), clazz, returnType, null, where, args), new SqlBeanMapper<R>(clazz, returnType));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public <R> List<R> selectBy(Class<R> returnType, Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, returnType, select), new SqlBeanMapper<R>(clazz, clazz));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public <R> List<R> selectBy(Class<R> returnType, ConditionHandle<T> cond) {
        return this.selectBy(returnType, this.conditionHandle(cond));
    }

    @Override
    public <R> List<R> selectBy(Class<R> returnType, Paging paging, String where, Object... args) {
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectBySql(getSqlBeanMeta(), clazz, returnType, paging, where, args), new SqlBeanMapper<R>(clazz, returnType));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public <R> List<R> selectBy(Class<R> returnType, Paging paging, Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        select.page(paging.getPagenum(), paging.getPagesize(), paging.getStartByZero());
        select.orderBy(paging.getOrders());
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, returnType, select), new SqlBeanMapper<R>(clazz, clazz));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public <R> List<R> selectBy(Class<R> returnType, Paging paging, ConditionHandle<T> cond) {
        return this.selectBy(returnType, paging, this.conditionHandle(cond));
    }

    @Override
    public List<T> selectBy(String where, Object... args) {
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectBySql(getSqlBeanMeta(), clazz, null, null, where, args), new SqlBeanMapper<T>(clazz, clazz));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<T> selectBy(Wrapper where) {
        Select select = new Select();
        select.where(where);
        return sqliteTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SqlBeanMapper<T>(clazz, clazz));
    }

    @Override
    public List<T> selectBy(ConditionHandle<T> cond) {
        return this.selectBy(this.conditionHandle(cond));
    }

    @Override
    public List<T> selectBy(Paging paging, String where, Object... args) {
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectBySql(getSqlBeanMeta(), clazz, null, paging, where, args), new SqlBeanMapper<T>(clazz, clazz));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<T> selectBy(Paging paging, Wrapper where) {
        Select select = new Select();
        select.where(where);
        select.page(paging.getPagenum(), paging.getPagesize(), paging.getStartByZero());
        select.orderBy(paging.getOrders());
        return sqliteTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SqlBeanMapper<T>(clazz, clazz));
    }

    @Override
    public List<T> selectBy(Paging paging, ConditionHandle<T> cond) {
        return this.selectBy(paging, this.conditionHandle(cond));
    }

    @Override
    public int countBy(String where, Object... args) {
        return sqliteTemplate.queryForObject(SqlBeanProvider.countBySql(getSqlBeanMeta(), clazz, where, args), new SqlBeanMapper<Integer>(clazz, Integer.class));
    }

    @Override
    public int countBy(Wrapper where) {
        Select select = new Select();
        select.where(where);
        return sqliteTemplate.queryForObject(SqlBeanProvider.countSql(getSqlBeanMeta(), clazz, null, select), new SqlBeanMapper<>(clazz, Integer.class));
    }

    @Override
    public int countBy(ConditionHandle<T> cond) {
        return this.countBy(this.conditionHandle(cond));
    }

    @Override
    public int count() {
        return sqliteTemplate.queryForObject(SqlBeanProvider.countBySql(getSqlBeanMeta(), clazz, null, null), new SqlBeanMapper<Integer>(clazz, Integer.class));
    }

    @Override
    public List<T> select() {
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectAllSql(getSqlBeanMeta(), clazz, null, null), new SqlBeanMapper<T>(clazz, clazz));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<T> select(Paging paging) {
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectAllSql(getSqlBeanMeta(), clazz, null, paging), new SqlBeanMapper<T>(clazz, clazz));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public <R> List<R> select(Class<R> returnType) {
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectAllSql(getSqlBeanMeta(), clazz, returnType, null), new SqlBeanMapper<R>(clazz, returnType));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public <R> List<R> select(Class<R> returnType, Paging paging) {
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectAllSql(getSqlBeanMeta(), clazz, returnType, paging), new SqlBeanMapper<R>(clazz, returnType));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> selectMapList(Select select) {
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SqlBeanMapper<Map<String, Object>>(clazz, Map.class));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }

    }

    @Override
    public <R> List<R> select(Class<R> returnType, Select select) {
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, returnType, select), new SqlBeanMapper<R>(clazz, returnType));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<T> select(Select select) {
        try {
            return sqliteTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SqlBeanMapper<T>(clazz, clazz));
        } catch (Exception e) {
            Log.e("flexsql", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public int count(Select select) {
        return sqliteTemplate.queryForObject(SqlBeanProvider.countSql(getSqlBeanMeta(), clazz, null, select), new SqlBeanMapper<Integer>(clazz, Integer.class));
    }

    @Override
    public int count(Class<?> clazz, Select select) {
        return sqliteTemplate.queryForObject(SqlBeanProvider.countSql(getSqlBeanMeta(), clazz, null, select), new SqlBeanMapper<Integer>(clazz, Integer.class));
    }

    @Override
    public ResultData<T> paging(Select select, PageHelper<T> pageHelper) {
        pageHelper.paging(select, this);
        return pageHelper.getResultData();
    }

    @Override
    public ResultData<T> paging(Select select, int pagenum, int pagesize) {
        PageHelper<T> pageHelper = new PageHelper<>(pagenum, pagesize);
        pageHelper.paging(select, this);
        return pageHelper.getResultData();
    }

    @Override
    public <R> ResultData<R> paging(Class<R> tClazz, Select select, PageHelper<R> pageHelper) {
        pageHelper.paging(tClazz, select, this);
        return pageHelper.getResultData();
    }

    @Override
    public <R> ResultData<R> paging(Class<R> tClazz, Select select, int pagenum, int pagesize) {
        PageHelper<R> pageHelper = new PageHelper<>(pagenum, pagesize);
        pageHelper.paging(tClazz, select, this);
        return pageHelper.getResultData();
    }

    @Override
    public int deleteById(ID... id) {
        if (id == null || id.length == 0) {
            throw new SqlBeanException("deleteById方法id参数至少拥有一个值");
        }
        return sqliteTemplate.update(SqlBeanProvider.deleteByIdSql(getSqlBeanMeta(), clazz, id));
    }

    @Override
    public int deleteBy(String where, Object... args) {
        return sqliteTemplate.update(SqlBeanProvider.deleteBySql(getSqlBeanMeta(), clazz, where, args));
    }

    @Override
    public int deleteBy(Wrapper where) {
        Delete delete = new Delete();
        delete.setLogicallyDelete(SqlBeanUtil.checkLogically(clazz));
        delete.where(where);
        return sqliteTemplate.update(SqlBeanProvider.deleteSql(getSqlBeanMeta(), clazz, delete, false));
    }

    @Override
    public int deleteBy(ConditionHandle<T> cond) {
        return this.deleteBy(this.conditionHandle(cond));
    }

    @Override
    public int delete(Delete delete) {
        return sqliteTemplate.update(SqlBeanProvider.deleteSql(getSqlBeanMeta(), clazz, delete, false));
    }

    @Override
    public int delete(Delete delete, boolean ignore) {
        return sqliteTemplate.update(SqlBeanProvider.deleteSql(getSqlBeanMeta(), clazz, delete, ignore));
    }

    @Override
    public int logicallyDeleteById(ID... id) {
        if (id == null || id.length == 0) {
            throw new SqlBeanException("logicallyDeleteById方法id参数至少拥有一个值");
        }
        return sqliteTemplate.update(SqlBeanProvider.logicallyDeleteByIdSql(getSqlBeanMeta(), clazz, id));
    }

    @Override
    public int logicallyDeleteBy(String where, Object... args) {
        return sqliteTemplate.update(SqlBeanProvider.logicallyDeleteBySql(getSqlBeanMeta(), clazz, where, args));
    }

    @Override
    public int logicallyDeleteBy(Wrapper where) {
        return sqliteTemplate.update(SqlBeanProvider.logicallyDeleteBySql(getSqlBeanMeta(), clazz, where));
    }

    @Override
    public int logicallyDeleteBy(ConditionHandle<T> cond) {
        return this.logicallyDeleteBy(this.conditionHandle(cond));
    }

    @Override
    public int update(Update<T> update) {
        return sqliteTemplate.update(SqlBeanProvider.updateSql(getSqlBeanMeta(), clazz, update, false));
    }

    @Override
    public int update(Update<T> update, boolean ignore) {
        return sqliteTemplate.update(SqlBeanProvider.updateSql(getSqlBeanMeta(), clazz, update, ignore));
    }

    @Override
    public int updateById(T bean, ID id) {
        return sqliteTemplate.update(SqlBeanProvider.updateByIdSql(getSqlBeanMeta(), clazz, bean, id, true, false, null));
    }

    @Override
    public int updateById(T bean, ID id, boolean updateNotNull, boolean optimisticLock) {
        return sqliteTemplate.update(SqlBeanProvider.updateByIdSql(getSqlBeanMeta(), clazz, bean, id, updateNotNull, optimisticLock, null));
    }

    @Override
    public int updateByBeanId(T bean) {
        return sqliteTemplate.update(SqlBeanProvider.updateByBeanIdSql(getSqlBeanMeta(), clazz, bean, true, false, null));
    }

    @Override
    public int updateById(T bean, ID id, boolean updateNotNull, boolean optimisticLock, Column... filterColumns) {
        return sqliteTemplate.update(SqlBeanProvider.updateByIdSql(getSqlBeanMeta(), clazz, bean, id, updateNotNull, optimisticLock, filterColumns));
    }

    @Override
    public <R> int updateById(T bean, ID id, boolean updateNotNull, boolean optimisticLock, ColumnFun<T, R>... filterColumns) {
        return sqliteTemplate.update(SqlBeanProvider.updateByIdSql(getSqlBeanMeta(), clazz, bean, id, updateNotNull, optimisticLock, SqlBeanUtil.funToColumn(filterColumns)));
    }

    @Override
    public int updateBy(T bean, String where, Object... args) {
        return sqliteTemplate.update(SqlBeanProvider.updateBySql(getSqlBeanMeta(), clazz, bean, true, false, null, where, args));
    }

    @Override
    public int updateByBeanId(T bean, boolean updateNotNull, boolean optimisticLock) {
        return sqliteTemplate.update(SqlBeanProvider.updateByBeanIdSql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, null));
    }

    @Override
    public int updateByBeanId(T bean, boolean updateNotNull, boolean optimisticLock, Column... filterColumns) {
        return sqliteTemplate.update(SqlBeanProvider.updateByBeanIdSql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, filterColumns));
    }

    @Override
    public <R> int updateByBeanId(T bean, boolean updateNotNull, boolean optimisticLock, ColumnFun<T, R>... filterColumns) {
        return sqliteTemplate.update(SqlBeanProvider.updateByBeanIdSql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, SqlBeanUtil.funToColumn(filterColumns)));
    }

    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, String where, Object... args) {
        return sqliteTemplate.update(SqlBeanProvider.updateBySql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, null, where, args));
    }

    @Override
    public int updateBy(T bean, Wrapper wrapper) {
        Update<T> update = new Update<>();
        update.bean(bean).notNull(true).optimisticLock(false).where(wrapper);
        return sqliteTemplate.update(SqlBeanProvider.updateSql(getSqlBeanMeta(), clazz, update, false));
    }

    @Override
    public int updateBy(T bean, ConditionHandle<T> cond) {
        return this.updateBy(bean, this.conditionHandle(cond));
    }

    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Wrapper wrapper) {
        Update<T> update = new Update<>();
        update.bean(bean).notNull(updateNotNull).optimisticLock(optimisticLock).where(wrapper);
        return sqliteTemplate.update(SqlBeanProvider.updateSql(getSqlBeanMeta(), clazz, update, false));
    }

    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, ConditionHandle<T> cond) {
        return this.updateBy(bean, this.conditionHandle(cond));
    }

    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Column[] filterColumns, String where, Object... args) {
        return sqliteTemplate.update(SqlBeanProvider.updateBySql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, filterColumns, where, args));
    }

    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Wrapper wrapper, Column... filterColumns) {
        Update<T> update = new Update<>();
        update.bean(bean).notNull(updateNotNull).optimisticLock(optimisticLock).filterFields(filterColumns).where(wrapper);
        return sqliteTemplate.update(SqlBeanProvider.updateSql(getSqlBeanMeta(), clazz, update, false));
    }

    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, ConditionHandle<T> cond, Column... filterColumns) {
        return this.updateBy(bean, updateNotNull, optimisticLock, this.conditionHandle(cond), filterColumns);
    }

    @Override
    public <R> int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Wrapper wrapper, ColumnFun<T, R>... filterColumns) {
        return this.updateBy(bean, updateNotNull, optimisticLock, wrapper, SqlBeanUtil.funToColumn(filterColumns));
    }

    @Override
    public <R> int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, ConditionHandle<T> cond, ColumnFun<T, R>... filterColumns) {
        return this.updateBy(bean, updateNotNull, optimisticLock, this.conditionHandle(cond), filterColumns);
    }

    @Override
    public int updateByBean(T bean, String where) {
        return sqliteTemplate.update(SqlBeanProvider.updateByBeanSql(getSqlBeanMeta(), clazz, bean, true, false, where, null));
    }

    @Override
    public int updateByBean(T bean, boolean updateNotNull, boolean optimisticLock, String where) {
        return sqliteTemplate.update(SqlBeanProvider.updateByBeanSql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, where, null));
    }

    @Override
    public int updateByBean(T bean, boolean updateNotNull, boolean optimisticLock, String where, Column... filterColumns) {
        return sqliteTemplate.update(SqlBeanProvider.updateByBeanSql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, where, filterColumns));
    }

    @Override
    public <R> int updateByBean(T bean, boolean updateNotNull, boolean optimisticLock, String where, ColumnFun<T, R>[] filterColumns) {
        return sqliteTemplate.update(SqlBeanProvider.updateByBeanSql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, where, SqlBeanUtil.funToColumn(filterColumns)));
    }

    @Override
    public int insert(T... bean) {
        if (bean == null || bean.length == 0) {
            throw new SqlBeanException("insert方法bean参数至少拥有一个值");
        }
        List<Long> keys = new ArrayList<>();
        for (T b : bean) {
            long rowid = sqliteTemplate.insert(SqlBeanProvider.insertBeanSql(getSqlBeanMeta(), clazz, Collections.singletonList(b)));
            keys.add(rowid < 0 ? null : rowid);
        }
        SqlBeanUtil.setAutoIncrId(clazz, keys, bean);
        return bean.length;
    }

    @Override
    public int insert(Collection<T> beanList) {
        if (beanList == null || beanList.size() == 0) {
            throw new SqlBeanException("insert方法beanList参数至少拥有一个值");
        }
        List<Long> keys = new ArrayList<>();
        for (T b : beanList) {
            long rowid = sqliteTemplate.insert(SqlBeanProvider.insertBeanSql(getSqlBeanMeta(), clazz, Collections.singletonList(b)));
            keys.add(rowid < 0 ? null : rowid);
        }
        SqlBeanUtil.setAutoIncrId(clazz, keys, beanList.toArray());
        return beanList.size();
    }

    @Override
    public int insert(Insert<T> insert) {
        List<T> beans = insert.getBean();
        if (beans != null && !beans.isEmpty()) {
            List<Long> keys = new ArrayList<>();
            for (T b : beans) {
                long rowid = sqliteTemplate.insert(SqlBeanProvider.insertBeanSql(getSqlBeanMeta(), clazz, Collections.singletonList(b)));
                keys.add(rowid < 0 ? null : rowid);
            }
            SqlBeanUtil.setAutoIncrId(clazz, keys, beans.toArray());
            return beans.size();
        }
        return (int) sqliteTemplate.insert(SqlBeanProvider.insertBeanSql(getSqlBeanMeta(), clazz, insert));
    }

    protected void conditionHandle(CommonCondition<?> condition, ConditionHandle<T> cond) {
        Condition<T> result = new Condition<>();
        cond.handle(result);
        condition.where().getDataList().addAll(result.getDataList());
    }

    protected Wrapper conditionHandle(ConditionHandle<T> cond) {
        Condition<T> result = new Condition<>();
        cond.handle(result);
        return SqlBeanUtil.conditionDataToWrapper(result.getDataList());
    }

}
