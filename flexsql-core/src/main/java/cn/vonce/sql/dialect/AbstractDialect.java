package cn.vonce.sql.dialect;

import cn.vonce.sql.bean.Alter;
import cn.vonce.sql.bean.Common;
import cn.vonce.sql.bean.Table;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.JdbcType;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.lang.reflect.Field;

/**
 * 方言抽象基类
 * <p>
 * 提供各方言实现共用的辅助方法，减少重复代码。
 * 子类需要实现 getType()、getTableListSql()、getColumnListSql()、alterTable()、
 * getSchemaSql()、getCreateSchemaSql()、getDropSchemaSql() 等方言特有方法。
 *
 * @param <T> 方言类型枚举
 * @author Jovi
 * @email imjovi@qq.com
 */
public abstract class AbstractDialect<T extends Enum<T>> implements SqlDialect<T> {

    @Override
    public JdbcType getJdbcType(Field field) {
        return JdbcType.getType(getType(field).name());
    }

    /**
     * 获取全名（schema.table 转义形式）
     *
     * @param common
     * @param table
     * @return
     */
    protected String getFullName(Common common, Table table) {
        String escape = SqlBeanUtil.getEscape(common);
        boolean toUpperCase = SqlBeanUtil.isToUpperCase(common);
        StringBuilder sql = new StringBuilder();
        if (StringUtil.isNotBlank(table.getSchema())) {
            sql.append(escape);
            sql.append(table.getSchema(toUpperCase));
            sql.append(escape);
            sql.append(SqlConstant.POINT);
        }
        sql.append(escape);
        sql.append(table.getName(toUpperCase));
        sql.append(escape);
        sql.append(SqlConstant.SPACES);
        return sql.toString();
    }

    /**
     * 更改字段名
     *
     * @param alter
     * @return
     */
    protected String changeColumn(Alter alter) {
        StringBuilder changeSql = new StringBuilder();
        changeSql.append(SqlConstant.ALTER_TABLE);
        changeSql.append(getFullName(alter, alter.getTable()));
        changeSql.append(SqlConstant.RENAME);
        changeSql.append(SqlConstant.COLUMN);
        changeSql.append(alter.getOldColumnName(SqlBeanUtil.isToUpperCase(alter)));
        changeSql.append(SqlConstant.TO);
        changeSql.append(alter.getColumnInfo().getName(SqlBeanUtil.isToUpperCase(alter)));
        return changeSql.toString();
    }

    @Override
    public String addRemarks(boolean isTable, Alter item, String escape) {
        StringBuilder remarksSql = new StringBuilder();
        remarksSql.append(SqlConstant.COMMENT);
        remarksSql.append(SqlConstant.ON);
        remarksSql.append(isTable ? SqlConstant.TABLE : SqlConstant.COLUMN);
        remarksSql.append(getFullName(item, item.getTable()));
        if (!isTable) {
            remarksSql.append(SqlConstant.POINT);
            remarksSql.append(escape);
            remarksSql.append(item.getColumnInfo().getName());
            remarksSql.append(escape);
        }
        remarksSql.append(SqlConstant.IS);
        remarksSql.append(SqlConstant.SINGLE_QUOTATION_MARK);
        remarksSql.append(StringUtil.isNotBlank(item.getColumnInfo().getRemarks()) ? item.getColumnInfo().getRemarks() : "''");
        remarksSql.append(SqlConstant.SINGLE_QUOTATION_MARK);
        return remarksSql.toString();
    }
}
