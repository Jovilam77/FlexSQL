package cn.vonce.sql.spring.service;

import cn.vonce.sql.bean.*;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.define.ColumnFun;
import cn.vonce.sql.define.ConditionHandle;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.helper.Wrapper;
import cn.vonce.sql.java.annotation.DbSwitch;
import cn.vonce.sql.java.annotation.DbTransactional;
import cn.vonce.sql.java.enumerate.DbRole;
import cn.vonce.sql.java.service.BaseSqlBeanServiceImpl;
import cn.vonce.sql.page.PageHelper;
import cn.vonce.sql.page.ResultData;
import cn.vonce.sql.service.AdvancedDbManageService;
import cn.vonce.sql.spring.config.UseSpringJdbc;
import cn.vonce.sql.spring.mapper.SpringJdbcSqlBeanMapper;
import cn.vonce.sql.provider.SqlBeanProvider;
import cn.vonce.sql.service.SqlBeanService;
import cn.vonce.sql.uitls.DateUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

/**
 * Spring环境的SpringJdbc实现
 *
 * @param <T>
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2019年5月22日下午16:20:12
 */
@UseSpringJdbc
@Service
public class SpringJdbcSqlBeanServiceImpl<T, ID> extends BaseSqlBeanServiceImpl<T> implements SqlBeanService<T, ID>, AdvancedDbManageService<T> {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Class<?> clazz;

    @Autowired
    @Qualifier("sqlBeanMetaForSpringJdbc")
    private SqlBeanMeta sqlBeanMeta;

    public SpringJdbcSqlBeanServiceImpl() {
        List<Class<?>> classes = SqlBeanUtil.getGenericTypeBySuperclass(this.getClass());
        if (!classes.isEmpty()) {
            this.clazz = classes.get(0);
        } else {
            this.clazz = null;
        }
    }

    @Override
    public SqlBeanMeta getSqlBeanMeta() {
        return sqlBeanMeta;
    }

    @Override
    public Class<?> getBeanClass() {
        return clazz;
    }

    /**
     * 从 GeneratedKeyHolder 取出生成的自增主键（按行 1:1）
     */
    private List<Long> generatedKeys(GeneratedKeyHolder keyHolder) {
        List<Long> keys = new ArrayList<>();
        for (Map<String, Object> row : keyHolder.getKeyList()) {
            Object v = row.isEmpty() ? null : row.values().iterator().next();
            keys.add(v == null ? null : ((Number) v).longValue());
        }
        return keys;
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public T selectById(ID id) {
        if (id == null) {
            return null;
        }
        return singleResult(jdbcTemplate.query(SqlBeanProvider.selectByIdSql(getSqlBeanMeta(), clazz, null, id), new SpringJdbcSqlBeanMapper<T>(clazz, clazz)));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectById(Class<R> returnType, ID id) {
        if (id == null) {
            return null;
        }
        return singleResult(jdbcTemplate.query(SqlBeanProvider.selectByIdSql(getSqlBeanMeta(), clazz, returnType, id), new SpringJdbcSqlBeanMapper<R>(clazz, returnType)));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectByIds(ID... ids) {
        if (ids == null || ids.length == 0) {
            throw new SqlBeanException("selectByIds方法ids参数必须拥有一个值");
        }
        return jdbcTemplate.query(SqlBeanProvider.selectByIdsSql(getSqlBeanMeta(), clazz, null, ids), new SpringJdbcSqlBeanMapper<T>(clazz, clazz));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectByIds(Class<R> returnType, ID... ids) {
        if (ids == null || ids.length == 0) {
            throw new SqlBeanException("selectByIds方法ids参数必须拥有一个值");
        }
        return jdbcTemplate.query(SqlBeanProvider.selectByIdsSql(getSqlBeanMeta(), clazz, returnType, ids), new SpringJdbcSqlBeanMapper<R>(clazz, returnType));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public T selectOne(Select select) {
        return singleResult(jdbcTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SpringJdbcSqlBeanMapper<T>(clazz, clazz)));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectOne(Class<R> returnType, Select select) {
        return singleResult(jdbcTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, returnType, select), new SpringJdbcSqlBeanMapper<R>(clazz, returnType)));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public Map<String, Object> selectMap(Select select) {
        return singleResult(jdbcTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SpringJdbcSqlBeanMapper<Map<String, Object>>(clazz, Map.class)));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public T selectOneBy(String where, Object... args) {
        return singleResult(jdbcTemplate.query(SqlBeanProvider.selectBySql(getSqlBeanMeta(), clazz, null, null, where, args), new SpringJdbcSqlBeanMapper<T>(clazz, clazz)));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectOneBy(Class<R> returnType, String where, Object... args) {
        return singleResult(jdbcTemplate.query(SqlBeanProvider.selectBySql(getSqlBeanMeta(), clazz, returnType, null, where, args), new SpringJdbcSqlBeanMapper<>(clazz, returnType)));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public T selectOneBy(Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        return singleResult(jdbcTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SpringJdbcSqlBeanMapper<>(clazz, clazz)));
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
        return singleResult(jdbcTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, returnType, select), new SpringJdbcSqlBeanMapper<>(clazz, returnType)));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> R selectOneBy(Class<R> returnType, ConditionHandle<T> cond) {
        return this.selectOneBy(returnType, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, String where, Object... args) {
        return jdbcTemplate.query(SqlBeanProvider.selectBySql(getSqlBeanMeta(), clazz, returnType, null, where, args), new SpringJdbcSqlBeanMapper<>(clazz, returnType));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        return jdbcTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, returnType, select), new SpringJdbcSqlBeanMapper<>(clazz, returnType));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, ConditionHandle<T> cond) {
        return this.selectBy(returnType, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, Paging paging, String where, Object... args) {
        return jdbcTemplate.query(SqlBeanProvider.selectBySql(getSqlBeanMeta(), clazz, returnType, paging, where, args), new SpringJdbcSqlBeanMapper<>(clazz, returnType));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, Paging paging, Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        select.page(paging.getPagenum(), paging.getPagesize(), paging.getStartByZero());
        select.orderBy(paging.getOrders());
        return jdbcTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, returnType, select), new SpringJdbcSqlBeanMapper<>(clazz, returnType));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> selectBy(Class<R> returnType, Paging paging, ConditionHandle<T> cond) {
        return this.selectBy(returnType, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(String where, Object... args) {
        return jdbcTemplate.query(SqlBeanProvider.selectBySql(getSqlBeanMeta(), clazz, null, null, where, args), new SpringJdbcSqlBeanMapper<>(clazz, clazz));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        return jdbcTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SpringJdbcSqlBeanMapper<>(clazz, clazz));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(ConditionHandle<T> cond) {
        return this.selectBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(Paging paging, String where, Object... args) {
        return jdbcTemplate.query(SqlBeanProvider.selectBySql(getSqlBeanMeta(), clazz, null, paging, where, args), new SpringJdbcSqlBeanMapper<>(clazz, clazz));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(Paging paging, Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        select.page(paging.getPagenum(), paging.getPagesize(), paging.getStartByZero());
        select.orderBy(paging.getOrders());
        return jdbcTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SpringJdbcSqlBeanMapper<>(clazz, clazz));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> selectBy(Paging paging, ConditionHandle<T> cond) {
        return this.selectBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int countBy(String where, Object... args) {
        return jdbcTemplate.queryForObject(SqlBeanProvider.countBySql(getSqlBeanMeta(), clazz, where, args), new SpringJdbcSqlBeanMapper<>(clazz, Integer.class));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int countBy(Wrapper wrapper) {
        Select select = new Select();
        select.where(wrapper);
        return jdbcTemplate.queryForObject(SqlBeanProvider.countSql(getSqlBeanMeta(), clazz, null, select), new SpringJdbcSqlBeanMapper<>(clazz, Integer.class));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int countBy(ConditionHandle<T> cond) {
        return this.countBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int count() {
        return jdbcTemplate.queryForObject(SqlBeanProvider.countBySql(getSqlBeanMeta(), clazz, null, null), new SpringJdbcSqlBeanMapper<>(clazz, Integer.class));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> select() {
        return jdbcTemplate.query(SqlBeanProvider.selectAllSql(getSqlBeanMeta(), clazz, null, null), new SpringJdbcSqlBeanMapper<T>(clazz, clazz));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> select(Paging paging) {
        return jdbcTemplate.query(SqlBeanProvider.selectAllSql(getSqlBeanMeta(), clazz, null, paging), new SpringJdbcSqlBeanMapper<T>(clazz, clazz));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> select(Class<R> returnType) {
        return jdbcTemplate.query(SqlBeanProvider.selectAllSql(getSqlBeanMeta(), clazz, returnType, null), new SpringJdbcSqlBeanMapper<R>(clazz, returnType));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> select(Class<R> returnType, Paging paging) {
        return jdbcTemplate.query(SqlBeanProvider.selectAllSql(getSqlBeanMeta(), clazz, returnType, paging), new SpringJdbcSqlBeanMapper<R>(clazz, returnType));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<Map<String, Object>> selectMapList(Select select) {
        return jdbcTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SpringJdbcSqlBeanMapper<Map<String, Object>>(clazz, Map.class));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public <R> List<R> select(Class<R> returnType, Select select) {
        return jdbcTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, returnType, select), new SpringJdbcSqlBeanMapper<R>(clazz, returnType));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public List<T> select(Select select) {
        return jdbcTemplate.query(SqlBeanProvider.selectSql(getSqlBeanMeta(), clazz, null, select), new SpringJdbcSqlBeanMapper<T>(clazz, clazz));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int count(Select select) {
        return jdbcTemplate.queryForObject(SqlBeanProvider.countSql(getSqlBeanMeta(), clazz, null, select), new SpringJdbcSqlBeanMapper<Integer>(clazz, Integer.class));
    }

    @DbSwitch(DbRole.SLAVE)
    @Override
    public int count(Class<?> returnType, Select select) {
        return jdbcTemplate.queryForObject(SqlBeanProvider.countSql(getSqlBeanMeta(), clazz, returnType, select), Integer.class);
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
            throw new SqlBeanException("deleteById方法id参数必须拥有一个值");
        }
        return jdbcTemplate.update(SqlBeanProvider.deleteByIdSql(getSqlBeanMeta(), clazz, id));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int deleteBy(String where, Object... args) {
        return jdbcTemplate.update(SqlBeanProvider.deleteBySql(getSqlBeanMeta(), clazz, where, args));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int deleteBy(Wrapper wrapper) {
        Delete delete = new Delete();
        delete.where(wrapper);
        return jdbcTemplate.update(SqlBeanProvider.deleteSql(getSqlBeanMeta(), clazz, delete, false));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int deleteBy(ConditionHandle<T> cond) {
        return this.deleteBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int delete(Delete delete) {
        return jdbcTemplate.update(SqlBeanProvider.deleteSql(getSqlBeanMeta(), clazz, delete, false));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int delete(Delete delete, boolean ignore) {
        return jdbcTemplate.update(SqlBeanProvider.deleteSql(getSqlBeanMeta(), clazz, delete, ignore));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int logicallyDeleteById(ID... id) {
        if (id == null || id.length == 0) {
            throw new SqlBeanException("logicallyDeleteById方法id参数必须拥有一个值");
        }
        return jdbcTemplate.update(SqlBeanProvider.logicallyDeleteByIdSql(getSqlBeanMeta(), clazz, id));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int logicallyDeleteBy(String where, Object... args) {
        return jdbcTemplate.update(SqlBeanProvider.logicallyDeleteBySql(getSqlBeanMeta(), clazz, where, args));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int logicallyDeleteBy(Wrapper wrapper) {
        return jdbcTemplate.update(SqlBeanProvider.logicallyDeleteBySql(getSqlBeanMeta(), clazz, wrapper));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int logicallyDeleteBy(ConditionHandle<T> cond) {
        return this.logicallyDeleteBy(super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int update(Update update) {
        return jdbcTemplate.update(SqlBeanProvider.updateSql(getSqlBeanMeta(), clazz, update, false));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int update(Update update, boolean ignore) {
        return jdbcTemplate.update(SqlBeanProvider.updateSql(getSqlBeanMeta(), clazz, update, ignore));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateById(T bean, ID id) {
        return jdbcTemplate.update(SqlBeanProvider.updateByIdSql(getSqlBeanMeta(), clazz, bean, id, true, false, null));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateById(T bean, ID id, boolean updateNotNull, boolean optimisticLock) {
        return jdbcTemplate.update(SqlBeanProvider.updateByIdSql(getSqlBeanMeta(), clazz, bean, id, updateNotNull, optimisticLock, null));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateByBeanId(T bean) {
        return jdbcTemplate.update(SqlBeanProvider.updateByBeanIdSql(getSqlBeanMeta(), clazz, bean, true, false, null));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateById(T bean, ID id, boolean updateNotNull, boolean optimisticLock, Column... filterColumns) {
        return jdbcTemplate.update(SqlBeanProvider.updateByIdSql(getSqlBeanMeta(), clazz, bean, id, updateNotNull, optimisticLock, filterColumns));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> int updateById(T bean, ID id, boolean updateNotNull, boolean optimisticLock, ColumnFun<T, R>... filterColumns) {
        return jdbcTemplate.update(SqlBeanProvider.updateByIdSql(getSqlBeanMeta(), clazz, bean, id, updateNotNull, optimisticLock, SqlBeanUtil.funToColumn(filterColumns)));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, String where, Object... args) {
        return jdbcTemplate.update(SqlBeanProvider.updateBySql(getSqlBeanMeta(), clazz, bean, true, false, null, where, args));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateByBeanId(T bean, boolean updateNotNull, boolean optimisticLock) {
        return jdbcTemplate.update(SqlBeanProvider.updateByBeanIdSql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, null));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateByBeanId(T bean, boolean updateNotNull, boolean optimisticLock, Column... filterColumns) {
        return jdbcTemplate.update(SqlBeanProvider.updateByBeanIdSql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, filterColumns));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> int updateByBeanId(T bean, boolean updateNotNull, boolean optimisticLock, ColumnFun<T, R>... filterColumns) {
        return jdbcTemplate.update(SqlBeanProvider.updateByBeanIdSql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, SqlBeanUtil.funToColumn(filterColumns)));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, String where, Object... args) {
        return jdbcTemplate.update(SqlBeanProvider.updateBySql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, null, where, args));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, Wrapper wrapper) {
        Update update = new Update();
        update.bean(bean).notNull(true).optimisticLock(false).where(wrapper);
        return jdbcTemplate.update(SqlBeanProvider.updateSql(getSqlBeanMeta(), clazz, update, false));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, ConditionHandle<T> cond) {
        return this.updateBy(bean, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Wrapper wrapper) {
        Update update = new Update();
        update.bean(bean).notNull(updateNotNull).optimisticLock(optimisticLock).where(wrapper);
        return jdbcTemplate.update(SqlBeanProvider.updateSql(getSqlBeanMeta(), clazz, update, false));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, ConditionHandle<T> cond) {
        return this.updateBy(bean, updateNotNull, optimisticLock, super.conditionHandle(cond));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Column[] filterColumns, String where, Object... args) {
        return jdbcTemplate.update(SqlBeanProvider.updateBySql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, filterColumns, where, args));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateBy(T bean, boolean updateNotNull, boolean optimisticLock, Wrapper wrapper, Column... filterColumns) {
        Update update = new Update();
        update.bean(bean).notNull(updateNotNull).optimisticLock(optimisticLock).filterFields(filterColumns).where(wrapper);
        return jdbcTemplate.update(SqlBeanProvider.updateSql(getSqlBeanMeta(), clazz, update, false));
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
    public int updateByBean(T bean, String where) {
        return jdbcTemplate.update(SqlBeanProvider.updateByBeanSql(getSqlBeanMeta(), clazz, bean, true, false, where, null));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateByBean(T bean, boolean updateNotNull, boolean optimisticLock, String where) {
        return jdbcTemplate.update(SqlBeanProvider.updateByBeanSql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, where, null));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int updateByBean(T bean, boolean updateNotNull, boolean optimisticLock, String where, Column... filterColumns) {
        return jdbcTemplate.update(SqlBeanProvider.updateByBeanSql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, where, filterColumns));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> int updateByBean(T bean, boolean updateNotNull, boolean optimisticLock, String where, ColumnFun<T, R>... filterColumns) {
        return jdbcTemplate.update(SqlBeanProvider.updateByBeanSql(getSqlBeanMeta(), clazz, bean, updateNotNull, optimisticLock, where, SqlBeanUtil.funToColumn(filterColumns)));
    }

    @Transactional
    @DbTransactional
    @DbSwitch(DbRole.MASTER)
    @Override
    public int insert(T... bean) {
        if (bean == null || bean.length == 0) {
            throw new SqlBeanException("insert方法bean参数必须拥有一个值");
        }
        String sql = SqlBeanProvider.insertBeanSql(getSqlBeanMeta(), clazz, bean);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int count = jdbcTemplate.update((PreparedStatementCreator) con -> con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keyHolder);
        super.setAutoIncrId(clazz, Arrays.asList(bean), generatedKeys(keyHolder));
        return count;
    }

    @Transactional
    @DbTransactional
    @DbSwitch(DbRole.MASTER)
    @Override
    public int insert(Collection<T> beanList) {
        if (beanList == null || beanList.size() == 0) {
            throw new SqlBeanException("insert方法beanList参数至少拥有一个值");
        }
        String sql = SqlBeanProvider.insertBeanSql(getSqlBeanMeta(), clazz, beanList);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int count = jdbcTemplate.update((PreparedStatementCreator) con -> con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keyHolder);
        super.setAutoIncrId(clazz, beanList, generatedKeys(keyHolder));
        return count;
    }

    @Transactional
    @DbTransactional
    @DbSwitch(DbRole.MASTER)
    @Override
    public int insert(Insert insert) {
        String sql = SqlBeanProvider.insertBeanSql(getSqlBeanMeta(), clazz, insert);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int count = jdbcTemplate.update((PreparedStatementCreator) con -> con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keyHolder);
        super.setAutoIncrId(clazz, insert.getBean(), generatedKeys(keyHolder));
        return count;
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public String backup() {
        String targetTableName = SqlBeanUtil.getTable(clazz).getName() + "_" + DateUtil.dateToString(new Date(), "yyyyMMddHHmmssSSS");
        jdbcTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, null, null, targetTableName, null));
        return targetTableName;
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(String targetTableName) {
        jdbcTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, null, null, targetTableName, null));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(String targetSchema, String targetTableName) {
        jdbcTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, null, targetSchema, targetTableName, null));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(Wrapper wrapper, String targetSchema, String targetTableName) {
        jdbcTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, null));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(ConditionHandle<T> cond, String targetSchema, String targetTableName) {
        this.backup(super.conditionHandle(cond), targetSchema, targetTableName);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(Wrapper wrapper, String targetTableName, Column... columns) {
        jdbcTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, columns));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(ConditionHandle<T> cond, String targetTableName, Column... columns) {
        this.backup(super.conditionHandle(cond), targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> void backup(Wrapper wrapper, String targetTableName, ColumnFun<T, R>... columns) {
        jdbcTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, SqlBeanUtil.funToColumn(columns)));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> void backup(ConditionHandle<T> cond, String targetTableName, ColumnFun<T, R>... columns) {
        this.backup(super.conditionHandle(cond), targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(Wrapper wrapper, String targetSchema, String targetTableName, Column... columns) {
        jdbcTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, columns));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void backup(ConditionHandle<T> cond, String targetSchema, String targetTableName, Column... columns) {
        this.backup(super.conditionHandle(cond), targetSchema, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> void backup(Wrapper wrapper, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        jdbcTemplate.update(SqlBeanProvider.backupSql(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, SqlBeanUtil.funToColumn(columns)));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> void backup(ConditionHandle<T> cond, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        this.backup(super.conditionHandle(cond), targetSchema, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(Wrapper wrapper, String targetTableName) {
        return jdbcTemplate.update(SqlBeanProvider.copySql(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, null));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(ConditionHandle<T> cond, String targetTableName) {
        return this.copy(super.conditionHandle(cond), targetTableName);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(Wrapper wrapper, String targetSchema, String targetTableName) {
        return jdbcTemplate.update(SqlBeanProvider.copySql(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, null));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(ConditionHandle<T> cond, String targetSchema, String targetTableName) {
        return this.copy(super.conditionHandle(cond), targetSchema, targetTableName);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(Wrapper wrapper, String targetTableName, Column... columns) {
        return jdbcTemplate.update(SqlBeanProvider.copySql(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, columns));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(ConditionHandle<T> cond, String targetTableName, Column... columns) {
        return this.copy(super.conditionHandle(cond), targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> int copy(Wrapper wrapper, String targetTableName, ColumnFun<T, R>... columns) {
        return jdbcTemplate.update(SqlBeanProvider.copySql(getSqlBeanMeta(), clazz, wrapper, null, targetTableName, SqlBeanUtil.funToColumn(columns)));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> int copy(ConditionHandle<T> cond, String targetTableName, ColumnFun<T, R>... columns) {
        return this.copy(super.conditionHandle(cond), targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(Wrapper wrapper, String targetSchema, String targetTableName, Column... columns) {
        return jdbcTemplate.update(SqlBeanProvider.copySql(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, columns));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int copy(ConditionHandle<T> cond, String targetSchema, String targetTableName, Column... columns) {
        return this.copy(super.conditionHandle(cond), targetSchema, targetTableName, columns);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public <R> int copy(Wrapper wrapper, String targetSchema, String targetTableName, ColumnFun<T, R>... columns) {
        return jdbcTemplate.update(SqlBeanProvider.copySql(getSqlBeanMeta(), clazz, wrapper, targetSchema, targetTableName, SqlBeanUtil.funToColumn(columns)));
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
                count += jdbcTemplate.update(sql);
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
                count += jdbcTemplate.update(sql);
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
        return jdbcTemplate.update(sql);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public List<String> getSchemas(String name) {
        if (getSqlBeanMeta().getDbType() == DbType.SQLite) {
            return null;
        }
        String sql = SqlBeanProvider.databaseSql(getSqlBeanMeta(), name);
        return jdbcTemplate.query(sql, new SpringJdbcSqlBeanMapper<>(clazz, String.class));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int createSchema(String name) {
        if (getSqlBeanMeta().getDbType() == DbType.SQLite || getSqlBeanMeta().getDbType() == DbType.Oracle) {
            return 0;
        }
        String sql = SqlBeanProvider.createSchemaSql(getSqlBeanMeta(), name);
        return jdbcTemplate.update(sql);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public int dropSchema(String name) {
        if (getSqlBeanMeta().getDbType() == DbType.SQLite || getSqlBeanMeta().getDbType() == DbType.Oracle) {
            return 0;
        }
        String sql = SqlBeanProvider.dropSchemaSql(getSqlBeanMeta(), name);
        return jdbcTemplate.update(sql);
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void dropTable() {
        SqlBeanMeta sqlBeanMeta = getSqlBeanMeta();
        if (sqlBeanMeta.getDbType() != DbType.MySQL && sqlBeanMeta.getDbType() != DbType.MariaDB && sqlBeanMeta.getDbType() != DbType.Postgresql && sqlBeanMeta.getDbType() != DbType.SQLServer && sqlBeanMeta.getDbType() != DbType.H2) {
            List<TableInfo> nameList = jdbcTemplate.queryForList(SqlBeanProvider.selectTableListSql(sqlBeanMeta, SqlBeanUtil.getTable(clazz).getSchema(), SqlBeanUtil.getTable(clazz).getName()), TableInfo.class);
            if (nameList == null || nameList.isEmpty()) {
                return;
            }
        }
        jdbcTemplate.update(SqlBeanProvider.dropTableSql(sqlBeanMeta, clazz));
    }

    @DbSwitch(DbRole.MASTER)
    @Override
    public void createTable() {
        jdbcTemplate.update(SqlBeanProvider.createTableSql(getSqlBeanMeta(), clazz));
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
        return jdbcTemplate.query(SqlBeanProvider.selectTableListSql(getSqlBeanMeta(), schema, tableName), new SpringJdbcSqlBeanMapper<TableInfo>(TableInfo.class, TableInfo.class));
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
        List<ColumnInfo> columnInfoList = jdbcTemplate.query(SqlBeanProvider.selectColumnListSql(getSqlBeanMeta(), schema, tableName), new SpringJdbcSqlBeanMapper<ColumnInfo>(ColumnInfo.class, ColumnInfo.class));
        super.handleColumnInfo(columnInfoList);
        return columnInfoList;
    }

    private static <T> T singleResult(Collection<T> results) {
        int size = results != null ? results.size() : 0;
        if (size == 0) {
            return null;
        } else if (results.size() > 1) {
            throw new SqlBeanException("执行返回的结果不止一条, size: " + results.size());
        } else {
            return results.iterator().next();
        }
    }

}
