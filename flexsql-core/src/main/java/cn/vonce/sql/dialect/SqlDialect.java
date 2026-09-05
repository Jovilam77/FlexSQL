package cn.vonce.sql.dialect;

import cn.vonce.sql.bean.Alter;
import cn.vonce.sql.bean.Cte;
import cn.vonce.sql.bean.Select;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.enumerate.JdbcType;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.helper.SqlHelper;
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

    /**
     * 构建行锁子句（在 ORDER BY 与 LIMIT/OFFSET 之后追加）
     * <p>
     * 例如 MySQL 8.0+ / PostgreSQL 9.5+ 的 FOR UPDATE SKIP LOCKED。
     * 默认不追加；由支持行锁的方言（MySQL、PostgreSQL）重写实现。
     * 仅在非 count 查询时由 SQL 构建器调用。
     *
     * @param sqlSb  SQL构建器
     * @param select 查询对象
     */
    default void appendLockClause(StringBuilder sqlSb, Select select) {
    }

    /**
     * 构建表级锁提示（紧跟 FROM / JOIN 后的表名之后追加）
     * <p>
     * 例如 SQL Server 的 WITH (UPDLOCK[, READPAST]) 表提示。
     * 与 appendLockClause（尾部子句，用于 MySQL/PostgreSQL/Oracle）不同，
     * 某些数据库（如 SQL Server）的行锁必须作为表提示紧贴表名，
     * 因此单独提供此钩子，由需要表级提示的方言重写实现，其余方言保持默认 no-op。
     * 仅在非 count 查询、且已设置行锁时由 SQL 构建器调用。
     *
     * @param sqlSb  SQL构建器
     * @param select 查询对象
     */
    default void appendTableHint(StringBuilder sqlSb, Select select) {
    }

    /**
     * 是否使用该方言的 RECURSIVE 关键字
     * <p>
     * MySQL / PostgreSQL / SQLite / H2 等需要在 WITH 之后显式写出 RECURSIVE；
     * Oracle / SQL Server / DB2 的递归 CTE 不需要（也不支持）该关键字，重写为 false。
     * 默认返回 true。
     *
     * @return 是否输出 WITH RECURSIVE
     */
    default boolean useRecursiveKeyword() {
        return true;
    }

    /**
     * 构建 CTE（WITH 子句）前缀，必须位于 SELECT 关键字之前、分页外层包裹之前插入。
     * <p>
     * 生成形如：WITH [RECURSIVE] name [(col,...)] AS (subquery) [, name2 AS (subquery)]。
     * 子查询可以是 Select 形式（交由 SqlHelper 递归构建，继承当前方言）或原生 SQL。
     * 仅在 select 含有 CTE 时由 SQL 构建器调用。默认实现为标准语法，各库一般无需重写。
     *
     * @param sqlSb  SQL构建器
     * @param select 查询对象
     */
    default void appendCtePrefix(StringBuilder sqlSb, Select select) {
        List<Cte> ctes = select.getCtes();
        if (ctes == null || ctes.isEmpty()) {
            return;
        }
        StringBuilder cteSb = new StringBuilder();
        cteSb.append("WITH ");
        if (select.isRecursive() && useRecursiveKeyword()) {
            cteSb.append("RECURSIVE ");
        }
        for (int i = 0; i < ctes.size(); i++) {
            Cte cte = ctes.get(i);
            if (i > 0) {
                cteSb.append(", ");
            }
            cteSb.append(cte.getName());
            List<String> cols = cte.getColumns();
            if (cols != null && !cols.isEmpty()) {
                cteSb.append(" (").append(String.join(", ", cols)).append(")");
            }
            cteSb.append(" AS (");
            Select sub = cte.getSubSelect();
            if (sub != null) {
                if (sub.getSqlBeanMeta() == null) {
                    sub.setSqlBeanMeta(select.getSqlBeanMeta());
                }
                cteSb.append(SqlHelper.buildSelectSql(sub));
            } else {
                cteSb.append(cte.getRawSql());
            }
            cteSb.append(")");
        }
        cteSb.append(" ");
        // 前置于 0：保证 CTE 位于 SELECT 关键字之前，且在 SQL Server 分页外层包裹（SELECT ALL FROM (）之外
        sqlSb.insert(0, cteSb);
    }

}
