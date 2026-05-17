package cn.vonce.sql.dialect;

import cn.vonce.sql.annotation.SqlJSON;
import cn.vonce.sql.bean.Alter;
import cn.vonce.sql.bean.ColumnInfo;
import cn.vonce.sql.bean.Select;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.AlterDifference;
import cn.vonce.sql.enumerate.AlterType;
import cn.vonce.sql.enumerate.JavaMapDB2Type;
import cn.vonce.sql.enumerate.JdbcType;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * DB2方言
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/4/16 9:54
 */
public class DB2Dialect extends AbstractDialect<JavaMapDB2Type> {

    @Override
    public JavaMapDB2Type getType(Field field) {
        Class<?> clazz = SqlBeanUtil.getEntityClassFieldType(field);
        for (JavaMapDB2Type javaType : JavaMapDB2Type.values()) {
            for (Class<?> thisClazz : javaType.getClasses()) {
                if (thisClazz == clazz) {
                    return javaType;
                }
            }
        }
        SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
        if (sqlJSON != null) {
            return JavaMapDB2Type.VARCHAR;
        }
        throw new SqlBeanException(field.getDeclaringClass().getName() + "，实体类不支持此字段类型：" + clazz.getSimpleName());
    }

    @Override
    public String getTableListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT name, remarks ");
        sql.append("FROM sysibm.systables ");
        sql.append("WHERE type = 'T' ");
        sql.append("AND creator = ");
        if (StringUtil.isNotEmpty(schema)) {
            sql.append("'" + schema + "'");
        } else {
            sql.append("current user");
        }
        if (StringUtil.isNotEmpty(tableName)) {
            sql.append(" AND name = '" + tableName + "'");
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
        for (int i = 0; i < alterList.size(); i++) {
            Alter alter = alterList.get(i);
            if (alter.getType() == AlterType.ADD) {
                StringBuilder sql = new StringBuilder();
                sql.append(SqlConstant.ALTER_TABLE);
                sql.append(getFullName(alter, alter.getTable()));
                sql.append(SqlConstant.ADD);
                sql.append(SqlBeanUtil.addColumn(alter, alter.getColumnInfo(), alter.getAfterColumnName()));
                sqlList.add(sql.toString());
                String remarks = addRemarks(false, alter, escape);
                if (StringUtil.isNotBlank(remarks)) {
                    sqlList.add(remarks);
                }
            } else if (alter.getType() == AlterType.CHANGE) {
                StringBuilder sql = new StringBuilder();
                sql.append(changeColumn(alter));
                sql.append(SqlConstant.SEMICOLON);
                //先改名后修改信息
                StringBuilder modifySql = modifyColumn(alter);
                if (modifySql.length() > 0) {
                    sql.append(modifySql);
                }
                sqlList.add(sql.toString());
                String remarks = addRemarks(false, alter, escape);
                if (StringUtil.isNotBlank(remarks)) {
                    sqlList.add(remarks);
                }
            } else if (alter.getType() == AlterType.MODIFY) {
                StringBuilder modifySql = modifyColumn(alter);
                if (modifySql.length() > 0) {
                    sqlList.add(modifySql.toString());
                }
                String remarks = addRemarks(false, alter, escape);
                if (StringUtil.isNotBlank(remarks)) {
                    sqlList.add(remarks);
                }
            } else if (alter.getType() == AlterType.DROP) {
                StringBuilder sql = new StringBuilder();
                sql.append(SqlConstant.ALTER_TABLE);
                sql.append(getFullName(alter, alter.getTable()));
                sql.append(SqlConstant.DROP);
                sql.append(SqlConstant.COLUMN);
                sql.append(alter.getColumnInfo().getName(SqlBeanUtil.isToUpperCase(alter)));
                sqlList.add(sql.toString());
            }
            sqlList.add(recast(alterList.get(i)));
        }
        return sqlList;
    }

    /**
     * 更改列信息
     *
     * @param alter
     * @return
     */
    private StringBuilder modifyColumn(Alter alter) {
        StringBuilder modifySql = new StringBuilder();
        List<AlterDifference> alterDifferenceList = alter.getDifferences();
        for (AlterDifference alterDifference : alterDifferenceList) {
            ColumnInfo columnInfo = alter.getColumnInfo();
            if (alterDifference == AlterDifference.NOT_NULL || alterDifference == AlterDifference.TYPE) {
                if (modifySql.length() > 0) {
                    modifySql.append(SqlConstant.SEMICOLON);
                }
                modifySql.append(SqlConstant.ALTER_TABLE);
                modifySql.append(getFullName(alter, alter.getTable()));
                modifySql.append(SqlConstant.ALTER);
                modifySql.append(SqlConstant.COLUMN);
                modifySql.append(columnInfo.getName(SqlBeanUtil.isToUpperCase(alter)));
            }
            if (alterDifference == AlterDifference.NOT_NULL) {
                if ((columnInfo.getNotnull() != null && columnInfo.getNotnull()) || columnInfo.getPk()) {
                    modifySql.append(SqlConstant.SET);
                    modifySql.append(SqlConstant.NOT_NULL);
                } else if (columnInfo.getNotnull() != null && !columnInfo.getNotnull()) {
                    modifySql.append(SqlConstant.SPACES);
                    modifySql.append(SqlConstant.DROP);
                    modifySql.append(SqlConstant.NOT_NULL);
                }
            } else if (alterDifference == AlterDifference.TYPE) {
                JdbcType jdbcType = JdbcType.getType(columnInfo.getType());
                modifySql.append(SqlConstant.SET);
                modifySql.append(SqlConstant.DATA_TYPE);
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
            }
        }
        return modifySql;
    }

    /**
     * 重组
     *
     * @param item
     * @return
     */
    private String recast(Alter item) {
        StringBuilder recastSql = new StringBuilder();
        recastSql.append("CALL SYSPROC.ADMIN_CMD");
        recastSql.append(SqlConstant.BEGIN_BRACKET);
        recastSql.append("'REORG TABLE ");
        recastSql.append(getFullName(item, item.getTable()));
        recastSql.append("'");
        recastSql.append(SqlConstant.END_BRACKET);
        return recastSql.toString();
    }

    @Override
    public void appendPageSuffix(StringBuilder sqlSb, Select select, String orderSql, Integer[] pageParam) {
        //DB2 count查询不进行分页处理
        if (select.isCount()) {
            return;
        }
        String sql = sqlSb.toString();
        sqlSb.setLength(0);
        sqlSb.append(SqlConstant.SELECT + SqlConstant.ALL + SqlConstant.FROM + SqlConstant.BEGIN_BRACKET);
        sqlSb.append(SqlConstant.SELECT + SqlConstant.T + SqlConstant.POINT + SqlConstant.ALL + SqlConstant.COMMA + SqlConstant.ROWNUMBER);
        sqlSb.append(SqlConstant.OVER + SqlConstant.BEGIN_BRACKET + SqlConstant.SPACES + SqlConstant.END_BRACKET + SqlConstant.AS + SqlConstant.RN + SqlConstant.FROM + SqlConstant.BEGIN_BRACKET);
        sqlSb.append(sql);
        sqlSb.append(SqlConstant.END_BRACKET + SqlConstant.T + SqlConstant.SPACES + SqlConstant.END_BRACKET + SqlConstant.TB);
        sqlSb.append(SqlConstant.WHERE + SqlConstant.BEGIN_BRACKET + SqlConstant.TB + SqlConstant.POINT + SqlConstant.RN + SqlConstant.LESS_THAN_OR_EQUAL_TO);
        sqlSb.append(pageParam[1]);
        sqlSb.append(SqlConstant.AND + SqlConstant.TB + SqlConstant.POINT + SqlConstant.RN + SqlConstant.GREATER_THAN);
        sqlSb.append(pageParam[0]);
        sqlSb.append(SqlConstant.END_BRACKET);
    }

    @Override
    public String getSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DATABASENAME as \"name\" FROM SYSIBM.SYSDATABASES ");
        if (StringUtil.isNotEmpty(schemaName)) {
            sql.append("WHERE DATABASENAME = ");
            sql.append("'" + this.getSchemaName(sqlBeanMeta, schemaName) + "'");
        }
        return sql.toString();
    }

    @Override
    public String getCreateSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        return "CREATE DATABASE " + this.getSchemaName(sqlBeanMeta, schemaName);
    }

    @Override
    public String getDropSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        return "DROP DATABASE " + this.getSchemaName(sqlBeanMeta, schemaName);
    }

}
