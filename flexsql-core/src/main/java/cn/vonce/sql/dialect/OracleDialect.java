package cn.vonce.sql.dialect;

import cn.vonce.sql.annotation.SqlJSON;
import cn.vonce.sql.bean.Alter;
import cn.vonce.sql.bean.ColumnInfo;
import cn.vonce.sql.bean.Common;
import cn.vonce.sql.bean.Table;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.AlterDifference;
import cn.vonce.sql.enumerate.AlterType;
import cn.vonce.sql.enumerate.JavaMapOracleType;
import cn.vonce.sql.enumerate.JdbcType;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Oracle方言
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/4/16 10:13
 */
public class OracleDialect implements SqlDialect<JavaMapOracleType> {

    @Override
    public JavaMapOracleType getType(Field field) {
        Class<?> clazz = SqlBeanUtil.getEntityClassFieldType(field);
        for (JavaMapOracleType javaType : JavaMapOracleType.values()) {
            for (Class<?> thisClazz : javaType.getClasses()) {
                if (thisClazz == clazz) {
                    return javaType;
                }
            }
        }
        SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
        if (sqlJSON != null) {
            return JavaMapOracleType.JSON;
        }
        throw new SqlBeanException(field.getDeclaringClass().getName() + "，实体类不支持此字段类型：" + clazz.getSimpleName());
    }

    @Override
    public JdbcType getJdbcType(Field field) {
        return JdbcType.getType(getType(field).name());
    }

    @Override
    public String getTableListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT t.table_name AS \"name\", c.comments AS \"remarks\" ");
        sql.append("FROM user_tables t ");
        sql.append("LEFT JOIN user_tab_comments c ");
        sql.append("ON c.table_name = t.table_name");
        if (StringUtil.isNotEmpty(tableName)) {
            sql.append(" AND t.table_name = '");
            sql.append((sqlBeanMeta.getSqlBeanConfig().getToUpperCase() != null && sqlBeanMeta.getSqlBeanConfig().getToUpperCase()) ? tableName.toUpperCase() : tableName);
            sql.append("'");
        }
        return sql.toString();
    }

    @Override
    public String getColumnListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT col.column_id AS cid, col.column_name AS name, col.data_type AS type, ");
        sql.append("(CASE col.nullable WHEN 'N' THEN '1' ELSE '0' END) AS notnull, col.data_default AS dflt_value, ");
        sql.append("(CASE uc1.constraint_type WHEN 'P' THEN '1' ELSE '0' END) AS pk, ");
        sql.append("(CASE uc2.constraint_type WHEN 'R' THEN '1' ELSE '0' END) AS fk, ");
        sql.append("(CASE WHEN col.data_type = 'FLOAT' OR col.data_type = 'DOUBLE' OR col.data_type = 'DECIMAL' OR col.data_type = 'NUMBER' THEN col.data_precision ELSE col.char_length END) AS length, ");
        sql.append("col.data_scale AS scale, ");
        sql.append("user_col_comments.comments AS remarks ");
        sql.append("FROM user_tab_columns col ");
        sql.append("LEFT JOIN user_cons_columns ucc ON ucc.table_name = col.table_name AND ucc.column_name = col.column_name AND ucc.position IS NOT NULL ");
        sql.append("LEFT JOIN user_constraints uc1 ON uc1.constraint_name = ucc.constraint_name AND uc1.constraint_type = 'P' ");
        sql.append("LEFT JOIN user_constraints uc2 ON uc2.constraint_name = ucc.constraint_name AND uc2.constraint_type = 'R' ");
        sql.append("INNER JOIN user_col_comments ON user_col_comments.table_name = col.table_name AND user_col_comments.column_name = col.column_name ");
        sql.append("WHERE col.table_name = '");
        sql.append(tableName);
        sql.append("'");
        return sql.toString();
    }

    @Override
    public List<String> alterTable(List<Alter> alterList) {
        List<String> sqlList = new ArrayList<>();
        String escape = SqlBeanUtil.getEscape(alterList.get(0));
        Table table = alterList.get(0).getTable();
        StringBuilder addOrModifySql = new StringBuilder();
        addOrModifySql.append(SqlConstant.ALTER_TABLE);
        addOrModifySql.append(getFullName(alterList.get(0), table));
        for (int i = 0; i < alterList.size(); i++) {
            Alter alter = alterList.get(i);
            if (alter.getType() == AlterType.ADD) {
                addOrModifySql.append(SqlConstant.ADD);
                addOrModifySql.append(SqlConstant.BEGIN_BRACKET);
                addOrModifySql.append(SqlBeanUtil.addColumn(alter, alter.getColumnInfo(), null));
                addOrModifySql.append(SqlConstant.END_BRACKET);
                addOrModifySql.append(SqlConstant.SPACES);
                sqlList.add(addRemarks(false, alter, escape));
            } else if (alter.getType() == AlterType.MODIFY) {
                addOrModifySql.append(modifyColumn(alter));
                sqlList.add(addRemarks(false, alter, escape));
            } else if (alter.getType() == AlterType.DROP) {
                StringBuilder dropSql = new StringBuilder();
                dropSql.append(SqlConstant.ALTER_TABLE);
                dropSql.append(getFullName(alter, table));
                dropSql.append(SqlConstant.DROP);
                dropSql.append(SqlConstant.BEGIN_BRACKET);
                dropSql.append(escape);
                dropSql.append(alter.getColumnInfo().getName(SqlBeanUtil.isToUpperCase(alter)));
                dropSql.append(escape);
                dropSql.append(SqlConstant.END_BRACKET);
                sqlList.add(dropSql.toString());
            } else if (alter.getType() == AlterType.CHANGE) {
                sqlList.add(changeColumn(alter));
                sqlList.add(addRemarks(false, alter, escape));
                //更改名称的同时可能也更改其他信息
                alter.getColumnInfo().setName(alter.getOldColumnName());
                addOrModifySql.append(modifyColumn(alter));
            }
        }
        //新增更改类型信息的语句需要先执行
        sqlList.add(0, addOrModifySql.toString());
        return sqlList;
    }

    /**
     * 获取全名
     *
     * @param common
     * @param table
     * @return
     */
    private String getFullName(Common common, Table table) {
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
     * 更改列信息
     *
     * @param alter
     * @return
     */
    private String modifyColumn(Alter alter) {
        StringBuilder modifySql = new StringBuilder();
        modifySql.append(SqlConstant.MODIFY);
        modifySql.append(SqlConstant.BEGIN_BRACKET);
        ColumnInfo columnInfo = alter.getColumnInfo();
        JdbcType jdbcType = JdbcType.getType(columnInfo.getType());
        modifySql.append(SqlBeanUtil.getTableFieldName(alter, columnInfo.getName()));
        modifySql.append(SqlConstant.SPACES);
        modifySql.append(jdbcType.name());
        if (columnInfo.getLength() != null && columnInfo.getLength() > 0) {
            modifySql.append(SqlConstant.BEGIN_BRACKET);
            //字段长度
            modifySql.append(columnInfo.getLength());
            if (jdbcType.isFloat()) {
                modifySql.append(SqlConstant.COMMA);
                modifySql.append(columnInfo.getScale() == null ? 0 : columnInfo.getScale());
            }
            modifySql.append(SqlConstant.END_BRACKET);
        }
        if (alter.getDifferences().contains(AlterDifference.NOT_NULL)) {
            //是否为null
            if ((columnInfo.getNotnull() != null && columnInfo.getNotnull()) || columnInfo.getPk()) {
                modifySql.append(SqlConstant.SPACES);
                modifySql.append(SqlConstant.NOT_NULL);
            } else {
                if (alter.getType() == AlterType.MODIFY && columnInfo.getNotnull() != null && !columnInfo.getNotnull()) {
                    modifySql.append(SqlConstant.SPACES);
                    modifySql.append(SqlConstant.NULL);
                }
            }
        }
        //默认值
        if (StringUtil.isNotBlank(columnInfo.getDfltValue())) {
            modifySql.append(SqlConstant.SPACES);
            modifySql.append(SqlConstant.DEFAULT);
            modifySql.append(SqlConstant.SPACES);
            modifySql.append(SqlBeanUtil.getSqlValue(alter, columnInfo.getDfltValue(), jdbcType));
        }
        modifySql.append(SqlConstant.END_BRACKET);
        modifySql.append(SqlConstant.SPACES);
        return modifySql.toString();
    }

    /**
     * 更改字段名
     *
     * @param alter
     * @return
     */
    private String changeColumn(Alter alter) {
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

    @Override
    public String getSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT USERNAME AS \"name\" FROM ALL_USERS ");
        if (StringUtil.isNotEmpty(schemaName)) {
            sql.append("WHERE USERNAME = ");
            sql.append("'" + this.getSchemaName(sqlBeanMeta, schemaName) + "'");
        }
        return sql.toString();
    }

    @Override
    public String getCreateSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        return null;
    }

    @Override
    public String getDropSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        return null;
    }

}
