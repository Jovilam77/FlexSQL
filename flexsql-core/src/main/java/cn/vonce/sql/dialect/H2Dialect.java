package cn.vonce.sql.dialect;

import cn.vonce.sql.annotation.SqlJSON;
import cn.vonce.sql.bean.Alter;
import cn.vonce.sql.bean.Select;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.AlterType;
import cn.vonce.sql.enumerate.JavaMapH2Type;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * H2方言
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/4/16 10:17
 */
public class H2Dialect extends AbstractDialect<JavaMapH2Type> {

    @Override
    public JavaMapH2Type getType(Field field) {
        Class<?> clazz = SqlBeanUtil.getEntityClassFieldType(field);
        for (JavaMapH2Type javaType : JavaMapH2Type.values()) {
            for (Class<?> thisClazz : javaType.getClasses()) {
                if (thisClazz == clazz) {
                    return javaType;
                }
            }
        }
        SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
        if (sqlJSON != null) {
            return JavaMapH2Type.VARCHAR;
        }
        throw new SqlBeanException(field.getDeclaringClass().getName() + "，实体类不支持此字段类型：" + clazz.getSimpleName());
    }

    /**
     * 获取表数据列表的SQL
     *
     * @param sqlBeanMeta
     * @param schema
     * @param tableName
     * @return
     */
    @Override
    public String getTableListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT TABLE_SCHEMA AS schema,TABLE_NAME AS name,REMARKS AS remarks ");
        sql.append("FROM information_schema.tables ");
        sql.append("WHERE (table_type = 'TABLE' OR TABLE_TYPE = 'BASE TABLE') ");
        sql.append(" AND TABLE_SCHEMA = ");
        if (StringUtil.isNotEmpty(schema)) {
            sql.append("'" + schema + "'");
        } else {
            sql.append("'PUBLIC'");
        }
        if (StringUtil.isNotEmpty(tableName)) {
            sql.append(" AND TABLE_NAME = '" + tableName + "'");
        }
        return sql.toString();
    }

    /**
     * 获取列数据列表的SQL
     *
     * @param sqlBeanMeta
     * @param tableName
     * @return
     */
    @Override
    public String getColumnListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT cl.ORDINAL_POSITION AS cid, ");
        sql.append("cl.COLUMN_NAME AS name, ");
        if (sqlBeanMeta.getDatabaseMajorVersion() == 1) {
            sql.append("cl.TYPE_NAME AS type, ");
        } else {
            sql.append("cl.DATA_TYPE AS type, ");
        }
        sql.append("CASE WHEN cl.IS_NULLABLE  = 'NO' THEN 1 ELSE 0 END AS notnull, ");
        sql.append("cl.COLUMN_DEFAULT AS dflt_value, ");
        sql.append("cl.CHARACTER_MAXIMUM_LENGTH AS length, ");
        sql.append("cl.NUMERIC_SCALE AS scale, ");
        sql.append("CASE WHEN tc.CONSTRAINT_TYPE = 'PRIMARY KEY' THEN 1 ELSE 0 END AS pk, ");
        sql.append("CASE WHEN tc.CONSTRAINT_TYPE = 'FOREIGN KEY' THEN 1 ELSE 0 END AS fk, ");
        sql.append("cl.REMARKS AS remarks ");
        sql.append("FROM INFORMATION_SCHEMA.COLUMNS cl ");
        sql.append("LEFT JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu ");
        sql.append("ON kcu.TABLE_NAME = cl.TABLE_NAME AND kcu.COLUMN_NAME = cl.COLUMN_NAME ");
        sql.append("LEFT JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc ");
        sql.append("ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME ");
        sql.append("WHERE cl.TABLE_SCHEMA = ");
        if (StringUtil.isNotEmpty(schema)) {
            sql.append("'" + schema + "'");
        } else {
            sql.append("'PUBLIC'");
        }
        sql.append(" AND cl.TABLE_NAME = '");
        sql.append(tableName);
        sql.append("'");
        return sql.toString();
    }

    /**
     * 更改表结构
     *
     * @param alterList
     * @return
     */
    @Override
    public List<String> alterTable(List<Alter> alterList) {
        List<String> sqlList = new ArrayList<>();
        String escape = SqlBeanUtil.getEscape(alterList.get(0));
        StringBuilder sql = new StringBuilder();
        StringBuilder remarksSql = new StringBuilder();
        for (int i = 0; i < alterList.size(); i++) {
            Alter alter = alterList.get(i);
            if (alter.getType() == AlterType.ADD) {
                sql.append(SqlConstant.ALTER_TABLE);
                sql.append(getFullName(alter, alter.getTable()));
                sql.append(SqlConstant.ADD);
                sql.append(SqlBeanUtil.addColumn(alter, alter.getColumnInfo(), alter.getAfterColumnName()));
                remarksSql.append(addRemarks(false, alter, escape));
            } else if (alter.getType() == AlterType.CHANGE) {
                sql.append(changeColumn(alter));
                sql.append(SqlConstant.SEMICOLON);
                //先改名后修改信息
                StringBuilder modifySql = modifyColumn(alter);
                if (modifySql.length() > 0) {
                    sql.append(SqlConstant.ALTER_TABLE);
                    sql.append(modifySql);
                }
                remarksSql.append(addRemarks(false, alter, escape));
            } else if (alter.getType() == AlterType.MODIFY) {
                sql.append(SqlConstant.ALTER_TABLE);
                sql.append(modifyColumn(alter));
                remarksSql.append(addRemarks(false, alter, escape));
            } else if (alter.getType() == AlterType.DROP) {
                sql.append(SqlConstant.ALTER_TABLE);
                sql.append(getFullName(alter, alter.getTable()));
                sql.append(SqlConstant.DROP);
                sql.append(SqlConstant.COLUMN);
                sql.append(alter.getColumnInfo().getName(SqlBeanUtil.isToUpperCase(alter)));
            }
            sql.append(SqlConstant.SPACES);
            sql.append(SqlConstant.SEMICOLON);
        }
        sqlList.add(sql.toString());
        sqlList.add(remarksSql.toString());
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
        modifySql.append(getFullName(alter, alter.getTable()));
        modifySql.append(SqlConstant.ALTER);
        modifySql.append(SqlConstant.COLUMN);
        modifySql.append(SqlBeanUtil.addColumn(alter, alter.getColumnInfo(), alter.getAfterColumnName()));
        return modifySql;
    }

    @Override
    public void appendPageSuffix(StringBuilder sqlSb, Select select, String orderSql, Integer[] pageParam) {
        //H2 count查询不进行分页处理
        if (select.isCount()) {
            return;
        }
        sqlSb.append(SqlConstant.LIMIT);
        sqlSb.append(pageParam[0]);
        sqlSb.append(SqlConstant.COMMA);
        sqlSb.append(pageParam[1]);
    }

    /**
     * 获取schema的SQL
     *
     * @param schemaName
     * @return
     */
    @Override
    public String getSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT SCHEMA_NAME as \"name\" FROM INFORMATION_SCHEMA.SCHEMATA ");
        if (StringUtil.isNotEmpty(schemaName)) {
            sql.append("WHERE SCHEMA_NAME = ");
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
