package cn.vonce.sql.java.provider;

import cn.vonce.sql.bean.*;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.helper.Wrapper;
import cn.vonce.sql.provider.SqlBeanProvider;

import java.util.Map;

/**
 * 通用的数据库操作sql语句生成
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2018年5月15日下午2:23:47
 */
public class MybatisSqlBeanProvider {

    /**
     * 根据id条件查询
     *
     * @param map
     * @return
     */
    public String selectById(Map<String, Object> map) {
        Class<?> returnType = null;
        if (map.containsKey("returnType")) {
            returnType = (Class<?>) map.get("returnType");
        }
        return SqlBeanProvider.selectByIdSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), returnType, map.get("id"));
    }

    /**
     * 根据id条件查询
     *
     * @param map
     * @return
     */
    public String selectByIds(Map<String, Object> map) {
        Class<?> returnType = null;
        if (map.containsKey("returnType")) {
            returnType = (Class<?>) map.get("returnType");
        }
        return SqlBeanProvider.selectByIdsSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), returnType, (Object[]) map.get("ids"));
    }

    /**
     * 根据条件查询
     *
     * @param map
     * @return
     */
    public String selectBy(Map<String, Object> map) {
        Paging paging = null;
        Class<?> returnType = null;
        if (map.containsKey("paging")) {
            paging = (Paging) map.get("paging");
        }
        if (map.containsKey("returnType")) {
            returnType = (Class<?>) map.get("returnType");
        }
        Object[] args = null;
        if (map.containsKey("args")) {
            args = (Object[]) map.get("args");
        }
        return SqlBeanProvider.selectBySql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), returnType, paging, (String) map.get("where"), args);
    }

    /**
     * 根据条件查询统计
     *
     * @param map
     * @return
     */
    public String countBy(Map<String, Object> map) {
        Object[] args = null;
        if (map.containsKey("args")) {
            args = (Object[]) map.get("args");
        }
        return SqlBeanProvider.countBySql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), (String) map.get("where"), args);
    }

    /**
     * 查询全部
     *
     * @param map
     * @return
     */
    public String selectAll(Map<String, Object> map) {
        Paging paging = null;
        Class<?> returnType = null;
        if (map.containsKey("paging")) {
            paging = (Paging) map.get("paging");
        }
        if (map.containsKey("returnType")) {
            returnType = (Class<?>) map.get("returnType");
        }
        return SqlBeanProvider.selectAllSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), returnType, paging);
    }

    /**
     * 根据自定义条件查询（可自动分页）
     *
     * @param map
     * @return
     */
    public String select(Map<String, Object> map) {
        Class<?> returnType = null;
        if (map.containsKey("returnType")) {
            returnType = (Class<?>) map.get("returnType");
        }
        return SqlBeanProvider.selectSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), returnType, (Select) map.get("select"));
    }

    /**
     * 根据自定义条件统计
     *
     * @param map
     * @return
     */
    public String count(Map<String, Object> map) {
        Class<?> returnType = null;
        if (map.containsKey("returnType")) {
            returnType = (Class<?>) map.get("returnType");
        }
        return SqlBeanProvider.countSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), returnType, (Select) map.get("select"));
    }

    /**
     * 根据id条件删除
     *
     * @param map
     * @return
     */
    public String deleteById(Map<String, Object> map) {
        return SqlBeanProvider.deleteByIdSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), map.get("id"));
    }

    /**
     * 根据条件删除
     *
     * @param map
     * @return
     */
    public String deleteBy(Map<String, Object> map) {
        Object[] args = null;
        if (map.containsKey("args")) {
            args = (Object[]) map.get("args");
        }
        return SqlBeanProvider.deleteBySql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), (String) map.get("where"), args);
    }

    /**
     * 删除
     *
     * @param map
     * @return
     */
    public String delete(Map<String, Object> map) {
        return SqlBeanProvider.deleteSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), (Delete) map.get("delete"), (boolean) map.get("ignore"));
    }

    /**
     * 逻辑删除
     *
     * @param map
     * @return
     */
    public String logicallyDeleteById(Map<String, Object> map) {
        return SqlBeanProvider.logicallyDeleteByIdSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), map.get("id"));
    }

    /**
     * 根据条件逻辑删除
     *
     * @param map
     * @return
     */
    public String logicallyDeleteBy(Map<String, Object> map) {
        Object[] args = null;
        if (map.containsKey("args")) {
            args = (Object[]) map.get("args");
        }
        return SqlBeanProvider.logicallyDeleteBySql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), (String) map.get("where"), args);
    }

    /**
     * 根据条件逻辑删除
     *
     * @param map
     * @return
     */
    public String logicallyDeleteByWrapper(Map<String, Object> map) {
        return SqlBeanProvider.logicallyDeleteBySql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), (Wrapper) map.get("wrapper"));
    }

    /**
     * 更新
     *
     * @param map
     * @return
     */
    public String update(Map<String, Object> map) {
        return SqlBeanProvider.updateSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), (Update) map.get("update"), (boolean) map.get("ignore"));
    }

    /**
     * 根据id条件更新
     *
     * @param map
     * @return
     */
    public String updateById(Map<String, Object> map) {
        return SqlBeanProvider.updateByIdSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), map.get("bean"), map.get("id"), (boolean) map.get("updateNotNull"), (boolean) map.get("optimisticLock"), (Column[]) map.get("filterColumns"));
    }

    /**
     * 根据实体类id条件更新
     *
     * @param map
     * @return
     */
    public String updateByBeanId(Map<String, Object> map) {
        return SqlBeanProvider.updateByBeanIdSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), map.get("bean"), (boolean) map.get("updateNotNull"), (boolean) map.get("optimisticLock"), (Column[]) map.get("filterColumns"));
    }

    /**
     * 根据条件更新
     *
     * @param map
     * @return
     */
    public String updateBy(Map<String, Object> map) {
        Object[] args = null;
        if (map.containsKey("args")) {
            args = (Object[]) map.get("args");
        }
        return SqlBeanProvider.updateBySql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), map.get("bean"), (boolean) map.get("updateNotNull"), (boolean) map.get("optimisticLock"), (Column[]) map.get("filterColumns"), (String) map.get("where"), args);
    }

    /**
     * 根据实体类字段条件更新
     *
     * @param map
     * @return
     */
    public String updateByBean(Map<String, Object> map) {
        return SqlBeanProvider.updateByBeanSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), map.get("bean"), (boolean) map.get("updateNotNull"), (boolean) map.get("optimisticLock"), (String) map.get("where"), (Column[]) map.get("filterColumns"));
    }

    /**
     * 插入数据
     *
     * @param map
     * @return
     */
    public String insertBean(Map<String, Object> map) {
        return SqlBeanProvider.insertBeanSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), map.get("beanList"));
    }

    /**
     * 插入数据
     *
     * @param map
     * @return
     */
    public String insert(Map<String, Object> map) {
        return SqlBeanProvider.insertSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), (Insert) map.get("insert"));
    }

    /**
     * 删除表
     *
     * @param map
     * @return
     */
    public String drop(Map<String, Object> map) {
        return SqlBeanProvider.dropTableSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"));
    }

    /**
     * 创建表
     *
     * @param map
     * @return
     */
    public String create(Map<String, Object> map) {
        return SqlBeanProvider.createTableSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"));
    }

    /**
     * 获取表名列表
     *
     * @param map
     * @return
     */
    public String selectTableList(Map<String, Object> map) {
        return SqlBeanProvider.selectTableListSql((SqlBeanMeta) map.get("sqlBeanMeta"), (String) map.get("schema"), (String) map.get("name"));
    }

    /**
     * 获取列信息列表
     *
     * @param map
     * @return
     */
    public String selectColumnInfoList(Map<String, Object> map) {
        return SqlBeanProvider.selectColumnListSql((SqlBeanMeta) map.get("sqlBeanMeta"), (String) map.get("schema"), (String) map.get("name"));
    }

    /**
     * 备份表和数据
     *
     * @param map
     * @return
     */
    public String backup(Map<String, Object> map) {
        return SqlBeanProvider.backupSql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), (Wrapper) map.get("wrapper"), (String) map.get("targetSchema"), (String) map.get("targetTableName"), (Column[]) map.get("columns"));
    }

    /**
     * 复制数据到指定表
     *
     * @param map
     * @return
     */
    public String copy(Map<String, Object> map) {
        return SqlBeanProvider.copySql((SqlBeanMeta) map.get("sqlBeanMeta"), (Class<?>) map.get("clazz"), (Wrapper) map.get("wrapper"), (String) map.get("targetSchema"), (String) map.get("targetTableName"), (Column[]) map.get("columns"));
    }

    /**
     * 执行Sql
     *
     * @param sql
     * @return
     */
    public String executeSql(String sql) {
        return sql;
    }

    /**
     * 复制数据到指定表
     *
     * @param map
     * @return
     */
    public String databases(Map<String, Object> map) {
        return SqlBeanProvider.databaseSql((SqlBeanMeta) map.get("sqlBeanMeta"), (String) map.get("name"));
    }

    /**
     * 复制数据到指定表
     *
     * @param map
     * @return
     */
    public String createSchema(Map<String, Object> map) {
        return SqlBeanProvider.createSchemaSql((SqlBeanMeta) map.get("sqlBeanMeta"), (String) map.get("name"));
    }

    /**
     * 复制数据到指定表
     *
     * @param map
     * @return
     */
    public String dropSchema(Map<String, Object> map) {
        return SqlBeanProvider.dropSchemaSql((SqlBeanMeta) map.get("sqlBeanMeta"), (String) map.get("name"));
    }

}
