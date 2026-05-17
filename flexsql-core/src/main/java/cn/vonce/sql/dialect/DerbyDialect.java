package cn.vonce.sql.dialect;

import cn.vonce.sql.annotation.SqlJSON;
import cn.vonce.sql.bean.Alter;
import cn.vonce.sql.bean.ColumnInfo;
import cn.vonce.sql.bean.Table;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.AlterType;
import cn.vonce.sql.enumerate.JavaMapDerbyType;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Derby方言
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/4/16 10:21
 */
public class DerbyDialect extends AbstractDialect<JavaMapDerbyType> {

    @Override
    public JavaMapDerbyType getType(Field field) {
        Class<?> clazz = SqlBeanUtil.getEntityClassFieldType(field);
        for (JavaMapDerbyType javaType : JavaMapDerbyType.values()) {
            for (Class<?> thisClazz : javaType.getClasses()) {
                if (thisClazz == clazz) {
                    return javaType;
                }
            }
        }
        SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
        if (sqlJSON != null) {
            return JavaMapDerbyType.VARCHAR;
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
        sql.append("SELECT tb.TABLENAME AS \"name\", sc.SCHEMANAME AS \"schema\" ");
        sql.append("FROM SYS.SYSTABLES tb ");
        sql.append("INNER JOIN SYS.SYSSCHEMAS sc ");
        sql.append("ON sc.SCHEMAID = tb.SCHEMAID ");
        sql.append("WHERE TABLETYPE = 'T' AND sc.SCHEMANAME = ");
        if (StringUtil.isNotEmpty(schema)) {
            sql.append("'" + schema + "'");
        } else {
            sql.append("'SA'");
        }
        if (StringUtil.isNotEmpty(tableName)) {
            sql.append(" AND tablename = '" + tableName + "'");
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
        sql.append("SELECT cl.COLUMNNUMBER AS cid, cl.COLUMNNAME AS name, cl.COLUMNDATATYPE AS type, cl.COLUMNDEFAULT AS dflt_value, ");
        //由于Derby的缺陷无法查询是否为主键和外键，所以默认第一个字段为主键，其余字段都不是外键
        sql.append("(CASE WHEN cl.COLUMNNUMBER = 1 THEN '1' ELSE '0' END) AS pk, 0 AS pk ");
        sql.append("FROM SYS.SYSTABLES AS tb ");
        sql.append("INNER JOIN SYS.SYSCOLUMNS cl ");
        sql.append("ON cl.REFERENCEID = tb.TABLEID ");
        sql.append("INNER JOIN SYS.SYSSCHEMAS sc ");
        sql.append("ON sc.SCHEMAID = tb.SCHEMAID ");
        sql.append("WHERE sc.SCHEMANAME = ");
        if (StringUtil.isNotEmpty(schema)) {
            sql.append("'" + schema + "'");
        } else {
            sql.append("'SA'");
        }
        sql.append(" AND tb.TABLENAME = '");
        sql.append(tableName);
        sql.append("' ORDER BY cl.COLUMNNUMBER");
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
        for (int i = 0; i < alterList.size(); i++) {
            Alter alter = alterList.get(i);
            if (alter.getType() == AlterType.ADD) {
                StringBuilder sql = new StringBuilder();
                sql.append(SqlConstant.ALTER_TABLE);
                sql.append(getFullName(alter, alter.getTable(), null));
                sql.append(SqlConstant.ADD);
                sql.append(SqlConstant.COLUMN);
                sql.append(SqlBeanUtil.addColumn(alter, alter.getColumnInfo(), alter.getAfterColumnName()));
                sqlList.add(sql.toString());
            } else if (alter.getType() == AlterType.CHANGE) {
                sqlList.add(changeColumn(alter));
                //先改名后修改信息
                StringBuilder modifySql = modifyColumn(alter);
                if (modifySql.length() > 0) {
                    sqlList.add(SqlConstant.ALTER_TABLE + modifySql);
                }
            } else if (alter.getType() == AlterType.MODIFY) {
                sqlList.add(SqlConstant.ALTER_TABLE + modifyColumn(alter));
            } else if (alter.getType() == AlterType.DROP) {
                StringBuilder sql = new StringBuilder();
                sql.append(SqlConstant.ALTER_TABLE);
                sql.append(getFullName(alter, alter.getTable(), null));
                sql.append(SqlConstant.DROP);
                sql.append(SqlConstant.COLUMN);
                sql.append(alter.getColumnInfo().getName(SqlBeanUtil.isToUpperCase(alter)));
                sqlList.add(sql.toString());
            }
        }
        return sqlList;
    }

    @Override
    public String addRemarks(boolean isTable, Alter item, String escape) {
        return null;
    }

    /**
     * 获取全名
     *
     * @param alter
     * @param table
     * @return
     */
    private String getFullName(Alter alter, Table table, String columnName) {
        boolean rename = alter.getType() == AlterType.CHANGE && StringUtil.isNotBlank(columnName);
        boolean toUpperCase = SqlBeanUtil.isToUpperCase(alter);
        StringBuilder sql = new StringBuilder();
        if (StringUtil.isNotBlank(table.getSchema())) {
            sql.append(table.getSchema(toUpperCase));
            sql.append(SqlConstant.POINT);
        }
        sql.append(table.getName(toUpperCase));
        if (rename) {
            sql.append(SqlConstant.POINT);
            sql.append(toUpperCase ? columnName.toUpperCase() : columnName);
        }
        sql.append(SqlConstant.SPACES);
        return sql.toString();
    }

    /**
     * 更改列信息
     *
     * @param alter
     * @return
     */
    private StringBuilder modifyColumn(Alter alter) {
        ColumnInfo columnInfo = alter.getColumnInfo();
        StringBuilder modifySql = new StringBuilder();
        modifySql.append(getFullName(alter, alter.getTable(), null));
        modifySql.append(SqlConstant.ALTER);
        modifySql.append(SqlConstant.COLUMN);
        modifySql.append(SqlBeanUtil.getTableFieldName(alter, columnInfo.getName()));
        //是否为null
        if (columnInfo.getNotnull() != null && columnInfo.getNotnull()) {
            modifySql.append(SqlConstant.SPACES);
            modifySql.append(SqlConstant.NOT_NULL);
        } else if (alter.getType() == AlterType.MODIFY && columnInfo.getNotnull() != null && !columnInfo.getNotnull()) {
            modifySql.append(SqlConstant.SPACES);
            modifySql.append(SqlConstant.NULL);
        }
        return modifySql;
    }

    /**
     * 更改字段名
     *
     * @param alter
     * @return
     */
    public String changeColumn(Alter alter) {
        StringBuilder changeSql = new StringBuilder();
        changeSql.append(SqlConstant.RENAME);
        changeSql.append(SqlConstant.COLUMN);
        changeSql.append(getFullName(alter, alter.getTable(), alter.getOldColumnName()));
        changeSql.append(SqlConstant.TO);
        changeSql.append(alter.getColumnInfo().getName(SqlBeanUtil.isToUpperCase(alter) ));
        return changeSql.toString();
    }

    @Override
    public String getSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT SCHEMANAME as \"name\" FROM SYS.SYSSCHEMAS ");
        if (StringUtil.isNotEmpty(schemaName)) {
            sql.append("WHERE SCHEMANAME = ");
            sql.append("'" + this.getSchemaName(sqlBeanMeta, schemaName) + "'");
        }
        return sql.toString();
    }

    @Override
    public String getCreateSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        return "CREATE SCHEMA AUTHORIZATION " + this.getSchemaName(sqlBeanMeta, schemaName);
    }

    @Override
    public String getDropSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        return "DROP SCHEMA " + this.getSchemaName(sqlBeanMeta, schemaName) + " RESTRICT";
    }

}
