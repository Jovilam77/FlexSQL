package cn.vonce.sql.dialect;

import cn.vonce.sql.annotation.SqlJSON;
import cn.vonce.sql.bean.Alter;
import cn.vonce.sql.bean.Select;
import cn.vonce.sql.bean.Table;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.AlterType;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.enumerate.JavaMapMySqlType;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Mysql方言
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/4/16 9:41
 */
public class MysqlDialect extends AbstractDialect<JavaMapMySqlType> {

    @Override
    public JavaMapMySqlType getType(Field field) {
        Class<?> clazz = SqlBeanUtil.getEntityClassFieldType(field);
        for (JavaMapMySqlType javaType : JavaMapMySqlType.values()) {
            for (Class<?> thisClazz : javaType.getClasses()) {
                if (thisClazz == clazz) {
                    return javaType;
                }
            }
        }
        SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
        if (sqlJSON != null) {
            return JavaMapMySqlType.JSON;
        }
        throw new SqlBeanException(field.getDeclaringClass().getName() + "，实体类不支持此字段类型：" + clazz.getSimpleName());
    }

    @Override
    public String getTableListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT table_schema AS `schema`, table_name AS `name`, table_comment AS `remarks` ");
        sql.append("FROM information_schema.tables ");
        sql.append("WHERE table_type = 'BASE TABLE' AND table_schema = ");
        if (StringUtil.isNotEmpty(schema)) {
            sql.append("'" + schema + "'");
        } else {
            sql.append("database()");
        }
        if (StringUtil.isNotEmpty(tableName)) {
            sql.append(" AND table_name = '" + tableName + "'");
        }
        return sql.toString();
    }

    @Override
    public String getColumnListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ordinal_position AS cid, column_name AS name, data_type AS type, ");
        sql.append("(CASE is_nullable WHEN 'NO' THEN 1 ELSE 0 END) AS notnull, column_default AS dflt_value, ");
        sql.append("(CASE column_key WHEN 'PRI' THEN 1 ELSE 0 END) AS pk, ");
        sql.append("(CASE column_key WHEN 'MUL' THEN 1 ELSE 0 END) AS fk, ");
        sql.append("(CASE extra WHEN 'auto_increment' THEN 1 ELSE 0 END) AS auto_incr, ");
        //MySql8之后整数类型不支持设置长度
        if (sqlBeanMeta.getDbType() == DbType.MySQL && sqlBeanMeta.getDatabaseMajorVersion() >= 8) {
            sql.append("COALESCE(character_maximum_length, numeric_precision) AS length, ");
        } else {
            sql.append("(CASE WHEN data_type = 'bit' OR data_type = 'tinyint' OR data_type = 'smallint' OR data_type = 'mediumint' OR data_type = 'int' OR data_type = 'bigint' ");
            sql.append("THEN REPLACE ( SUBSTRING( column_type, INSTR( column_type, '(' )+ 1 ), ')', '' ) ");
            sql.append("WHEN data_type = 'float' OR data_type = 'double' OR data_type = 'decimal' ");
            sql.append("THEN numeric_precision ELSE character_maximum_length END ) AS length, ");
        }
        sql.append("numeric_scale AS scale, ");
        sql.append("column_comment AS remarks ");
        sql.append("FROM information_schema.columns ");
        sql.append("WHERE table_schema = ");
        if (StringUtil.isNotEmpty(schema)) {
            sql.append("'" + schema + "'");
        } else {
            sql.append("database()");
        }
        sql.append(" AND table_name = '");
        sql.append(tableName);
        sql.append("'");
        return sql.toString();
    }

    @Override
    public List<String> alterTable(List<Alter> alterList) {
        String escape = SqlBeanUtil.getEscape(alterList.get(0));
        Table table = alterList.get(0).getTable();
        StringBuilder sql = new StringBuilder();
        sql.append(SqlConstant.ALTER_TABLE);
        if (StringUtil.isNotBlank(table.getSchema())) {
            sql.append(escape);
            sql.append(table.getSchema());
            sql.append(escape);
            sql.append(SqlConstant.POINT);
        }
        sql.append(escape);
        sql.append(table.getName());
        sql.append(escape);
        sql.append(SqlConstant.SPACES);
        for (int i = 0; i < alterList.size(); i++) {
            Alter alter = alterList.get(i);
            if (alter.getType() == AlterType.ADD) {
                sql.append(SqlConstant.ADD);
                sql.append(SqlConstant.COLUMN);
                sql.append(SqlBeanUtil.addColumn(alter, alter.getColumnInfo(), alter.getAfterColumnName()));
            } else if (alter.getType() == AlterType.MODIFY) {
                sql.append(SqlConstant.MODIFY);
                sql.append(SqlConstant.COLUMN);
                sql.append(SqlBeanUtil.addColumn(alter, alter.getColumnInfo(), alter.getAfterColumnName()));
            } else if (alter.getType() == AlterType.CHANGE) {
                sql.append(SqlConstant.CHANGE);
                sql.append(SqlConstant.COLUMN);
                sql.append(alter.getOldColumnName(SqlBeanUtil.isToUpperCase(alter)));
                sql.append(SqlBeanUtil.addColumn(alter, alter.getColumnInfo(), alter.getAfterColumnName()));
            } else if (alter.getType() == AlterType.DROP) {
                sql.append(SqlConstant.DROP);
                sql.append(SqlConstant.COLUMN);
                sql.append(alter.getColumnInfo().getName(SqlBeanUtil.isToUpperCase(alter)));
            }
            sql.append(SqlConstant.SPACES);
            if (i < alterList.size() - 1) {
                sql.append(SqlConstant.COMMA);
            }
        }
        List<String> sqlList = new ArrayList<>();
        sqlList.add(sql.toString());
        return sqlList;
    }

    @Override
    public String addRemarks(boolean isTable, Alter item, String escape) {
        StringBuilder remarksSql = new StringBuilder();
        remarksSql.append(SqlConstant.ALTER_TABLE);
        if (StringUtil.isNotBlank(item.getTable().getSchema())) {
            remarksSql.append(escape);
            remarksSql.append(item.getTable().getSchema());
            remarksSql.append(escape);
            remarksSql.append(SqlConstant.POINT);
        }
        remarksSql.append(escape);
        remarksSql.append(item.getTable().getName());
        remarksSql.append(escape);
        remarksSql.append(SqlConstant.COMMENT);
        remarksSql.append(SqlConstant.EQUAL_TO);
        remarksSql.append(SqlConstant.SINGLE_QUOTATION_MARK);
        remarksSql.append(StringUtil.isNotBlank(item.getColumnInfo().getRemarks()) ? item.getColumnInfo().getRemarks() : "''");
        remarksSql.append(SqlConstant.SINGLE_QUOTATION_MARK);
        return remarksSql.toString();
    }

    @Override
    public void appendPageSuffix(StringBuilder sqlSb, Select select, String orderSql, Integer[] pageParam) {
        //MySQL count查询不进行分页处理
        if (select.isCount()) {
            return;
        }
        sqlSb.append(SqlConstant.LIMIT);
        sqlSb.append(pageParam[0]);
        sqlSb.append(SqlConstant.COMMA);
        sqlSb.append(pageParam[1]);
    }

    @Override
    public String getSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT schema_name as `name` FROM information_schema.schemata ");
        if (StringUtil.isNotEmpty(schemaName)) {
            sql.append("WHERE schema_name = ");
            sql.append("'" + this.getSchemaName(sqlBeanMeta, schemaName) + "'");
        }
        return sql.toString();
    }

    @Override
    public String getCreateSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE DATABASE IF NOT EXISTS ");
        sql.append(this.getSchemaName(sqlBeanMeta, schemaName));
        sql.append(" CHARACTER SET ");
        if (sqlBeanMeta.getDatabaseMajorVersion() > 5 || (sqlBeanMeta.getDatabaseMajorVersion() == 5 && sqlBeanMeta.getDatabaseMinorVersion() > 3)) {
            sql.append("utf8mb4 COLLATE utf8mb4_general_ci");
        } else {
            sql.append("utf8 COLLATE utf8_general_ci");
        }
        return sql.toString();
    }

    @Override
    public String getDropSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        return "DROP DATABASE IF EXISTS " + this.getSchemaName(sqlBeanMeta, schemaName);
    }

}
