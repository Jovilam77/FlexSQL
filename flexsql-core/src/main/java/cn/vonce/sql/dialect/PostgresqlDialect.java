package cn.vonce.sql.dialect;

import cn.vonce.sql.annotation.SqlJSON;
import cn.vonce.sql.bean.Alter;
import cn.vonce.sql.bean.ColumnInfo;
import cn.vonce.sql.bean.Common;
import cn.vonce.sql.bean.Table;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.AlterType;
import cn.vonce.sql.enumerate.JavaMapPostgresqlType;
import cn.vonce.sql.enumerate.JdbcType;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Postgresql方言
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/4/16 10:15
 */
public class PostgresqlDialect implements SqlDialect<JavaMapPostgresqlType> {

    @Override
    public JavaMapPostgresqlType getType(Field field) {
        Class<?> clazz = SqlBeanUtil.getEntityClassFieldType(field);
        for (JavaMapPostgresqlType javaType : JavaMapPostgresqlType.values()) {
            for (Class<?> thisClazz : javaType.getClasses()) {
                if (thisClazz == clazz || thisClazz.isAssignableFrom(clazz)) {
                    return javaType;
                }
            }
        }
        SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
        if (sqlJSON != null) {
            return JavaMapPostgresqlType.JSON;
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
        sql.append("SELECT pt.tablename AS \"name\", pd.description AS remarks ");
        sql.append("FROM pg_tables pt ");
        sql.append("INNER JOIN pg_class pc ON pc.relname = pt.tablename ");
        sql.append("INNER JOIN pg_namespace pn ON pn.oid = pc.relnamespace AND pn.nspname = pt.schemaname ");
        sql.append("LEFT JOIN pg_description pd ON pd.objoid = pc.oid AND pd.objsubid = 0 ");
        sql.append("WHERE pt.schemaname = ");
        if (StringUtil.isNotEmpty(schema)) {
            sql.append("'" + schema + "'");
        } else {
            sql.append("current_schema()");
        }
        if (StringUtil.isNotEmpty(tableName)) {
            sql.append(" AND tablename = '" + tableName + "'");
        }
        return sql.toString();
    }

    @Override
    public String getColumnListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT cl.ordinal_position as cid, cl.column_name as name, cl.data_type as type, ");
        sql.append("CASE WHEN cl.is_nullable = 'NO' THEN 1 ELSE 0 END as notnull, ");
        sql.append("cl.column_default as dflt_value, cl.character_maximum_length as length, cl.numeric_scale as scale, ");
        sql.append("CASE WHEN tc.constraint_type = 'PRIMARY KEY' THEN 1 ELSE 0 END AS pk, ");
        sql.append("CASE WHEN tc.constraint_type = 'FOREIGN KEY' THEN 1 ELSE 0 END AS fk, ");
        sql.append("(SELECT pd.description ");
        sql.append("FROM pg_description pd ");
        sql.append("INNER JOIN pg_class pc ON pc.oid = pd.objoid ");
        sql.append("INNER JOIN pg_namespace pn ON pn.oid = pc.relnamespace ");
        sql.append("WHERE pn.nspname = cl.table_schema AND pc.relname = cl.table_name AND pd.objsubid = cl.ordinal_position) AS remarks ");
        sql.append("FROM information_schema.columns cl ");
        sql.append("LEFT JOIN information_schema.key_column_usage kcu ON kcu.table_name = cl.table_name AND kcu.column_name = cl.column_name AND kcu.table_schema = cl.table_schema ");
        sql.append("LEFT JOIN information_schema.table_constraints tc ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = cl.table_schema ");
        sql.append("WHERE cl.table_schema = ");
        if (StringUtil.isNotEmpty(schema)) {
            sql.append("'" + schema + "'");
        } else {
            sql.append("current_schema()");
        }
        if (StringUtil.isNotEmpty(tableName)) {
            sql.append(" AND cl.table_name = '" + tableName + "'");
        }
        return sql.toString();
    }

    @Override
    public List<String> alterTable(List<Alter> alterList) {
        List<String> sqlList = new ArrayList<>();
        String escape = SqlBeanUtil.getEscape(alterList.get(0));
        Table table = alterList.get(0).getTable();
        StringBuilder alertSql = new StringBuilder();
        alertSql.append(SqlConstant.ALTER_TABLE);
        alertSql.append(getFullName(alterList.get(0), table));
        StringBuilder addOrModifySql = new StringBuilder();
        for (int i = 0; i < alterList.size(); i++) {
            Alter alter = alterList.get(i);
            if (alter.getType() == AlterType.ADD) {
                if (addOrModifySql.length() > 0) {
                    addOrModifySql.append(SqlConstant.COMMA);
                }
                addOrModifySql.append(SqlConstant.ADD);
                addOrModifySql.append(SqlConstant.COLUMN);
                addOrModifySql.append(SqlBeanUtil.addColumn(alter, alter.getColumnInfo(), null));
                sqlList.add(addRemarks(false, alter, escape));
            } else if (alter.getType() == AlterType.MODIFY) {
                if (addOrModifySql.length() > 0) {
                    addOrModifySql.append(SqlConstant.COMMA);
                }
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
                if (addOrModifySql.length() > 0) {
                    addOrModifySql.append(SqlConstant.COMMA);
                }
                alter.getColumnInfo().setName(alter.getOldColumnName());
                addOrModifySql.append(modifyColumn(alter));
            }
        }
        //新增更改类型信息的语句需要先执行
        alertSql.append(addOrModifySql);
        sqlList.add(0, alertSql.toString());
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
        ColumnInfo columnInfo = alter.getColumnInfo();
        JdbcType jdbcType = JdbcType.getType(columnInfo.getType());
        StringBuilder modifySql = new StringBuilder();
        modifySql.append(SqlConstant.ALTER);
        modifySql.append(SqlConstant.COLUMN);
        String columnName = SqlBeanUtil.getTableFieldName(alter, columnInfo.getName());
        modifySql.append(columnName);
        modifySql.append(SqlConstant.SPACES);
        modifySql.append(SqlConstant.TYPE);
        StringBuilder typeSql = new StringBuilder();
        typeSql.append(jdbcType.name());
        if (columnInfo.getLength() != null && columnInfo.getLength() > 0) {
            typeSql.append(SqlConstant.BEGIN_BRACKET);
            //字段长度
            typeSql.append(columnInfo.getLength());
            if (jdbcType.isFloat()) {
                typeSql.append(SqlConstant.COMMA);
                typeSql.append(columnInfo.getScale() == null ? 0 : columnInfo.getScale());
            }
            typeSql.append(SqlConstant.END_BRACKET);
        }
        modifySql.append(typeSql);
        modifySql.append(SqlConstant.USING);
        modifySql.append(columnName);
        modifySql.append(SqlConstant.DOUBLE_COLON);
        modifySql.append(typeSql);
        //是否存在主键
        if (columnInfo.getPk()) {
            modifySql.append(SqlConstant.COMMA);
            //先删除主键
            modifySql.append(SqlConstant.DROP);
            modifySql.append(SqlConstant.CONSTRAINT);
            modifySql.append(SqlConstant.DOUBLE_ESCAPE_CHARACTER);
            modifySql.append(alter.getTable().getName());
            modifySql.append(SqlConstant.UNDERLINE);
            modifySql.append(SqlConstant.PKEY);
            modifySql.append(SqlConstant.DOUBLE_ESCAPE_CHARACTER);
            modifySql.append(SqlConstant.COMMA);
            //添加主键
            modifySql.append(SqlConstant.ADD);
            modifySql.append(SqlConstant.PRIMARY_KEY);
            modifySql.append(SqlConstant.BEGIN_BRACKET);
            modifySql.append(columnName);
            modifySql.append(SqlConstant.END_BRACKET);
        }
        //是否为null
        if (columnInfo.getNotnull() != null) {
            modifySql.append(SqlConstant.COMMA);
            modifySql.append(SqlConstant.ALTER);
            modifySql.append(SqlConstant.COLUMN);
            modifySql.append(columnName);
            modifySql.append(columnInfo.getNotnull() ? SqlConstant.SET : SqlConstant.SPACES + SqlConstant.DROP);
            modifySql.append(SqlConstant.NOT_NULL);
        }
        //默认值
        if (columnInfo.getDfltValue() != null) {
            modifySql.append(SqlConstant.COMMA);
            modifySql.append(SqlConstant.ALTER);
            modifySql.append(SqlConstant.COLUMN);
            modifySql.append(columnName);
            if (StringUtil.isNotEmpty(columnInfo.getDfltValue())) {
                modifySql.append(SqlConstant.SET);
                modifySql.append(SqlConstant.DEFAULT);
                modifySql.append(SqlConstant.SPACES);
                modifySql.append(SqlBeanUtil.getSqlValue(alter, columnInfo.getDfltValue(), jdbcType));
            } else {
                modifySql.append(SqlConstant.SPACES + SqlConstant.DROP);
                modifySql.append(SqlConstant.DEFAULT);
            }
        }
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
        sql.append("SELECT schema_name as \"name\" FROM information_schema.schemata ");
        if (StringUtil.isNotEmpty(schemaName)) {
            sql.append("WHERE schema_name = ");
            sql.append("'" + this.getSchemaName(sqlBeanMeta, schemaName) + "'");
        }
        return sql.toString();
    }

    @Override
    public String getCreateSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        return "CREATE SCHEMA IF NOT EXISTS " + this.getSchemaName(sqlBeanMeta, schemaName);
    }

    @Override
    public String getDropSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        return "DROP SCHEMA IF EXISTS " + this.getSchemaName(sqlBeanMeta, schemaName);
    }

}
