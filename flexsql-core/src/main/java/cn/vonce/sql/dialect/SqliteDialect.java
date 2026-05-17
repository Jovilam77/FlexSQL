package cn.vonce.sql.dialect;

import cn.vonce.sql.annotation.SqlJSON;
import cn.vonce.sql.annotation.SqlTable;
import cn.vonce.sql.bean.*;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.AlterType;
import cn.vonce.sql.enumerate.JavaMapSqliteType;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.helper.SqlHelper;
import cn.vonce.sql.uitls.DateUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Sqlite方言
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/4/16 10:24
 */
public class SqliteDialect extends AbstractDialect<JavaMapSqliteType> {

    @Override
    public JavaMapSqliteType getType(Field field) {
        Class<?> clazz = SqlBeanUtil.getEntityClassFieldType(field);
        for (JavaMapSqliteType javaType : JavaMapSqliteType.values()) {
            for (Class<?> thisClazz : javaType.getClasses()) {
                if (thisClazz == clazz) {
                    return javaType;
                }
            }
        }
        SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
        if (sqlJSON != null) {
            return JavaMapSqliteType.TEXT;
        }
        throw new SqlBeanException(field.getDeclaringClass().getName() + "，实体类不支持此字段类型：" + clazz.getSimpleName());
    }

    @Override
    public String getTableListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT name ");
        sql.append("FROM sqlite_master ");
        sql.append("WHERE type='table'");
        if (StringUtil.isNotEmpty(tableName)) {
            sql.append(" AND name = '" + tableName + "'");
        }
        return sql.toString();
    }

    @Override
    public String getColumnListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("pragma table_info('");
        sql.append(tableName);
        sql.append("')");
        return sql.toString();
    }

    @Override
    public List<String> alterTable(List<Alter> alterList) {
        List<String> sqlList = new ArrayList<>();
        SqlBeanMeta sqlBeanMeta = alterList.get(0).getSqlBeanMeta();
        Table table = alterList.get(0).getTable();
        Class<?> beanClass = alterList.get(0).getBeanClass();
        SqlTable sqlTable = beanClass.getAnnotation(SqlTable.class);
        //备份表
        String oldTableName = SqlConstant.UNDERLINE + table.getName() + SqlConstant.UNDERLINE + "old" + SqlConstant.UNDERLINE + DateUtil.dateToString(new Date(), "yyyyMMddHHmmss");
        StringBuilder alterTableSql = new StringBuilder();
        alterTableSql.append(SqlConstant.ALTER_TABLE);
        alterTableSql.append(SqlConstant.DOUBLE_ESCAPE_CHARACTER);
        alterTableSql.append(table.getName());
        alterTableSql.append(SqlConstant.DOUBLE_ESCAPE_CHARACTER);
        alterTableSql.append(SqlConstant.SPACES);
        alterTableSql.append(SqlConstant.RENAME_TO);
        alterTableSql.append(SqlConstant.DOUBLE_ESCAPE_CHARACTER);
        alterTableSql.append(oldTableName);
        alterTableSql.append(SqlConstant.DOUBLE_ESCAPE_CHARACTER);
        sqlList.add(alterTableSql.toString());
        //创建表
        Create create = new Create();
        create.setSqlBeanMeta(sqlBeanMeta);
        create.setBeanClass(beanClass);
        create.setTable(beanClass);
        sqlList.add(SqlHelper.buildCreateSql(create));
        //复制表数据
        Map<String, Alter> alterMap = new HashMap<>();
        for (Alter alter : alterList) {
            alterMap.put(alter.getColumnInfo().getName(), alter);
        }
        List<Column> columnList = new ArrayList<>();
        List<Column> targetColumnList = new ArrayList<>();
        List<Field> fieldList = SqlBeanUtil.getBeanAllField(beanClass);
        for (Field field : fieldList) {
            if (SqlBeanUtil.isIgnore(field)) {
                continue;
            }
            String targetColumnName;
            String columnName = SqlBeanUtil.getTableFieldName(field, sqlTable);
            Alter alter = alterMap.get(columnName);
            if (alter != null && (alter.getType() == AlterType.ADD || alter.getType() == AlterType.DROP)) {
                continue;
            }
            //如果CHANGE说明是改字段名
            if (alter != null && alter.getType() == AlterType.CHANGE) {
                targetColumnName = alter.getOldColumnName();
            } else {
                targetColumnName = columnName;
            }
            //新建表的字段
            Column column = new Column();
            column.setTableAlias(table.getAlias());
            column.setName(columnName);
            column.setAlias(column.getName());
            columnList.add(column);
            //备份表的字段
            Column targetColumn = new Column();
            targetColumn.setTableAlias(table.getAlias());
            targetColumn.setName(targetColumnName);
            targetColumn.setAlias(column.getName());
            targetColumnList.add(targetColumn);
        }
        Copy copy = new Copy();
        copy.setSqlBeanMeta(sqlBeanMeta);
        copy.setBeanClass(beanClass);
        copy.setTable(beanClass);
        copy.setColumns(columnList.toArray(new Column[]{}));
        copy.setTargetTableName(oldTableName);
        copy.setTargetColumns(targetColumnList.toArray(new Column[]{}));
        sqlList.add(SqlHelper.buildCopy(copy));
        return sqlList;
    }

    @Override
    public void appendPageSuffix(StringBuilder sqlSb, Select select, String orderSql, Integer[] pageParam) {
        //SQLite count查询不进行分页处理
        if (select.isCount()) {
            return;
        }
        sqlSb.append(SqlConstant.LIMIT);
        sqlSb.append(pageParam[1]);
        sqlSb.append(SqlConstant.OFFSET);
        sqlSb.append(pageParam[0]);
    }

    @Override
    public String addRemarks(boolean isTable, Alter item, String escape) {
        return null;
    }

    @Override
    public String getSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        return null;
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
