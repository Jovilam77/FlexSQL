package cn.vonce.sql.java.service;

import cn.vonce.sql.bean.*;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.define.ColumnFun;
import cn.vonce.sql.define.ConditionHandle;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.helper.Wrapper;
import cn.vonce.sql.java.annotation.DbSwitch;
import cn.vonce.sql.java.annotation.UseFlexSql;
import cn.vonce.sql.java.dao.FlexSqlBeanDao;
import cn.vonce.sql.java.enumerate.DbRole;
import cn.vonce.sql.page.PageHelper;
import cn.vonce.sql.page.ResultData;
import cn.vonce.sql.provider.SqlBeanProvider;
import cn.vonce.sql.service.AdvancedDbManageService;
import cn.vonce.sql.service.SqlBeanService;
import cn.vonce.sql.uitls.SqlBeanUtil;

import java.util.*;

/**
 * FlexSQL 原生 JDBC Service 实现
 * 不依赖 MyBatis，使用 FlexSqlBeanDao 和 FlexJdbcTemplate 执行 SQL
 *
 * 使用方式：
 * 1. 在服务类上使用 @UseFlexSql 注解
 * 2. 继承此类并指定实体类型和ID类型
 * 3. 使用 @DbSwitch 注解切换数据源
 * 4. 单数据源使用 Spring @Transactional 注解开启事务，多数据源使用 @DbTransactional 注解
 *
 * @param <T> 实体类型
 * @param <ID> ID类型
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
@UseFlexSql
public class FlexSqlBeanServiceImpl<T, ID> extends BaseSqlBeanServiceImpl<T> implements SqlBeanService<T, ID>, AdvancedDbManageService<T> {

    /**
     * SQL Bean DAO
     */
    protected FlexSqlBeanDao<T> flexSqlBeanDao;

    /**
     * SQL Bean Meta
     */
    protected SqlBeanMeta sqlBeanMeta;

    /**
     * 实体类
     */
    private final Class<?> clazz;

    /**
     * 默认构造函数
     * 适用于框架自动装配场景（Spring/Solon/JFinal）
     */
    public FlexSqlBeanServiceImpl() {
        List<Class<?>> classes = SqlBeanUtil.getGenericTypeBySuperclass(this.getClass());
        if (!classes.isEmpty()) {
            this.clazz = classes.get(0);
        } else {
            this.clazz = null;
        }
    }

    /**
     * 带参数构造函数
     * 适用于手动初始化场景
     *
     * @param sqlBeanMeta SqlBeanMeta
     */
    public FlexSqlBeanServiceImpl(SqlBeanMeta sqlBeanMeta) {
        this();
        this.sqlBeanMeta = sqlBeanMeta;
        this.flexSqlBeanDao = new FlexSqlBeanDao<>(sqlBeanMeta);
    }

    // ==================== SqlBeanService 接口实现 ====================

    @Override
    public SqlBeanMeta getSqlBeanMeta() {
        return sqlBeanMeta;
    }

    @Override
    public Class<?> getBeanClass() {
        return clazz;
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public T selectById(ID id) {
        if (id == null) {
            return null;
        }
        return flexSqlBeanDao.selectById(clazz, id);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectById(Class<R> returnType, ID id) {
        if (id == null) {
            return null;
        }
        return flexSqlBeanDao.selectById(clazz, returnType, id);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectByIds(ID... ids) {
        if (ids == null || ids.length == 0) {
            throw new SqlBeanException("selectByIds方法ids参数至少拥有一个值");
        }
        List<T> result = new ArrayList<>();
        for (ID id : ids) {
            T item = selectById(id);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectByIds(Class<R> returnType, ID... ids) {
        if (ids == null || ids.length == 0) {
            throw new SqlBeanException("selectByIds方法ids参数至少拥有一个值");
        }
        List<R> result = new ArrayList<>();
        for (ID id : ids) {
            R item = selectById(returnType, id);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public T selectOne(Select select) {
        return flexSqlBeanDao.selectOne(clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectOne(Class<R> returnType, Select select) {
        return flexSqlBeanDao.selectOne(returnType, clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public Map<String, Object> selectMap(Select select) {
        String sql = SqlBeanProvider.selectSql(sqlBeanMeta, clazz, clazz, select);
        return flexSqlBeanDao.getJdbcTemplate().queryForMap(sql);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public T selectOneBy(String where, Object... args) {
        String sql = SqlBeanProvider.selectBySql(sqlBeanMeta, clazz, clazz, null, where, args);
        return flexSqlBeanDao.getJdbcTemplate().queryForObject(sql, (Class<T>) clazz);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectOneBy(Class<R> returnType, String where, Object... args) {
        String sql = SqlBeanProvider.selectBySql(sqlBeanMeta, clazz, returnType, null, where, args);
        return flexSqlBeanDao.getJdbcTemplate().queryForObject(sql, returnType);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public T selectOneBy(Wrapper where) {
        Select select = new Select();
        select.where(where);
        return flexSqlBeanDao.selectOne(clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public T selectOneBy(ConditionHandle<T> cond) {
        return this.selectOneBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectOneBy(Class<R> returnType, Wrapper where) {
        Select select = new Select();
        select.where(where);
        return flexSqlBeanDao.selectOne(returnType, clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectOneBy(Class<R> returnType, ConditionHandle<T> cond) {
        return this.selectOneBy(returnType, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(String where, Object... args) {
        String sql = SqlBeanProvider.selectBySql(sqlBeanMeta, clazz, clazz, null, where, args);
        return flexSqlBeanDao.getJdbcTemplate().queryForList(sql, (Class<T>) clazz);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(Wrapper where) {
        Select select = new Select();
        select.where(where);
        return flexSqlBeanDao.select(clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(ConditionHandle<T> cond) {
        return selectBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(Paging paging, String where, Object... args) {
        String sql = SqlBeanProvider.selectBySql(sqlBeanMeta, clazz, clazz, paging, where, args);
        return flexSqlBeanDao.getJdbcTemplate().queryForList(sql, (Class<T>) clazz);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(Paging paging, Wrapper where) {
        Select select = new Select();
        select.where(where);
        select.page(paging.getPagenum(), paging.getPagesize(), paging.getStartByZero());
        select.orderBy(paging.getOrders());
        return flexSqlBeanDao.select(clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(Paging paging, ConditionHandle<T> cond) {
        return selectBy(paging, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, String where, Object... args) {
        String sql = SqlBeanProvider.selectBySql(sqlBeanMeta, clazz, returnType, null, where, args);
        return flexSqlBeanDao.getJdbcTemplate().queryForList(sql, returnType);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, Wrapper where) {
        Select select = new Select();
        select.where(where);
        return flexSqlBeanDao.select(returnType, clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, ConditionHandle<T> cond) {
        return this.selectBy(returnType, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, Paging paging, String where, Object... args) {
        String sql = SqlBeanProvider.selectBySql(sqlBeanMeta, clazz, returnType, paging, where, args);
        return flexSqlBeanDao.getJdbcTemplate().queryForList(sql, returnType);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, Paging paging, Wrapper where) {
        Select select = new Select();
        select.where(where);
        select.page(paging.getPagenum(), paging.getPagesize(), paging.getStartByZero());
        select.orderBy(paging.getOrders());
        return flexSqlBeanDao.select(returnType, clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, Paging paging, ConditionHandle<T> cond) {
        return this.selectBy(returnType, paging, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int countBy(String where, Object... args) {
        String sql = SqlBeanProvider.countBySql(sqlBeanMeta, clazz, where, args);
        return flexSqlBeanDao.getJdbcTemplate().queryForRowCount(sql);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int countBy(Wrapper where) {
        Select select = new Select();
        select.where(where);
        return flexSqlBeanDao.count(clazz, null, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int countBy(ConditionHandle<T> cond) {
        return countBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int count() {
        String sql = SqlBeanProvider.selectAllSql(sqlBeanMeta, clazz, clazz, null);
        // 使用 COUNT 语句
        sql = "SELECT COUNT(*) FROM (" + sql + ") AS t";
        return flexSqlBeanDao.getJdbcTemplate().queryForRowCount(sql);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public List<T> select() {
        return flexSqlBeanDao.selectAll(clazz);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public List<T> select(Paging paging) {
        String sql = SqlBeanProvider.selectAllSql(sqlBeanMeta, clazz, clazz, paging);
        return flexSqlBeanDao.getJdbcTemplate().queryForList(sql, (Class<T>) clazz);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> List<R> select(Class<R> returnType) {
        return flexSqlBeanDao.selectAll(clazz, returnType);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> List<R> select(Class<R> returnType, Paging paging) {
        String sql = SqlBeanProvider.selectAllSql(sqlBeanMeta, clazz, returnType, paging);
        return flexSqlBeanDao.getJdbcTemplate().queryForList(sql, returnType);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<Map<String, Object>> selectMapList(Select select) {
        String sql = SqlBeanProvider.selectSql(sqlBeanMeta, clazz, clazz, select);
        return flexSqlBeanDao.getJdbcTemplate().queryForMapList(sql);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> select(Select select) {
        return flexSqlBeanDao.select(clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> select(Class<R> returnType, Select select) {
        return flexSqlBeanDao.select(returnType, clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int count(Select select) {
        return flexSqlBeanDao.count(clazz, null, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int count(Class<?> returnType, Select select) {
        return flexSqlBeanDao.count(clazz, returnType, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public ResultData<T> paging(Select select, PageHelper<T> pageHelper) {
        pageHelper.paging(select, this);
        return pageHelper.getResultData();
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public ResultData<T> paging(Select select, int pagenum, int pagesize) {
        PageHelper<T> pageHelper = new PageHelper<>(pagenum, pagesize);
        pageHelper.paging(select, this);
        return pageHelper.getResultData();
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> ResultData<R> paging(Class<R> returnType, Select select, PageHelper<R> pageHelper) {
        pageHelper.paging(returnType, select, this);
        return pageHelper.getResultData();
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> ResultData<R> paging(Class<R> returnType, Select select, int pagenum, int pagesize) {
        PageHelper<R> pageHelper = new PageHelper<>(pagenum, pagesize);
        pageHelper.paging(returnType, select, this);
        return pageHelper.getResultData();
    }

    // ==================== InsertService 接口实现 ====================

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int insert(T... bean) {
        if (bean == null || bean.length == 0) {
            throw new SqlBeanException("insert方法bean参数至少拥有一个值");
        }
        List<T> beanList = Arrays.asList(bean);
        int count = 0;
        for (T t : beanList) {
            count += flexSqlBeanDao.insert(t);
        }
        return count;
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int insert(Collection<T> beanList) {
        if (beanList == null || beanList.size() == 0) {
            throw new SqlBeanException("insert方法beanList参数至少拥有一个值");
        }
        int count = 0;
        for (T t : beanList) {
            count += flexSqlBeanDao.insert(t);
        }
        return count;
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int insert(Insert<T> insert) {
        String sql = SqlBeanProvider.insertSql(sqlBeanMeta, clazz, insert);
        int count = flexSqlBeanDao.getJdbcTemplate().update(sql);
        return count;
    }

    // ==================== UpdateService 接口实现 ====================

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateById(T bean, ID id) {
        return flexSqlBeanDao.updateById(clazz, bean, id, true, false, null);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateById(T bean, ID id, boolean updateNotNull, boolean optimisticLock) {
        return flexSqlBeanDao.updateById(clazz, bean, id, updateNotNull, optimisticLock, null);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateById(T bean, ID id, boolean updateNotNull, boolean optimisticLock, Column... filterColumns) {
        return flexSqlBeanDao.updateById(clazz, bean, id, updateNotNull, optimisticLock, filterColumns);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public <R> int updateById(T bean, ID id, boolean updateNotNull, boolean optimisticLock, ColumnFun<T, R>... filterColumns) {
        return flexSqlBeanDao.updateById(clazz, bean, id, updateNotNull, optimisticLock, SqlBeanUtil.funToColumn(filterColumns));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateByBeanId(T bean) {
        return flexSqlBeanDao.updateByBeanId(clazz, bean, true, false, null);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateByBeanId(T bean, boolean updateNotNull, boolean optimisticLock) {
        return flexSqlBeanDao.updateByBeanId(clazz, bean, updateNotNull, optimisticLock, null);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateByBeanId(T bean, boolean updateNotNull, boolean optimisticLock, Column... filterColumns) {
        return flexSqlBeanDao.updateByBeanId(clazz, bean, updateNotNull, optimisticLock, filterColumns);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public <R> int updateByBeanId(T bean, boolean updateNotNull, boolean optimisticLock, ColumnFun<T, R>... filterColumns) {
        return flexSqlBeanDao.updateByBeanId(clazz, bean, updateNotNull, optimisticLock, SqlBeanUtil.funToColumn(filterColumns));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateBy(T bean, String where, Object... args) {
        return flexSqlBeanDao.updateByBeanId(clazz, bean, true, false, null);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, String where, Object... args) {
        String sql = SqlBeanProvider.updateBySql(sqlBeanMeta, clazz, bean, updateNotNull, optimisticLock, null, where, args);
        return flexSqlBeanDao.getJdbcTemplate().update(sql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateBy(T bean, Wrapper where) {
        Update<T> update = new Update<>();
        update.bean(bean).notNull(true).optimisticLock(false).where(where);
        return flexSqlBeanDao.update(clazz, update, false);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateBy(T bean, ConditionHandle<T> cond) {
        return this.updateBy(bean, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Wrapper wrapper) {
        Update<T> update = new Update<>();
        update.bean(bean).notNull(updateNotNull).optimisticLock(optimisticLock).where(wrapper);
        return flexSqlBeanDao.update(clazz, update, false);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, ConditionHandle<T> cond) {
        return this.updateBy(bean, updateNotNull, optimisticLock, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Wrapper wrapper, Column... filterColumns) {
        Update<T> update = new Update<>();
        update.bean(bean).notNull(updateNotNull).optimisticLock(optimisticLock).filterFields(filterColumns).where(wrapper);
        return flexSqlBeanDao.update(clazz, update, false);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, ConditionHandle<T> cond, Column... filterColumns) {
        return this.updateBy(bean, updateNotNull, optimisticLock, super.conditionHandle(cond), filterColumns);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public <R> int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Wrapper wrapper, ColumnFun<T, R>... filterColumns) {
        return this.updateBy(bean, updateNotNull, optimisticLock, wrapper, SqlBeanUtil.funToColumn(filterColumns));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public <R> int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, ConditionHandle<T> cond, ColumnFun<T, R>... filterColumns) {
        return this.updateBy(bean, updateNotNull, optimisticLock, super.conditionHandle(cond), SqlBeanUtil.funToColumn(filterColumns));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Column[] filterColumns, String where, Object... args) {
        String sql = SqlBeanProvider.updateBySql(sqlBeanMeta, clazz, bean, updateNotNull, optimisticLock, filterColumns, where, args);
        return flexSqlBeanDao.getJdbcTemplate().update(sql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateByBean(T bean, String where) {
        String sql = SqlBeanProvider.updateByBeanSql(sqlBeanMeta, clazz, bean, true, false, where, null);
        return flexSqlBeanDao.getJdbcTemplate().update(sql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateByBean(T bean, boolean updateNotNull, boolean optimisticLock, String where) {
        String sql = SqlBeanProvider.updateByBeanSql(sqlBeanMeta, clazz, bean, updateNotNull, optimisticLock, where, null);
        return flexSqlBeanDao.getJdbcTemplate().update(sql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int updateByBean(T bean, boolean updateNotNull, boolean optimisticLock, String where, Column... filterColumns) {
        String sql = SqlBeanProvider.updateByBeanSql(sqlBeanMeta, clazz, bean, updateNotNull, optimisticLock, where, filterColumns);
        return flexSqlBeanDao.getJdbcTemplate().update(sql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    @SuppressWarnings("unchecked")
    public <R> int updateByBean(T bean, boolean updateNotNull, boolean optimisticLock, String where, ColumnFun<T, R>[] filterColumns) {
        return flexSqlBeanDao.updateByBeanId(clazz, bean, updateNotNull, optimisticLock, SqlBeanUtil.funToColumn(filterColumns));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int update(Update<T> update) {
        return flexSqlBeanDao.update(clazz, update, false);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int update(Update<T> update, boolean ignore) {
        return flexSqlBeanDao.update(clazz, update, ignore);
    }

    // ==================== DeleteService 接口实现 ====================

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int deleteById(ID... id) {
        if (id == null || id.length == 0) {
            throw new SqlBeanException("deleteById方法id参数至少拥有一个值");
        }
        int count = 0;
        for (ID i : id) {
            count += flexSqlBeanDao.deleteById(clazz, i);
        }
        return count;
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int deleteBy(String where, Object... args) {
        String sql = SqlBeanProvider.deleteBySql(sqlBeanMeta, clazz, where, args);
        return flexSqlBeanDao.getJdbcTemplate().update(sql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int deleteBy(Wrapper where) {
        Delete delete = new Delete();
        delete.where(where);
        return flexSqlBeanDao.delete(clazz, delete, false);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int deleteBy(ConditionHandle<T> cond) {
        return this.deleteBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int delete(Delete delete) {
        return flexSqlBeanDao.delete(clazz, delete, false);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int delete(Delete delete, boolean ignore) {
        return flexSqlBeanDao.delete(clazz, delete, ignore);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int logicallyDeleteById(ID... id) {
        if (id == null || id.length == 0) {
            throw new SqlBeanException("logicallyDeleteById方法id参数至少拥有一个值");
        }
        int count = 0;
        for (ID i : id) {
            count += flexSqlBeanDao.logicallyDeleteById(clazz, i);
        }
        return count;
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int logicallyDeleteBy(String where, Object... args) {
        String sql = SqlBeanProvider.logicallyDeleteBySql(sqlBeanMeta, clazz, where, args);
        return flexSqlBeanDao.getJdbcTemplate().update(sql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int logicallyDeleteBy(Wrapper where) {
        return flexSqlBeanDao.logicallyDeleteByWrapper(clazz, where);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int logicallyDeleteBy(ConditionHandle<T> cond) {
        return this.logicallyDeleteBy(super.conditionHandle(cond));
    }

    // ==================== AdvancedDbManageService 接口实现 ====================

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public void createTable() {
        flexSqlBeanDao.create(clazz);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public void dropTable() {
        flexSqlBeanDao.drop(clazz);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public void dropAndCreateTable() {
        dropTable();
        createTable();
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int alterRemarks(String remarks) {
        if (getSqlBeanMeta().getDbType() == DbType.SQLite || getSqlBeanMeta().getDbType() == DbType.Derby) {
            return 0;
        }
        String sql = SqlBeanProvider.alterRemarksSql(getSqlBeanMeta(), clazz, remarks);
        return flexSqlBeanDao.executeSql(sql);
    }

    @Override
    public List<String> getSchemas(String name) {
        if (getSqlBeanMeta().getDbType() == DbType.SQLite) {
            return null;
        }
        String sql = "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA";
        if (name != null && !name.isEmpty()) {
            sql += " WHERE SCHEMA_NAME LIKE ?";
            return flexSqlBeanDao.getJdbcTemplate().queryForList(sql, String.class, "%" + name + "%");
        }
        return flexSqlBeanDao.getJdbcTemplate().queryForList(sql, String.class);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int createSchema(String name) {
        if (getSqlBeanMeta().getDbType() == DbType.SQLite || getSqlBeanMeta().getDbType() == DbType.Oracle) {
            return 0;
        }
        String sql = "CREATE SCHEMA " + name;
        return flexSqlBeanDao.executeSql(sql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int dropSchema(String name) {
        if (getSqlBeanMeta().getDbType() == DbType.SQLite || getSqlBeanMeta().getDbType() == DbType.Oracle) {
            return 0;
        }
        String sql = "DROP SCHEMA " + name;
        return flexSqlBeanDao.executeSql(sql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int alter(Table table, List<ColumnInfo> columnInfoList) {
        List<String> sqlList = SqlBeanProvider.buildAlterSql(getSqlBeanMeta(), clazz, columnInfoList);
        int count = 0;
        if (sqlList != null && sqlList.size() > 0) {
            for (String sql : sqlList) {
                count += flexSqlBeanDao.executeSql(sql);
            }
        }
        return count;
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int alter(Alter alter) {
        List<Alter> alterList = new ArrayList<>();
        alterList.add(alter);
        return alter(alterList);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int alter(List<Alter> alterList) {
        List<String> sqlList = SqlBeanProvider.alterSql(getSqlBeanMeta().getDbType(), alterList);
        int count = 0;
        if (sqlList != null && sqlList.size() > 0) {
            for (String sql : sqlList) {
                count += flexSqlBeanDao.executeSql(sql);
            }
        }
        return count;
    }

    // ==================== DbManageService 接口实现 ====================

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public String backup() {
        String targetTableName = SqlBeanUtil.getTable(clazz).getName() + "_" + System.currentTimeMillis();
        String sql = "CREATE TABLE " + targetTableName + " AS SELECT * FROM " + SqlBeanUtil.getTable(clazz).getName();
        flexSqlBeanDao.executeSql(sql);
        return targetTableName;
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public void backup(String targetTableName) {
        String sql = "CREATE TABLE " + targetTableName + " AS SELECT * FROM " + SqlBeanUtil.getTable(clazz).getName();
        flexSqlBeanDao.executeSql(sql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public void backup(String targetSchema, String targetTableName) {
        String tableName = SqlBeanUtil.getTable(clazz).getName();
        String schema = SqlBeanUtil.getTable(clazz).getSchema();
        String fromTable = (schema != null ? schema + "." : "") + tableName;
        String toTable = (targetSchema != null ? targetSchema + "." : "") + targetTableName;
        String sql = "CREATE TABLE " + toTable + " AS SELECT * FROM " + fromTable;
        flexSqlBeanDao.executeSql(sql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public void backup(Wrapper wrapper, String targetSchema, String targetTableName) {
        // 简化实现：创建表结构，然后插入数据
        String tableName = SqlBeanUtil.getTable(clazz).getName();
        String schema = SqlBeanUtil.getTable(clazz).getSchema();
        String fromTable = (schema != null ? schema + "." : "") + tableName;
        String toTable = (targetSchema != null ? targetSchema + "." : "") + targetTableName;

        // 创建表结构
        String createSql = "CREATE TABLE " + toTable + " LIKE " + fromTable;
        flexSqlBeanDao.executeSql(createSql);

        // 构建查询条件
        Select select = new Select();
        select.where(wrapper);
        String selectSql = SqlBeanProvider.selectSql(sqlBeanMeta, clazz, clazz, select);
        String insertSql = "INSERT INTO " + toTable + " " + selectSql;
        flexSqlBeanDao.executeSql(insertSql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public void backup(ConditionHandle<T> cond, String targetSchema, String targetTableName) {
        this.backup(super.conditionHandle(cond), targetSchema, targetTableName);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public void backup(Wrapper wrapper, String targetTableName, Column... columns) {
        String tableName = SqlBeanUtil.getTable(clazz).getName();
        String toTable = targetTableName;
        String fromTable = tableName;

        // 创建表结构
        String createSql = "CREATE TABLE " + toTable + " LIKE " + fromTable;
        flexSqlBeanDao.executeSql(createSql);

        // 构建查询条件
        Select select = new Select();
        select.where(wrapper);
        if (columns != null && columns.length > 0) {
            select.column(columns);
        }
        String selectSql = SqlBeanProvider.selectSql(sqlBeanMeta, clazz, clazz, select);
        String insertSql = "INSERT INTO " + toTable + " " + selectSql;
        flexSqlBeanDao.executeSql(insertSql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public void backup(ConditionHandle<T> cond, String targetTableName, Column... columns) {
        this.backup(super.conditionHandle(cond), targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public <R> void backup(Wrapper wrapper, String targetTableName, ColumnFun<T, R>... columns) {
        this.backup(wrapper, targetTableName, SqlBeanUtil.funToColumn(columns));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public <R> void backup(ConditionHandle<T> cond, String targetTableName, ColumnFun<T, R>... columns) {
        this.backup(cond, targetTableName, SqlBeanUtil.funToColumn(columns));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public void backup(Wrapper wrapper, String targetSchema, String targetTableName, Column... columns) {
        String tableName = SqlBeanUtil.getTable(clazz).getName();
        String schema = SqlBeanUtil.getTable(clazz).getSchema();
        String fromTable = (schema != null ? schema + "." : "") + tableName;
        String toTable = (targetSchema != null ? targetSchema + "." : "") + targetTableName;

        // 创建表结构
        String createSql = "CREATE TABLE " + toTable + " LIKE " + fromTable;
        flexSqlBeanDao.executeSql(createSql);

        // 构建查询条件
        Select select = new Select();
        select.where(wrapper);
        if (columns != null && columns.length > 0) {
            select.column(columns);
        }
        String selectSql = SqlBeanProvider.selectSql(sqlBeanMeta, clazz, clazz, select);
        String insertSql = "INSERT INTO " + toTable + " " + selectSql;
        flexSqlBeanDao.executeSql(insertSql);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public void backup(ConditionHandle<T> cond, String targetSchema, String targetTableName, Column... columns) {
        this.backup(cond, targetSchema, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public <R> void backup(Wrapper wrapper, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        this.backup(wrapper, targetSchema, targetTableName, SqlBeanUtil.funToColumn(columns));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public <R> void backup(ConditionHandle<T> cond, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        this.backup(cond, targetSchema, targetTableName, SqlBeanUtil.funToColumn(columns));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int copy(Wrapper wrapper, String targetTableName) {
        return flexSqlBeanDao.copy(clazz, wrapper, null, targetTableName, null);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int copy(ConditionHandle<T> cond, String targetTableName) {
        return this.copy(super.conditionHandle(cond), targetTableName);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int copy(Wrapper wrapper, String targetSchema, String targetTableName) {
        return flexSqlBeanDao.copy(clazz, wrapper, targetSchema, targetTableName, null);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int copy(ConditionHandle<T> cond, String targetSchema, String targetTableName) {
        return this.copy(super.conditionHandle(cond), targetSchema, targetTableName);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int copy(Wrapper wrapper, String targetTableName, Column... columns) {
        return flexSqlBeanDao.copy(clazz, wrapper, null, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int copy(ConditionHandle<T> cond, String targetTableName, Column... columns) {
        return this.copy(super.conditionHandle(cond), targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public <R> int copy(Wrapper wrapper, String targetTableName, ColumnFun<T, R>... columns) {
        return flexSqlBeanDao.copy(clazz, wrapper, null, targetTableName, SqlBeanUtil.funToColumn(columns));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public <R> int copy(ConditionHandle<T> cond, String targetTableName, ColumnFun<T, R>... columns) {
        return this.copy(super.conditionHandle(cond), targetTableName, SqlBeanUtil.funToColumn(columns));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int copy(Wrapper wrapper, String targetSchema, String targetTableName, Column... columns) {
        return flexSqlBeanDao.copy(clazz, wrapper, targetSchema, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public int copy(ConditionHandle<T> cond, String targetSchema, String targetTableName, Column... columns) {
        return this.copy(super.conditionHandle(cond), targetSchema, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public <R> int copy(Wrapper wrapper, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        return flexSqlBeanDao.copy(clazz, wrapper, targetSchema, targetTableName, SqlBeanUtil.funToColumn(columns));
    }

    @DbSwitch(DbRole.MASTER)
    
    @Override
    public <R> int copy(ConditionHandle<T> cond, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        return this.copy(super.conditionHandle(cond), targetSchema, targetTableName, SqlBeanUtil.funToColumn(columns));
    }

    @Override
    public List<TableInfo> getTableList() {
        return this.getTableList(SqlBeanUtil.getTable(clazz).getSchema(), null);
    }

    @Override
    public List<TableInfo> getTableList(String tableName) {
        return this.getTableList(SqlBeanUtil.getTable(clazz).getSchema(), tableName);
    }

    @Override
    public List<TableInfo> getTableList(String schema, String tableName) {
        // 需要查询数据库元数据实现，这里简化处理
        return new ArrayList<>();
    }

    @Override
    public List<ColumnInfo> getColumnInfoList() {
        Table table = SqlBeanUtil.getTable(clazz);
        return this.getColumnInfoList(table.getSchema(), table.getName());
    }

    @Override
    public List<ColumnInfo> getColumnInfoList(String tableName) {
        return this.getColumnInfoList(SqlBeanUtil.getTable(clazz).getSchema(), tableName);
    }

    @Override
    public List<ColumnInfo> getColumnInfoList(String schema, String tableName) {
        // 需要查询数据库元数据实现，这里简化处理
        List<ColumnInfo> columnInfoList = new ArrayList<>();
        super.handleColumnInfo(columnInfoList);
        return columnInfoList;
    }

    // ==================== Setter ====================

    public void setSqlBeanMeta(SqlBeanMeta sqlBeanMeta) {
        this.sqlBeanMeta = sqlBeanMeta;
        if (flexSqlBeanDao == null) {
            this.flexSqlBeanDao = new FlexSqlBeanDao<>(sqlBeanMeta);
        }
    }

    public void setFlexSqlBeanDao(FlexSqlBeanDao<T> flexSqlBeanDao) {
        this.flexSqlBeanDao = flexSqlBeanDao;
        if (flexSqlBeanDao != null) {
            this.sqlBeanMeta = flexSqlBeanDao.getSqlBeanMeta();
        }
    }

    /**
     * 设置 JdbcTemplate
     * 允许注入框架特定的 JdbcTemplate（如 SpringFlexJdbcTemplate）
     *
     * @param jdbcTemplate JdbcTemplate
     */
    public void setJdbcTemplate(cn.vonce.sql.java.datasource.FlexJdbcTemplate jdbcTemplate) {
        if (flexSqlBeanDao != null) {
            flexSqlBeanDao.setJdbcTemplate(jdbcTemplate);
        }
    }

}