package cn.vonce.sql.solon.service;

import cn.vonce.sql.bean.*;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.define.ColumnFun;
import cn.vonce.sql.define.ConditionHandle;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.helper.Wrapper;
import cn.vonce.sql.java.annotation.DbSwitch;
import cn.vonce.sql.java.annotation.DbTransactional;
import cn.vonce.sql.java.dao.MybatisSqlBeanDao;
import cn.vonce.sql.java.enumerate.DbRole;
import cn.vonce.sql.java.service.BaseSqlBeanServiceImpl;
import cn.vonce.sql.page.PageHelper;
import cn.vonce.sql.page.ResultData;
import cn.vonce.sql.provider.SqlBeanProvider;
import cn.vonce.sql.service.AdvancedDbManageService;
import cn.vonce.sql.service.SqlBeanService;
import cn.vonce.sql.uitls.DateUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;
import org.apache.ibatis.solon.annotation.Db;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.data.annotation.Tran;

import java.util.*;

/**
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/8/9 15:03
 */
@Component
public class MybatisSqlBeanServiceImpl<T, ID> extends BaseSqlBeanServiceImpl<T> implements SqlBeanService<T, ID>, AdvancedDbManageService<T> {

    @Db
    private MybatisSqlBeanDao<T> mybatisSqlBeanDao;

    @Inject
    private SqlBeanMeta sqlBeanMeta;

    private final Class<?> clazz;

    public MybatisSqlBeanServiceImpl() {
        List<Class<?>> classes = SqlBeanUtil.getGenericTypeBySuperclass(this.getClass());
        if (!classes.isEmpty()) {
            this.clazz = classes.get(0);
        } else {
            this.clazz = null;
        }
    }

    @Override
    public SqlBeanMeta getSqlBeanMeta() {
        return super.setSqlBeanMeta(sqlBeanMeta);
    }

    @Override
    public Long getAutoIncrId() {
        if (getSqlBeanMeta().getDbType() == DbType.MySQL || getSqlBeanMeta().getDbType() == DbType.MariaDB) {
            return mybatisSqlBeanDao.lastInsertId();
        }
        return null;
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
        return mybatisSqlBeanDao.selectById(getSqlBeanMeta(), clazz, id);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectById(Class<R> returnType, ID id) {
        if (id == null) {
            return null;
        }
        return mybatisSqlBeanDao.selectByIdO(getSqlBeanMeta(), clazz, returnType, id);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectByIds(ID... ids) {
        if (ids == null || ids.length == 0) {
            throw new SqlBeanException("selectByIds方法ids参数至少拥有一个值");
        }
        return mybatisSqlBeanDao.selectByIds(getSqlBeanMeta(), clazz, ids);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectByIds(Class<R> returnType, ID... ids) {
        if (ids == null || ids.length == 0) {
            throw new SqlBeanException("selectByIds方法ids参数至少拥有一个值");
        }
        return mybatisSqlBeanDao.selectByIdsO(getSqlBeanMeta(), clazz, returnType, ids);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public T selectOne(Select select) {
        return mybatisSqlBeanDao.selectOne(getSqlBeanMeta(), clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectOne(Class<R> returnType, Select select) {
        return mybatisSqlBeanDao.selectOneO(getSqlBeanMeta(), clazz, returnType, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public Map<String, Object> selectMap(Select select) {
        return mybatisSqlBeanDao.selectMap(getSqlBeanMeta(), clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public T selectOneBy(String where, Object... args) {
        return mybatisSqlBeanDao.selectOneBy(getSqlBeanMeta(), clazz, where, args);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectOneBy(Class<R> returnType, String where, Object... args) {
        return mybatisSqlBeanDao.selectOneByO(getSqlBeanMeta(), clazz, returnType, where, args);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public T selectOneBy(Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        return mybatisSqlBeanDao.selectOne(getSqlBeanMeta(), clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public T selectOneBy(ConditionHandle<T> cond) {
        return this.selectOneBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectOneBy(Class<R> returnType, Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        return mybatisSqlBeanDao.selectOneO(getSqlBeanMeta(), clazz, returnType, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectOneBy(Class<R> returnType, ConditionHandle<T> cond) {
        return this.selectOneBy(returnType, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, String where, Object... args) {
        return mybatisSqlBeanDao.selectByO(getSqlBeanMeta(), clazz, returnType, null, where, args);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        return mybatisSqlBeanDao.selectO(getSqlBeanMeta(), clazz, returnType, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, ConditionHandle<T> cond) {
        return this.selectBy(returnType, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, Paging paging, String where, Object... args) {
        return mybatisSqlBeanDao.selectByO(getSqlBeanMeta(), clazz, returnType, paging, where, args);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, Paging paging, Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        select.page(paging.getPagenum(), paging.getPagesize(), paging.getStartByZero());
        select.orderBy(paging.getOrders());
        return mybatisSqlBeanDao.selectO(getSqlBeanMeta(), clazz, returnType, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, Paging paging, ConditionHandle<T> cond) {
        return this.selectBy(returnType, paging, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(String where, Object... args) {
        return mybatisSqlBeanDao.selectBy(getSqlBeanMeta(), clazz, null, where, args);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        return mybatisSqlBeanDao.select(getSqlBeanMeta(), clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(ConditionHandle<T> cond) {
        return this.selectBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(Paging paging, String where, Object... args) {
        return mybatisSqlBeanDao.selectBy(getSqlBeanMeta(), clazz, paging, where, args);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(Paging paging, Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        select.page(paging.getPagenum(), paging.getPagesize(), paging.getStartByZero());
        select.orderBy(paging.getOrders());
        return mybatisSqlBeanDao.select(getSqlBeanMeta(), clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(Paging paging, ConditionHandle<T> cond) {
        return this.selectBy(paging, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int countBy(String where, Object... args) {
        return mybatisSqlBeanDao.countBy(getSqlBeanMeta(), clazz, where, args);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int countBy(Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        return mybatisSqlBeanDao.count(getSqlBeanMeta(), clazz, null, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int countBy(ConditionHandle<T> cond) {
        return this.countBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int count() {
        return mybatisSqlBeanDao.countBy(getSqlBeanMeta(), clazz, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public List<T> select() {
        return mybatisSqlBeanDao.selectAll(getSqlBeanMeta(), clazz, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public List<T> select(Paging paging) {
        return mybatisSqlBeanDao.selectAll(getSqlBeanMeta(), clazz, paging);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> List<R> select(Class<R> returnType) {
        return mybatisSqlBeanDao.selectAllO(getSqlBeanMeta(), clazz, returnType, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> List<R> select(Class<R> returnType, Paging paging) {
        return mybatisSqlBeanDao.selectAllO(getSqlBeanMeta(), clazz, returnType, paging);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<Map<String, Object>> selectMapList(Select select) {
        return mybatisSqlBeanDao.selectMapList(getSqlBeanMeta(), clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> select(Class<R> returnType, Select select) {
        return mybatisSqlBeanDao.selectO(getSqlBeanMeta(), clazz, returnType, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> select(Select select) {
        return mybatisSqlBeanDao.select(getSqlBeanMeta(), clazz, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int count(Select select) {
        return mybatisSqlBeanDao.count(getSqlBeanMeta(), clazz, null, select);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int count(Class<?> returnType, Select select) {
        return mybatisSqlBeanDao.count(getSqlBeanMeta(), clazz, returnType, select);
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

    @DbSwitch(DbRole.MASTER)
    @Override
    public int deleteById(ID... id) {
        if (id == null || id.length == 0) {
            throw new SqlBeanException("deleteById方法id参数至少拥有一个值");
        }
        return mybatisSqlBeanDao.deleteById(getSqlBeanMeta(), clazz, id);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int deleteBy(String where, Object... args) {
        return mybatisSqlBeanDao.deleteBy(getSqlBeanMeta(), clazz, where, args);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int deleteBy(Wrapper wrapper) {
        Delete delete = new Delete();
        delete.where(wrapper);
        return mybatisSqlBeanDao.delete(getSqlBeanMeta(), clazz, delete, false);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int deleteBy(ConditionHandle<T> cond) {
        return this.deleteBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int delete(Delete delete) {
        return mybatisSqlBeanDao.delete(getSqlBeanMeta(), clazz, delete, false);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int delete(Delete delete, boolean ignore) {
        return mybatisSqlBeanDao.delete(getSqlBeanMeta(), clazz, delete, ignore);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int logicallyDeleteById(ID... id) {
        if (id == null || id.length == 0) {
            throw new SqlBeanException("logicallyDeleteById方法id参数至少拥有一个值");
        }
        return mybatisSqlBeanDao.logicallyDeleteById(getSqlBeanMeta(), clazz, id);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int logicallyDeleteBy(String where, Object... args) {
        return mybatisSqlBeanDao.logicallyDeleteBy(getSqlBeanMeta(), clazz, where, args);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int logicallyDeleteBy(Wrapper wrapper) {
        return mybatisSqlBeanDao.logicallyDeleteByWrapper(getSqlBeanMeta(), clazz, wrapper);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int logicallyDeleteBy(ConditionHandle<T> cond) {
        return this.logicallyDeleteBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int update(Update<T> update) {
        return mybatisSqlBeanDao.update(getSqlBeanMeta(), clazz, update, false);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int update(Update<T> update, boolean ignore) {
        return mybatisSqlBeanDao.update(getSqlBeanMeta(), clazz, update, ignore);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateById(T bean, ID id) {
        return mybatisSqlBeanDao.updateById(getSqlBeanMeta(), clazz, bean, id, true, false, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateById(T bean, ID id, boolean updateNotNull, boolean optimisticLock) {
        return mybatisSqlBeanDao.updateById(getSqlBeanMeta(), clazz, bean, id, updateNotNull, optimisticLock, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateByBeanId(T bean) {
        return mybatisSqlBeanDao.updateByBeanId(getSqlBeanMeta(), clazz, bean, true, false, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateById(T bean, ID id, boolean updateNotNull, boolean optimisticLock, Column... filterColumns) {
        return mybatisSqlBeanDao.updateById(getSqlBeanMeta(), clazz, bean, id, updateNotNull, optimisticLock, filterColumns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> int updateById(T bean, ID id, boolean updateNotNull, boolean optimisticLock, ColumnFun<T, R>... filterColumns) {
        return mybatisSqlBeanDao.updateById(getSqlBeanMeta(), clazz, bean, id, updateNotNull, optimisticLock, SqlBeanUtil.funToColumn(filterColumns));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, String where, Object... args) {
        return mybatisSqlBeanDao.updateByBeanId(getSqlBeanMeta(), clazz, bean, true, false, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateByBeanId(T bean, boolean updateNotNull, boolean optimisticLock) {
        return mybatisSqlBeanDao.updateByBeanId(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateByBeanId(T bean, boolean updateNotNull, boolean optimisticLock, Column... filterColumns) {
        return mybatisSqlBeanDao.updateByBeanId(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, filterColumns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> int updateByBeanId(T bean, boolean updateNotNull, boolean optimisticLock, ColumnFun<T, R>... filterColumns) {
        return mybatisSqlBeanDao.updateByBeanId(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, SqlBeanUtil.funToColumn(filterColumns));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, String where, Object... args) {
        return mybatisSqlBeanDao.updateBy(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, null, where, args);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, Wrapper wrapper) {
        Update update = new Update();
        update.bean(bean).notNull(true).optimisticLock(false).where(wrapper);
        return mybatisSqlBeanDao.update(getSqlBeanMeta(), clazz, update, false);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, ConditionHandle<T> cond) {
        return this.updateBy(bean, true, false, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Wrapper wrapper) {
        Update update = new Update();
        update.bean(bean).notNull(updateNotNull).optimisticLock(optimisticLock).where(wrapper);
        return mybatisSqlBeanDao.update(getSqlBeanMeta(), clazz, update, false);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, ConditionHandle<T> cond) {
        return this.updateBy(bean, updateNotNull, optimisticLock, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Column[] filterColumns, String where, Object... args) {
        return mybatisSqlBeanDao.updateBy(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, filterColumns, where, args);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Wrapper wrapper, Column... filterColumns) {
        Update update = new Update();
        update.bean(bean).notNull(updateNotNull).optimisticLock(optimisticLock).filterFields(filterColumns).where(wrapper);
        return mybatisSqlBeanDao.update(getSqlBeanMeta(), clazz, update, false);
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
        return this.updateBy(bean, updateNotNull, optimisticLock, super.conditionHandle(cond), filterColumns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateByBean(T bean, String where) {
        return mybatisSqlBeanDao.updateByBean(getSqlBeanMeta(), clazz, bean, true, false, where, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateByBean(T bean, boolean updateNotNull, boolean optimisticLock, String where) {
        return mybatisSqlBeanDao.updateByBean(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, where, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateByBean(T bean, boolean updateNotNull, boolean optimisticLock, String where, Column... filterColumns) {
        return mybatisSqlBeanDao.updateByBean(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, where, filterColumns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> int updateByBean(T bean, boolean updateNotNull, boolean optimisticLock, String where, ColumnFun<T, R>... filterColumns) {
        return mybatisSqlBeanDao.updateByBean(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, where, SqlBeanUtil.funToColumn(filterColumns));
    }

    @Tran
    @DbTransactional
    @DbSwitch(DbRole.MASTER)
    @Override
    public int insert(T... bean) {
        if (bean == null || bean.length == 0) {
            throw new SqlBeanException("insert方法bean参数至少拥有一个值");
        }
        List<T> beanList = Arrays.asList(bean);
        int count = mybatisSqlBeanDao.insertBean(getSqlBeanMeta(), clazz, beanList);
        super.setAutoIncrId(clazz, beanList);
        return count;
    }

    @Tran
    @DbTransactional
    @DbSwitch(DbRole.MASTER)
    @Override
    public int insert(Collection<T> beanList) {
        if (beanList == null || beanList.size() == 0) {
            throw new SqlBeanException("insert方法beanList参数至少拥有一个值");
        }
        int count = mybatisSqlBeanDao.insertBean(getSqlBeanMeta(), clazz, beanList);
        super.setAutoIncrId(clazz, beanList);
        return count;
    }

    @Tran
    @DbTransactional
    @DbSwitch(DbRole.MASTER)
    @Override
    public int insert(Insert<T> insert) {
        int count = mybatisSqlBeanDao.insert(getSqlBeanMeta(), clazz, insert);
        super.setAutoIncrId(clazz, insert.getBean());
        return count;
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public String backup() {
        String targetTableName = SqlBeanUtil.getTable(clazz).getName() + "_" + DateUtil.dateToString(new Date(), "yyyyMMddHHmmssSSS");
        mybatisSqlBeanDao.backup(getSqlBeanMeta(), clazz, null, null, targetTableName, null);
        return targetTableName;
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(String targetTableName) {
        mybatisSqlBeanDao.backup(getSqlBeanMeta(), clazz, null, null, targetTableName, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(String targetSchema, String targetTableName) {
        mybatisSqlBeanDao.backup(getSqlBeanMeta(), clazz, null, targetSchema, targetTableName, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(Wrapper wrapper, String targetSchema, String targetTableName) {
        mybatisSqlBeanDao.backup(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(ConditionHandle<T> cond, String targetSchema, String targetTableName) {
        this.backup(super.conditionHandle(cond), targetSchema, targetTableName);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(Wrapper wrapper, String targetTableName, Column... columns) {
        mybatisSqlBeanDao.backup(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(ConditionHandle<T> cond, String targetTableName, Column... columns) {
        this.backup(super.conditionHandle(cond), targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> void backup(Wrapper wrapper, String targetTableName, ColumnFun<T, R>... columns) {
        mybatisSqlBeanDao.backup(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, SqlBeanUtil.funToColumn(columns));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> void backup(ConditionHandle<T> cond, String targetTableName, ColumnFun<T, R>... columns) {
        this.backup(super.conditionHandle(cond), targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(Wrapper wrapper, String targetSchema, String targetTableName, Column... columns) {
        mybatisSqlBeanDao.backup(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(ConditionHandle<T> cond, String targetSchema, String targetTableName, Column... columns) {
        this.backup(super.conditionHandle(cond), targetSchema, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> void backup(Wrapper wrapper, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        mybatisSqlBeanDao.backup(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, SqlBeanUtil.funToColumn(columns));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> void backup(ConditionHandle<T> cond, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        this.backup(super.conditionHandle(cond), targetSchema, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(Wrapper wrapper, String targetTableName) {
        return mybatisSqlBeanDao.copy(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(ConditionHandle<T> cond, String targetTableName) {
        return this.copy(super.conditionHandle(cond), targetTableName);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(Wrapper wrapper, String targetSchema, String targetTableName) {
        return mybatisSqlBeanDao.copy(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, null);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(ConditionHandle<T> cond, String targetSchema, String targetTableName) {
        return this.copy(super.conditionHandle(cond), targetSchema, targetTableName);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(Wrapper wrapper, String targetTableName, Column... columns) {
        return mybatisSqlBeanDao.copy(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(ConditionHandle<T> cond, String targetTableName, Column... columns) {
        return this.copy(super.conditionHandle(cond), targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> int copy(Wrapper wrapper, String targetTableName, ColumnFun<T, R>... columns) {
        return mybatisSqlBeanDao.copy(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, SqlBeanUtil.funToColumn(columns));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> int copy(ConditionHandle<T> cond, String targetTableName, ColumnFun<T, R>... columns) {
        return this.copy(super.conditionHandle(cond), targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(Wrapper wrapper, String targetSchema, String targetTableName, Column... columns) {
        return mybatisSqlBeanDao.copy(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(ConditionHandle<T> cond, String targetSchema, String targetTableName, Column... columns) {
        return this.copy(super.conditionHandle(cond), targetSchema, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> int copy(Wrapper wrapper, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        return mybatisSqlBeanDao.copy(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, SqlBeanUtil.funToColumn(columns));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> int copy(ConditionHandle<T> cond, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        return this.copy(super.conditionHandle(cond), targetSchema, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int alter(Table table, List<ColumnInfo> columnInfoList) {
        List<String> sqlList = SqlBeanProvider.buildAlterSql(getSqlBeanMeta(), clazz, columnInfoList);
        int count = 0;
        if (sqlList != null && sqlList.size() > 0) {
            for (String sql : sqlList) {
                count += mybatisSqlBeanDao.executeSql(sql);
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
                count += mybatisSqlBeanDao.executeSql(sql);
            }
        }
        return count;
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int alterRemarks(String remarks) {
        if (getSqlBeanMeta().getDbType() == DbType.SQLite || getSqlBeanMeta().getDbType() == DbType.Derby) {
            return 0;
        }
        String sql = SqlBeanProvider.alterRemarksSql(getSqlBeanMeta(), clazz, remarks);
        return mybatisSqlBeanDao.executeSql(sql);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public List<String> getSchemas(String name) {
        if (getSqlBeanMeta().getDbType() == DbType.SQLite) {
            return null;
        }
        return mybatisSqlBeanDao.databases(getSqlBeanMeta(), name);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int createSchema(String name) {
        if (getSqlBeanMeta().getDbType() == DbType.SQLite || getSqlBeanMeta().getDbType() == DbType.Oracle) {
            return 0;
        }
        return mybatisSqlBeanDao.createSchema(getSqlBeanMeta(), name);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int dropSchema(String name) {
        if (getSqlBeanMeta().getDbType() == DbType.SQLite || getSqlBeanMeta().getDbType() == DbType.Oracle) {
            return 0;
        }
        return mybatisSqlBeanDao.dropSchema(getSqlBeanMeta(), name);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void dropTable() {
        SqlBeanMeta sqlBeanMate = getSqlBeanMeta();
        if (sqlBeanMate.getDbType() != DbType.MySQL && sqlBeanMate.getDbType() != DbType.MariaDB && sqlBeanMate.getDbType() != DbType.Postgresql && sqlBeanMate.getDbType() != DbType.SQLServer && sqlBeanMate.getDbType() != DbType.H2) {
            List<TableInfo> nameList = mybatisSqlBeanDao.selectTableList(getSqlBeanMeta(), SqlBeanUtil.getTable(clazz).getSchema(), SqlBeanUtil.getTable(clazz).getName());
            if (nameList == null || nameList.isEmpty()) {
                return;
            }
        }
        mybatisSqlBeanDao.drop(sqlBeanMate, clazz);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void createTable() {
        mybatisSqlBeanDao.create(getSqlBeanMeta(), clazz);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void dropAndCreateTable() {
        dropTable();
        createTable();
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<TableInfo> getTableList() {
        return this.getTableList(SqlBeanUtil.getTable(clazz).getSchema(), null);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<TableInfo> getTableList(String tableName) {
        return this.getTableList(SqlBeanUtil.getTable(clazz).getSchema(), tableName);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<TableInfo> getTableList(String schema, String tableName) {
        return mybatisSqlBeanDao.selectTableList(getSqlBeanMeta(), schema, tableName);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<ColumnInfo> getColumnInfoList() {
        Table table = SqlBeanUtil.getTable(clazz);
        return this.getColumnInfoList(table.getSchema(), table.getName());
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<ColumnInfo> getColumnInfoList(String tableName) {
        return this.getColumnInfoList(SqlBeanUtil.getTable(clazz).getSchema(), tableName);
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<ColumnInfo> getColumnInfoList(String schema, String tableName) {
        List<ColumnInfo> columnInfoList = mybatisSqlBeanDao.selectColumnInfoList(getSqlBeanMeta(), schema, tableName);
        super.handleColumnInfo(columnInfoList);
        return columnInfoList;
    }
}
