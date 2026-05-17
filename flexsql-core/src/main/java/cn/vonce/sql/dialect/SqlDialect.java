package cn.vonce.sql.dialect;

import cn.vonce.sql.bean.Alter;
import cn.vonce.sql.bean.Select;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.enumerate.JdbcType;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.uitls.SqlBeanUtil;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Sql方言
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/4/16 9:32
 */
public interface SqlDialect<T> {

    /**
     * 获取字段对应的Jdbc类型
     *
     * @param field
     * @return
     */
    T getType(Field field);

    /**
     * 获取字段对应的Jdbc类型
     *
     * @param field
     * @return
     */
    JdbcType getJdbcType(Field field);

    /**
     * 查询表信息sql
     *
     * @param sqlBeanMeta
     * @param schema
     * @param tableName
     * @return
     */
    String getTableListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName);

    /**
     * 查询列信息sql
     *
     * @param sqlBeanMeta
     * @param schema
     * @param tableName
     * @return
     */
    String getColumnListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName);

    /**
     * 更改表结构sql
     *
     * @param alterList
     * @return
     */
    List<String> alterTable(List<Alter> alterList);

    /**
     * 更改注释sql
     *
     * @param isTable
     * @param item
     * @param escape
     * @return
     */
    String addRemarks(boolean isTable, Alter item, String escape);

    /**
     * 获取模式列表sql
     *
     * @param sqlBeanMeta
     * @param schemaName
     * @return
     */
    String getSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName);

    /**
     * 创建模式sql
     *
     * @param sqlBeanMeta
     * @param schemaName
     * @return
     */
    String getCreateSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName);

    /**
     * 删除模式sql
     *
     * @param sqlBeanMeta
     * @param schemaName
     * @return
     */
    String getDropSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName);

    /**
     * 获取schema名称并进行安全校验
     * <p>
     * 校验机制：只允许字母、数字、下划线、$和#字符，防止SQL注入。
     * 所有方言的 getSchemaSql / getCreateSchemaSql / getDropSchemaSql
     * 均通过此方法获取名称，一处加固即覆盖所有注入入口。
     *
     * @param sqlBeanMeta
     * @param schemaName
     * @return
     * @throws SqlBeanException 当schemaName包含非法字符时抛出
     */
    default String getSchemaName(SqlBeanMeta sqlBeanMeta, String schemaName) {
        if (schemaName == null) {
            return null;
        }
        String name = SqlBeanUtil.isToUpperCase(sqlBeanMeta) ? schemaName.toUpperCase() : schemaName;
        // 校验：只允许字母、数字、下划线、$、# 等安全标识符字符
        if (!name.matches("[a-zA-Z_$#][a-zA-Z0-9_$#]*")) {
            throw new SqlBeanException("Schema name contains invalid characters: " + schemaName);
        }
        return name;
    }

    /**
     * 构建分页SQL前缀（在SELECT关键字之前插入）
     * 用于SQL Server的 SELECT ALL FROM ( 外层包裹
     *
     * @param sqlSb     SQL构建器
     * @param select    查询对象
     * @param orderSql  排序语句
     * @param pageParam 分页参数[offset/startIndex, limit/endIndex]
     */
    default void appendPageBeforePrefix(StringBuilder sqlSb, Select select, String orderSql, Integer[] pageParam) {
    }

    /**
     * 构建分页SELECT列前缀（在SELECT关键字之后、列字段列表之前插入）
     * 用于SQL Server的 TOP n ROW_NUMBER() OVER(order) AS rownum,
     *
     * @param sqlSb     SQL构建器
     * @param select    查询对象
     * @param orderSql  排序语句
     * @param pageParam 分页参数[offset/startIndex, limit/endIndex]
     */
    default void appendPageAfterSelect(StringBuilder sqlSb, Select select, String orderSql, Integer[] pageParam) {
    }

    /**
     * 构建分页SQL后缀（在完整SQL构建完成后调用）
     * 用于追加 LIMIT/OFFSET 或包裹已有SQL（Oracle/DB2嵌套查询）
     *
     * @param sqlSb     SQL构建器
     * @param select    查询对象
     * @param orderSql  排序语句
     * @param pageParam 分页参数[offset/startIndex, limit/endIndex]
     */
    default void appendPageSuffix(StringBuilder sqlSb, Select select, String orderSql, Integer[] pageParam) {
    }

}
