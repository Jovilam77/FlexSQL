package cn.vonce.sql.helper;

import cn.vonce.sql.annotation.*;
import cn.vonce.sql.define.SqlEnum;
import cn.vonce.sql.define.SqlFun;
import cn.vonce.sql.uitls.ReflectUtil;
import cn.vonce.sql.uitls.StringUtil;
import cn.vonce.sql.bean.*;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.*;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.uitls.SqlBeanUtil;

import java.lang.reflect.Field;
import java.util.*;

/**
 * SQL 语句助手
 *
 * @author jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2017年6月2日下午5:41:59
 */
public class SqlHelper {

    /**
     * 生成select sql语句
     *
     * @param select
     * @return
     */
    public static String buildSelectSql(Select select) {
        SqlBeanUtil.check(select);
        StringBuffer sqlSb = new StringBuffer();
        Integer[] pageParam = null;
        String orderSql = orderBySql(select);
        //SQLServer2008 分页处理
        if (select.getSqlBeanMeta().getDbType() == DbType.SQLServer) {
            if (SqlBeanUtil.isUsePage(select)) {
                pageParam = pageParam(select);
                sqlSb.append(SqlConstant.SELECT);
                sqlSb.append(SqlConstant.ALL);
                sqlSb.append(SqlConstant.FROM);
                sqlSb.append(SqlConstant.BEGIN_BRACKET);
            }
        }
        //标准Sql
        sqlSb.append(select.isDistinct() ? SqlConstant.SELECT_DISTINCT : SqlConstant.SELECT);
        //SqlServer 分页处理
        if (select.getSqlBeanMeta().getDbType() == DbType.SQLServer) {
            if (SqlBeanUtil.isUsePage(select)) {
                sqlSb.append(SqlConstant.TOP);
                sqlSb.append(pageParam[0]);
                sqlSb.append(SqlConstant.ROW_NUMBER + SqlConstant.OVER + SqlConstant.BEGIN_BRACKET + orderSql + SqlConstant.END_BRACKET + SqlConstant.ROWNUM + SqlConstant.COMMA);
            }
        }
        //标准Sql
        if (select.isCount() && !select.isDistinct()) {
            sqlSb.append(SqlConstant.COUNT + SqlConstant.BEGIN_BRACKET + SqlConstant.ALL + SqlConstant.END_BRACKET);
        } else {
            sqlSb.append(column(select));
        }
        sqlSb.append(SqlConstant.FROM);
        sqlSb.append(SqlBeanUtil.fromFullName(select.getTable().getSchema(), select.getTable().getName(), select.getTable().getAlias(), select));
        sqlSb.append(joinSql(select));
        sqlSb.append(whereSql(select, null));
        String groupBySql = groupBySql(select);
        sqlSb.append(groupBySql);
        sqlSb.append(havingSql(select));
        if (!select.isCount()) {
            sqlSb.append(orderSql);
        }
        //SQLServer2008 分页处理
        if (select.getSqlBeanMeta().getDbType() == DbType.SQLServer) {
            // 主要逻辑 结束
            if (SqlBeanUtil.isUsePage(select)) {
                sqlSb.append(SqlConstant.END_BRACKET);
                sqlSb.append(SqlConstant.T);
                sqlSb.append(SqlConstant.WHERE);
                sqlSb.append(SqlConstant.T + SqlConstant.POINT + SqlConstant.ROWNUM);
                sqlSb.append(SqlConstant.GREATER_THAN);
                sqlSb.append(pageParam[1]);
            }
        }
        //标准Sql 如果是克隆的select则为分页时的count
        if ((select.isCount() && select.isDistinct()) || (select.isCount() && StringUtil.isNotEmpty(groupBySql))) {
            sqlSb.insert(0, SqlConstant.SELECT + SqlConstant.COUNT + SqlConstant.BEGIN_BRACKET + SqlConstant.ALL + SqlConstant.END_BRACKET + SqlConstant.FROM + SqlConstant.BEGIN_BRACKET);
            sqlSb.append(SqlConstant.END_BRACKET + SqlConstant.AS + SqlConstant.T);
        }
        //MySQL,MariaDB,H2 分页处理
        if (!select.isCount() && (select.getSqlBeanMeta().getDbType() == DbType.MySQL || select.getSqlBeanMeta().getDbType() == DbType.MariaDB || select.getSqlBeanMeta().getDbType() == DbType.H2)) {
            mysqlPageDispose(select, sqlSb);
        }
        //Postgresql,SQLite,Hsql 分页处理
        else if (!select.isCount() && (select.getSqlBeanMeta().getDbType() == DbType.Postgresql || select.getSqlBeanMeta().getDbType() == DbType.SQLite || select.getSqlBeanMeta().getDbType() == DbType.Hsql)) {
            PostgresqlPageDispose(select, sqlSb);
        }
        //Oracle 分页处理
        else if (!select.isCount() && select.getSqlBeanMeta().getDbType() == DbType.Oracle) {
            oraclePageDispose(select, sqlSb);
        }
        //DB2 分页处理
        else if (!select.isCount() && select.getSqlBeanMeta().getDbType() == DbType.DB2) {
            db2PageDispose(select, sqlSb);
        }
        //Derby 分页处理
        else if (!select.isCount() && select.getSqlBeanMeta().getDbType() == DbType.Derby) {
            derbyPageDispose(select, sqlSb);
        }
        return sqlSb.toString();
    }

    /**
     * 生成update sql语句
     *
     * @param update
     * @return
     * @throws SqlBeanException
     */
    public static String buildUpdateSql(Update update) {
        SqlBeanUtil.check(update);
        StringBuffer sqlSb = new StringBuffer();
        sqlSb.append(SqlConstant.UPDATE);
        if (update.getSqlBeanMeta().getDbType() == DbType.H2 || update.getSqlBeanMeta().getDbType() == DbType.Oracle) {
            sqlSb.append(SqlBeanUtil.fromFullName(update.getTable().getSchema(), update.getTable().getName(), update.getTable().getAlias(), update));
        } else {
            sqlSb.append(SqlBeanUtil.getTableName(update.getTable(), update));
        }
        sqlSb.append(SqlConstant.SET);
        sqlSb.append(setSql(update));
        sqlSb.append(whereSql(update, update.getBean()));
        return sqlSb.toString();
    }

    /**
     * 生成insert sql语句
     *
     * @param insert
     * @return
     */
    @SuppressWarnings("unchecked")
    public static String buildInsertSql(Insert insert) {
        SqlBeanUtil.check(insert);
        String sql = null;
        try {
            sql = fieldAndValuesSql(insert);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        return sql;
    }

    /**
     * 生成delete sql语句
     *
     * @param delete
     * @return
     */
    public static String buildDeleteSql(Delete delete) {
        SqlBeanUtil.check(delete);
        StringBuffer sqlSb = new StringBuffer();
        sqlSb.append(SqlConstant.DELETE_FROM);
        if (delete.getSqlBeanMeta().getDbType() == DbType.H2 || delete.getSqlBeanMeta().getDbType() == DbType.Oracle) {
            sqlSb.append(SqlBeanUtil.fromFullName(delete.getTable().getSchema(), delete.getTable().getName(), delete.getTable().getAlias(), delete));
        } else {
            sqlSb.append(SqlBeanUtil.getTableName(delete.getTable(), delete));
        }
        sqlSb.append(whereSql(delete, null));
        return sqlSb.toString();
    }

    /**
     * 生成create sql语句
     *
     * @param create
     * @return
     */
    public static String buildCreateSql(Create create) {
        SqlBeanUtil.check(create);
        StringBuffer sqlSb = new StringBuffer();
        sqlSb.append(SqlConstant.CREATE_TABLE);
        sqlSb.append(SqlBeanUtil.getTableName(create.getTable(), create));
        sqlSb.append(SqlConstant.BEGIN_BRACKET);
        List<Field> fieldList = SqlBeanUtil.getBeanAllField(create.getBeanClass());
        SqlTable sqlTable = SqlBeanUtil.getSqlTable(create.getBeanClass());
        DbType dbType = create.getSqlBeanMeta().getDbType();
        Class constantClass = SqlBeanUtil.getConstantClass(create.getBeanClass());
        String remarks = sqlTable.remarks();
        //如果没有设置表注释，则从类上获取
        if (StringUtil.isEmpty(remarks)) {
            remarks = SqlBeanUtil.getBeanRemarks(constantClass);
        }
        for (int i = 0; i < fieldList.size(); i++) {
            if (SqlBeanUtil.isIgnore(fieldList.get(i))) {
                continue;
            }
            SqlColumn sqlColumn = fieldList.get(i).getAnnotation(SqlColumn.class);
            sqlSb.append(SqlBeanUtil.addColumn(create, SqlBeanUtil.buildColumnInfo(create.getSqlBeanMeta(), fieldList.get(i), sqlTable, sqlColumn, constantClass), null));
            sqlSb.append(SqlConstant.COMMA);
        }
        Field idField = SqlBeanUtil.getIdField(create.getBeanClass());
        //主键
        if (idField != null) {
            String idFieldName = SqlBeanUtil.getTableFieldName(create, idField, sqlTable);
            sqlSb.append(SqlConstant.PRIMARY_KEY);
            sqlSb.append(SqlConstant.BEGIN_BRACKET);
            sqlSb.append(idFieldName);
            sqlSb.append(SqlConstant.END_BRACKET);
        } else {
            sqlSb.deleteCharAt(sqlSb.length() - SqlConstant.COMMA.length());
        }
        sqlSb.append(SqlConstant.END_BRACKET);
        //如果是Mysql或MariaDB可直接保存备注
        if (StringUtil.isNotBlank(remarks) && (dbType == DbType.MySQL || dbType == DbType.MariaDB)) {
            sqlSb.append(SqlConstant.SPACES);
            sqlSb.append(SqlConstant.COMMENT);
            sqlSb.append(SqlConstant.EQUAL_TO);
            sqlSb.append(SqlConstant.SINGLE_QUOTATION_MARK);
            sqlSb.append(remarks);
            sqlSb.append(SqlConstant.SINGLE_QUOTATION_MARK);
        }
        return sqlSb.toString();
    }

    /**
     * 生成backup sql语句
     *
     * @param backup
     * @return
     */
    public static String buildBackup(Backup backup) {
        SqlBeanUtil.check(backup);
        String targetSchema = backup.getTargetSchema();
        if (StringUtil.isEmpty(targetSchema)) {
            targetSchema = backup.getTable().getSchema();
        }
        StringBuffer backupSql = new StringBuffer();
        //非SQLServer、Postgresql数据库则使用：create table A as select * from B
        if (DbType.SQLServer != backup.getSqlBeanMeta().getDbType() && DbType.Postgresql != backup.getSqlBeanMeta().getDbType()) {
            backupSql.append(SqlConstant.CREATE_TABLE);
            backupSql.append(SqlBeanUtil.getTableName(backup, targetSchema, backup.getTargetTableName()));
            backupSql.append(SqlConstant.SPACES);
            backupSql.append(SqlConstant.AS);
        }
        backupSql.append(SqlConstant.SELECT);
        if (backup.getColumns() != null && backup.getColumns().length > 0) {
            for (Column column : backup.getColumns()) {
                backupSql.append(column.getName());
                backupSql.append(SqlConstant.COMMA);
            }
            backupSql.delete(backupSql.length() - SqlConstant.COMMA.length(), backupSql.length());
        } else {
            backupSql.append(SqlConstant.ALL);
        }
        //如果是SQLServer、Postgresql数据库则需要拼接INTO：select * into A from B
        if (DbType.SQLServer == backup.getSqlBeanMeta().getDbType() || DbType.Postgresql == backup.getSqlBeanMeta().getDbType()) {
            backupSql.append(SqlConstant.INTO);
            backupSql.append(SqlBeanUtil.getTableName(backup, targetSchema, backup.getTargetTableName()));
        }
        backupSql.append(SqlConstant.FROM);
        backupSql.append(SqlBeanUtil.getTableName(backup.getTable(), backup));
        //如果是Derby数据库，仅支持创建表结构，其他数据库则可通过条件备份数据和是否需要数据
        if (DbType.Derby == backup.getSqlBeanMeta().getDbType()) {
            backupSql.append(" WITH NO DATA");
        } else {
            backupSql.append(whereSql(backup, null));
        }
        return backupSql.toString();
    }

    /**
     * 生成copy sql语句
     *
     * @param copy
     * @return
     */
    public static String buildCopy(Copy copy) {
        SqlBeanUtil.check(copy);
        String targetSchema = copy.getTargetSchema();
        if (StringUtil.isEmpty(targetSchema)) {
            targetSchema = copy.getTable().getSchema();
        }
        StringBuffer copySql = new StringBuffer();
        StringBuffer columnSql = new StringBuffer();
        copySql.append(SqlConstant.INSERT_INTO);
        copySql.append(SqlBeanUtil.getTableName(copy.getTable(), copy));
        if (copy.getColumns() != null && copy.getColumns().length > 0) {
            for (Column column : copy.getColumns()) {
                columnSql.append(column.getName());
                columnSql.append(SqlConstant.COMMA);
            }
            columnSql.delete(columnSql.length() - SqlConstant.COMMA.length(), columnSql.length());
            copySql.append(SqlConstant.SPACES);
            copySql.append(SqlConstant.BEGIN_BRACKET);
            copySql.append(columnSql);
            copySql.append(SqlConstant.END_BRACKET);
        }
        copySql.append(SqlConstant.SPACES);
        copySql.append(SqlConstant.SELECT);
        if (copy.getTargetColumns() != null && copy.getTargetColumns().length > 0) {
            StringBuffer targetColumnSql = new StringBuffer();
            for (Column column : copy.getTargetColumns()) {
                targetColumnSql.append(column.getName());
                targetColumnSql.append(SqlConstant.COMMA);
            }
            targetColumnSql.delete(targetColumnSql.length() - SqlConstant.COMMA.length(), targetColumnSql.length());
            copySql.append(targetColumnSql);
        } else if (copy.getTargetColumns() != null && copy.getTargetColumns().length > 0) {
            copySql.append(columnSql);
        } else {
            copySql.append(SqlConstant.ALL);
        }
        copySql.append(SqlConstant.FROM);
        copySql.append(SqlBeanUtil.getTableName(copy, targetSchema, copy.getTargetTableName()));
        copySql.append(whereSql(copy, null));
        return copySql.toString();
    }

    /**
     * 生成drop sql语句
     *
     * @param drop
     * @return
     */
    public static String buildDrop(Drop drop) {
        StringBuffer dropSql = new StringBuffer();
        String tableName = SqlBeanUtil.getTableName(drop.getTable(), drop);
        if (drop.getSqlBeanMeta().getDbType() == DbType.MySQL || drop.getSqlBeanMeta().getDbType() == DbType.MariaDB || drop.getSqlBeanMeta().getDbType() == DbType.Postgresql || drop.getSqlBeanMeta().getDbType() == DbType.H2) {
            dropSql.append("DROP TABLE IF EXISTS ");
            dropSql.append(tableName);
        } else if (drop.getSqlBeanMeta().getDbType() == DbType.MySQL) {
            dropSql.append("IF OBJECT_ID(N'" + tableName + "', N'U') IS NOT NULL ");
            dropSql.append("DROP TABLE " + tableName + " ");
        } else {
            dropSql.append("DROP TABLE ");
            dropSql.append(tableName);
        }
        return dropSql.toString();
    }

    /**
     * 返回column语句
     *
     * @param select
     * @return
     */
    private static String column(Select select) {
        StringBuffer columnSql = new StringBuffer();
        if (select.getColumnList() != null && select.getColumnList().size() != 0) {
            for (int i = 0; i < select.getColumnList().size(); i++) {
                Column column = select.getColumnList().get(i);
                Column newColumn = SqlBeanUtil.copy(column);
                if (column instanceof SqlFun) {
                    newColumn.setName(SqlBeanUtil.getSqlFunction(select, (SqlFun) column));
                }
                String escape = SqlBeanUtil.getEscape(select);
                //存在列别名
                boolean existAlias = StringUtil.isNotEmpty(newColumn.getAlias());
                if (existAlias) {
                    columnSql.append(SqlConstant.BEGIN_BRACKET);
                }
                //存在表别名
                if (StringUtil.isNotEmpty(newColumn.getTableAlias())) {
                    columnSql.append(SqlBeanUtil.getTableFieldFullName(select, newColumn));
                } else {
                    columnSql.append(newColumn.getName(SqlBeanUtil.isToUpperCase(select)));
                }
                if (existAlias) {
                    columnSql.append(SqlConstant.END_BRACKET);
                    columnSql.append(SqlConstant.AS);
                    columnSql.append(escape);
                    columnSql.append(newColumn.getAlias());
                    columnSql.append(escape);
                }
                columnSql.append(SqlConstant.COMMA);
            }
            columnSql.deleteCharAt(columnSql.length() - SqlConstant.COMMA.length());
        }
        return columnSql.toString();
    }

    /**
     * 返回innerJoin语句
     *
     * @param select
     * @return
     */
    private static String joinSql(Select select) {
        StringBuffer joinSql = new StringBuffer();
        if (select != null && select.getJoin().size() != 0) {
            for (int i = 0; i < select.getJoin().size(); i++) {
                Join join = select.getJoin().get(i);
                switch (join.getJoinType()) {
                    case INNER_JOIN:
                        joinSql.append(SqlConstant.INNER_JOIN);
                        break;
                    case LEFT_JOIN:
                        joinSql.append(SqlConstant.LEFT_JOIN);
                        break;
                    case RIGHT_JOIN:
                        joinSql.append(SqlConstant.RIGHT_JOIN);
                        break;
                    case FULL_JOIN:
                        joinSql.append(SqlConstant.FULL_JOIN);
                        break;
                }
                String schema = join.getSchema();
                String tableName = join.getTableName();
                String tableAlias = join.getTableAlias();
                joinSql.append(SqlBeanUtil.fromFullName(schema, tableName, tableAlias, select));
                joinSql.append(SqlConstant.ON);
                if (join.on() != null && join.on().getDataList().size() > 0) {
                    joinSql.append(simpleConditionHandle(select, join.on().getDataList()));
                } else {
                    //过时暂时兼容
                    String tableKeyword = SqlBeanUtil.getTableFieldFullName(select, tableAlias, join.getTableKeyword());
                    String mainKeyword = SqlBeanUtil.getTableFieldFullName(select, select.getTable().getAlias(), join.getMainKeyword());
                    if (StringUtil.isNotEmpty(join.getOn())) {
                        joinSql.append(join.getOn());
                    } else {
                        joinSql.append(tableKeyword);
                        joinSql.append(SqlConstant.EQUAL_TO);
                        joinSql.append(mainKeyword);
                    }
                    if (i < select.getJoin().size() - 1) {
                        joinSql.append(SqlConstant.SPACES);
                    }
                }
            }
        }
        return joinSql.toString();
    }

    /**
     * 返回field及values语句
     *
     * @param insert
     * @return
     * @throws IllegalArgumentException
     */
    private static String fieldAndValuesSql(Insert insert) throws IllegalArgumentException {
        String tableName = SqlBeanUtil.getTableName(insert.getTable(), insert);
        StringBuffer fieldSql = new StringBuffer();
        StringBuffer valueSql = new StringBuffer();
        StringBuffer fieldAndValuesSql = new StringBuffer();
        List<String> valueSqlList = new ArrayList<>();
        List objectList = insert.getBean();
        SqlTable sqlTable = SqlBeanUtil.getSqlTable(insert.getBeanClass());
        //获取bean的全部字段
        List<Field> fieldList = SqlBeanUtil.getBeanAllField(insert.getBeanClass());
        if (insert.getSqlBeanMeta().getDbType() == DbType.Oracle) {
            if (objectList != null && objectList.size() > 1) {
                fieldAndValuesSql.append(SqlConstant.INSERT_ALL_INTO);
            } else {
                fieldAndValuesSql.append(SqlConstant.INSERT_INTO);
            }
        } else {
            fieldAndValuesSql.append(SqlConstant.INSERT_INTO);
        }
        //如果是Bean模式
        if (objectList != null && objectList.size() > 0) {
            for (int i = 0; i < objectList.size(); i++) {
                //每次必须清空
                valueSql.delete(0, valueSql.length());
                //只有在循环第一遍的时候才会处理
                if (i == 0) {
                    fieldSql.append(SqlConstant.BEGIN_BRACKET);
                }
                valueSql.append(SqlConstant.BEGIN_BRACKET);
                int existId = 0;
                for (Field field : fieldList) {
                    if (SqlBeanUtil.isIgnore(field)) {
                        continue;
                    }
                    SqlId sqlId = field.getAnnotation(SqlId.class);
                    SqlDefaultValue sqlDefaultValue = field.getAnnotation(SqlDefaultValue.class);
                    SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
                    JdbcType jdbcType = SqlBeanUtil.getJdbcType(insert.getSqlBeanMeta(), field);
                    if (sqlId != null) {
                        existId++;
                    }
                    if (existId > 1) {
                        throw new SqlBeanException("请正确的标识id字段，id字段只能标识一个，但我们在'" + field.getDeclaringClass().getName() + "'此实体类或其父类找到了不止一处");
                    }
                    //只有在循环第一遍的时候才会处理
                    if (i == 0) {
                        String tableFieldName = SqlBeanUtil.getTableFieldName(insert, field, sqlTable);
                        //如果此字段非id字段 或者 此字段为id字段但是不是自增的id则生成该字段的insert语句
                        if (sqlId == null || (sqlId != null && sqlId.type() != IdType.AUTO)) {
                            fieldSql.append(tableFieldName);
                            fieldSql.append(SqlConstant.COMMA);
                        }
                    }
                    if (sqlId != null && sqlId.type() == IdType.AUTO) {
                        continue;
                    }
                    Object value = ReflectUtil.instance().get(objectList.get(i).getClass(), objectList.get(i), field.getName());
                    if (sqlJSON != null) {
                        value = SqlBeanUtil.getJSONValue(sqlJSON, value);
                    }
                    //如果此字段为id且需要生成唯一id
                    if (sqlId != null && sqlId.type() != IdType.AUTO && sqlId.type() != IdType.NORMAL) {
                        if (StringUtil.isEmpty(value)) {
                            value = insert.getSqlBeanMeta().getSqlBeanConfig().getUniqueIdProcessor().uniqueId(sqlId.type());
                            ReflectUtil.instance().set(objectList.get(i).getClass(), objectList.get(i), field.getName(), value);
                        }
                        valueSql.append(SqlBeanUtil.getSqlValue(insert, value, jdbcType));
                    } else if (field.isAnnotationPresent(SqlLogically.class) && value == null) {
                        //如果标识逻辑删除的字段为空则自动填充
                        Object defaultValue = SqlBeanUtil.assignInitialValue(SqlBeanUtil.getEntityClassFieldType(field));
                        valueSql.append(SqlBeanUtil.getSqlValue(insert, defaultValue, jdbcType));
                        ReflectUtil.instance().set(objectList.get(i).getClass(), objectList.get(i), field.getName(), field.getType() == Boolean.class || field.getType() == boolean.class ? false : 0);
                    } else if (value == null && sqlDefaultValue != null && (sqlDefaultValue.with() == FillWith.INSERT || sqlDefaultValue.with() == FillWith.TOGETHER)) {
                        Object defaultValue = SqlHelper.setDefaultValue(objectList.get(i).getClass(), objectList.get(i), field);
                        valueSql.append(SqlBeanUtil.getSqlValue(insert, defaultValue, jdbcType));
                    } else {
                        valueSql.append(SqlBeanUtil.getSqlValue(insert, value, jdbcType));
                    }
                    valueSql.append(SqlConstant.COMMA);
                }
                valueSql.deleteCharAt(valueSql.length() - SqlConstant.COMMA.length());
                valueSql.append(SqlConstant.END_BRACKET);
                valueSqlList.add(valueSql.toString());
                //只有在循环第一遍的时候才会处理
                if (i == 0) {
                    fieldSql.deleteCharAt(fieldSql.length() - SqlConstant.COMMA.length());
                    fieldSql.append(SqlConstant.END_BRACKET);
                }
            }
        } else {
            List<Column> columnList = insert.getColumnList();
            List<List<Object>> valuesList = insert.getValuesList();
            if (columnList == null || columnList.size() == 0) {
                throw new SqlBeanException("如果你不使用Bean对象的方式用作Insert，请指定Insert的字段");
            }
            if (valuesList == null || valuesList.size() == 0) {
                throw new SqlBeanException("请指定Insert的字段对应的值");
            }
            fieldSql.append(SqlConstant.BEGIN_BRACKET);
            for (int i = 0; i < columnList.size(); i++) {
                fieldSql.append(SqlBeanUtil.getTableFieldName(insert, columnList.get(i).getName()));
                if (i < columnList.size() - 1) {
                    fieldSql.append(SqlConstant.COMMA);
                }
            }
            fieldSql.append(SqlConstant.END_BRACKET);
            for (int i = 0; i < valuesList.size(); i++) {
                //每次必须清空
                valueSql.delete(0, valueSql.length());
                List<Object> valueList = valuesList.get(i);
                if (valueList.size() != columnList.size()) {
                    throw new SqlBeanException("指定Insert的value数量与column数量不一致");
                }
                valueSql.append(SqlConstant.BEGIN_BRACKET);
                for (int j = 0; j < valueList.size(); j++) {
                    valueSql.append(SqlBeanUtil.getSqlValue(insert, valueList.get(j)));
                    if (j < columnList.size() - 1) {
                        valueSql.append(SqlConstant.COMMA);
                    }
                }
                valueSql.append(SqlConstant.END_BRACKET);
                valueSqlList.add(valueSql.toString());
            }
        }
        if (insert.getSqlBeanMeta().getDbType() == DbType.Oracle) {
            for (int k = 0; k < valueSqlList.size(); k++) {
                if (k > 0) {
                    fieldAndValuesSql.append(SqlConstant.INTO);
                }
                fieldAndValuesSql.append(tableName);
                fieldAndValuesSql.append(fieldSql);
                fieldAndValuesSql.append(SqlConstant.VALUES);
                fieldAndValuesSql.append(valueSqlList.get(k));
            }
            if (objectList != null && objectList.size() > 1) {
                fieldAndValuesSql.append(SqlConstant.SELECT_DUAL);
            }
        } else {
            for (int k = 0; k < valueSqlList.size(); k++) {
                if (k == 0) {
                    fieldAndValuesSql.append(tableName);
                    fieldAndValuesSql.append(fieldSql);
                    fieldAndValuesSql.append(SqlConstant.VALUES);
                }
                fieldAndValuesSql.append(valueSqlList.get(k));
                fieldAndValuesSql.append(SqlConstant.COMMA);
            }
            fieldAndValuesSql.deleteCharAt(fieldAndValuesSql.length() - SqlConstant.COMMA.length());
        }
        return fieldAndValuesSql.toString();
    }

    /**
     * 返回set语句
     *
     * @param update
     * @return
     */
    private static String setSql(Update update) {
        StringBuffer setSql = new StringBuffer();
        String escape = SqlBeanUtil.getEscape(update);
        List<Column> filterColumns = update.getFilterColumns();
        Object bean = update.getBean();
        boolean isToUpperCase = SqlBeanUtil.isToUpperCase(update);
        if (bean != null) {
            Table table = SqlBeanUtil.getTable(bean.getClass());
            SqlTable sqlTable = SqlBeanUtil.getSqlTable(bean.getClass());
            List<Field> fieldList = SqlBeanUtil.getBeanAllField(bean.getClass());
            for (Field field : fieldList) {
                if (SqlBeanUtil.isIgnore(field)) {
                    continue;
                }
                Column column = SqlBeanUtil.getTableColumn(field, table, sqlTable);
                if (SqlBeanUtil.isFilter(filterColumns, column)) {
                    continue;
                }
                Object objectValue = ReflectUtil.instance().get(bean.getClass(), bean, field.getName());
                SqlDefaultValue sqlDefaultValue = field.getAnnotation(SqlDefaultValue.class);
                SqlVersion sqlVersion = field.getAnnotation(SqlVersion.class);
                SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
                if (sqlJSON != null && objectValue != null) {
                    objectValue = SqlBeanUtil.getJSONValue(sqlJSON, objectValue);
                }
                //如果是只更新不为null的字段，那么该字段如果是null并且也不是乐观锁字段，也不是更新时填充默认值的字段则跳过
                if (update.isNotNull() && objectValue == null && sqlVersion == null && (sqlDefaultValue == null || sqlDefaultValue.with() == FillWith.INSERT)) {
                    continue;
                }
                //如果不是乐观锁字段，那么字段如果是null并且没有标识乐观锁注解则跳过
                if (!update.isOptimisticLock() && objectValue == null && sqlVersion != null) {
                    continue;
                }
                if (StringUtil.isNotBlank(sqlTable.alias())) {
                    setSql.append(escape);
                    setSql.append(sqlTable.alias());
                    setSql.append(escape);
                    setSql.append(SqlConstant.POINT);
                }
                setSql.append(escape);
                setSql.append(column.getName(isToUpperCase));
                setSql.append(escape);
                setSql.append(SqlConstant.EQUAL_TO);
                if (update.isOptimisticLock() && sqlVersion != null) {
                    Object o = SqlBeanUtil.updateVersion(field.getType(), objectValue);
                    setSql.append(SqlBeanUtil.getSqlValue(update, o));
                } else if (sqlDefaultValue != null && (sqlDefaultValue.with() == FillWith.UPDATE_EVERYTIME || (objectValue == null && (sqlDefaultValue.with() == FillWith.UPDATE || sqlDefaultValue.with() == FillWith.TOGETHER)))) {
                    Object defaultValue = SqlHelper.setDefaultValue(bean.getClass(), bean, field);
                    setSql.append(SqlBeanUtil.getSqlValue(update, defaultValue));
                } else {
                    setSql.append(SqlBeanUtil.getSqlValue(update, objectValue));
                }
                setSql.append(SqlConstant.COMMA);
            }
            setSql.deleteCharAt(setSql.length() - SqlConstant.COMMA.length());
        } else {
            List<SetInfo> setInfoList = update.getSetInfoList();
            if (setInfoList != null && !setInfoList.isEmpty()) {
                for (SetInfo setInfo : setInfoList) {
                    if (StringUtil.isNotBlank(setInfo.getTableAlias())) {
                        setSql.append(escape);
                        setSql.append(setInfo.getTableAlias());
                        setSql.append(escape);
                        setSql.append(SqlConstant.POINT);
                    }
                    setSql.append(escape);
                    setSql.append(setInfo.getName(isToUpperCase));
                    setSql.append(escape);
                    setSql.append(SqlConstant.EQUAL_TO);
                    if (setInfo.getValue() != null && setInfo.getValue().getClass().isArray()) {
                        Object[] values = (Object[]) setInfo.getValue();
                        setSql.append(SqlBeanUtil.getActualValue(update, values[0]));
                        if (setInfo.getOperator() == SetInfo.Operator.ADDITION) {
                            setSql.append(SqlConstant.ADDITION);
                        } else if (setInfo.getOperator() == SetInfo.Operator.SUBTRACT) {
                            setSql.append(SqlConstant.SUBTRACT);
                        }
                        setSql.append(SqlBeanUtil.getActualValue(update, values[1]));
                    } else {
                        setSql.append(SqlBeanUtil.getActualValue(update, setInfo.getValue()));
                    }
                    setSql.append(SqlConstant.COMMA);
                }
                setSql.deleteCharAt(setSql.length() - SqlConstant.COMMA.length());
            }
        }
        return setSql.toString();
    }

    /**
     * 设置默认值
     *
     * @param clazz
     * @param bean
     * @param field
     * @return
     */
    private static Object setDefaultValue(Class<?> clazz, Object bean, Field field) {
        Object defaultValue = SqlBeanUtil.assignInitialValue(SqlBeanUtil.getEntityClassFieldType(field));
        if (SqlEnum.class.isAssignableFrom(field.getType())) {
            //优先根据泛型类型的默认值来匹配，匹配不到则获取第一个枚举
            SqlEnum sqlEnum = SqlBeanUtil.matchEnum(field, defaultValue);
            if (sqlEnum == null) {
                SqlEnum[] sqlEnums = (SqlEnum[]) field.getType().getEnumConstants();
                sqlEnum = sqlEnums[0];
                defaultValue = sqlEnum.getCode();
            }
            ReflectUtil.instance().set(clazz, bean, field.getName(), sqlEnum);
        } else {
            ReflectUtil.instance().set(clazz, bean, field.getName(), defaultValue);
        }
        return defaultValue;
    }

    /**
     * 返回where语句
     *
     * @param commonCondition
     * @param bean
     * @return
     */
    @SuppressWarnings("unchecked")
    public static String whereSql(CommonCondition commonCondition, Object bean) {
        return conditionHandle(ConditionType.WHERE, commonCondition, commonCondition.getWhere(), commonCondition.getArgs(), bean, commonCondition.where(), commonCondition.getWhereWrapper());
    }

    /**
     * 返回groupBy语句
     *
     * @param select
     * @return
     */
    private static String groupBySql(Select select) {
        return groupByAndOrderBySql(SqlConstant.GROUP_BY, select);
    }

    /**
     * @param select
     * @return
     */
    private static String havingSql(Select select) {
        return conditionHandle(ConditionType.HAVING, select, select.getHaving(), select.getHavingArgs(), null, select.having(), select.getHavingWrapper());
    }

    /**
     * 返回orderBy语句
     *
     * @param select
     * @return
     */
    private static String orderBySql(Select select) {
        return groupByAndOrderBySql(SqlConstant.ORDER_BY, select);
    }

    /**
     * 返回orderBy和groupBy语句
     *
     * @param type   SqlHelperCons.ORDER_BY or SqlHelperCons.GROUP_BY
     * @param select
     * @return
     */
    private static String groupByAndOrderBySql(String type, Select select) {
        StringBuffer groupByAndOrderBySql = new StringBuffer();
        int length = SqlConstant.ORDER_BY.equals(type) ? select.getOrderBy().size() : select.getGroupBy().size();
        String escape = SqlBeanUtil.getEscape(select);
        boolean isToUpperCase = SqlBeanUtil.isToUpperCase(select);
        if (length != 0) {
            groupByAndOrderBySql.append(type);
            for (int i = 0; i < length; i++) {
                Column column = SqlConstant.ORDER_BY.equals(type) ? select.getOrderBy().get(i).getColumn() : select.getGroupBy().get(i).getColumn();
                if (StringUtil.isNotEmpty(column.getTableAlias())) {
                    groupByAndOrderBySql.append(escape);
                    groupByAndOrderBySql.append(column.getTableAlias());
                    groupByAndOrderBySql.append(escape);
                    groupByAndOrderBySql.append(SqlConstant.POINT);
                    groupByAndOrderBySql.append(escape);
                }
                if (column instanceof SqlFun) {
                    groupByAndOrderBySql.append(SqlBeanUtil.getSqlFunction(select, (SqlFun) column));
                } else {
                    groupByAndOrderBySql.append(column.getName(isToUpperCase));
                }
                if (StringUtil.isNotEmpty(column.getTableAlias())) {
                    groupByAndOrderBySql.append(escape);
                }
                if (SqlConstant.ORDER_BY.equals(type)) {
                    groupByAndOrderBySql.append(SqlConstant.SPACES);
                    groupByAndOrderBySql.append(select.getOrderBy().get(i).getSqlSort().name());
                    groupByAndOrderBySql.append(SqlConstant.SPACES);
                }
                groupByAndOrderBySql.append(SqlConstant.COMMA);
            }
            groupByAndOrderBySql.deleteCharAt(groupByAndOrderBySql.length() - SqlConstant.COMMA.length());
        } else {
            if (SqlConstant.ORDER_BY.equals(type) && select.getSqlBeanMeta().getDbType() == DbType.SQLServer && SqlBeanUtil.isUsePage(select) && !select.isCount()) {
                groupByAndOrderBySql.append(type);
                String tableFieldFullName = SqlBeanUtil.getTableFieldFullName(select, select.getTable().getAlias(), select.getPage().getIdName());
                groupByAndOrderBySql.append(tableFieldFullName);
            }
        }
        return groupByAndOrderBySql.toString();
    }

    /**
     * 条件处理
     *
     * @param conditionType   条件类型（where还是 having）
     * @param common          公共类
     * @param conditionString 条件字符串（优先级1）
     * @param args            条件字符串参数
     * @param bean            对应的bean
     * @param condition       简单条件（优先级3）
     * @param wrapper         条件包装器（优先级2）
     * @return
     */
    private static String conditionHandle(ConditionType conditionType, Common common, String conditionString, Object[] args, Object bean, Condition condition, Wrapper wrapper) {
        StringBuffer conditionSql = new StringBuffer();
        if (ConditionType.WHERE == conditionType && StringUtil.isBlank(conditionString)) {
            conditionSql.append(versionCondition(common, bean));
            conditionSql.append(logicallyDeleteCondition(common));
        }
        // 优先级1 使用条件字符串拼接
        if (StringUtil.isNotBlank(conditionString)) {
            conditionSql.append(SqlConstant.BEGIN_BRACKET);
            if (args != null && args.length > 0) {
                conditionSql.append(SqlBeanUtil.getCondition(common, conditionString, args));
            } else if (conditionString.indexOf("${") > -1 && bean != null) {
                conditionSql.append(SqlBeanUtil.getCondition(common, conditionString, bean));
            } else {
                conditionSql.append(conditionString);
            }
            conditionSql.append(SqlConstant.END_BRACKET);
        }
        // 优先级2 使用条件包装器
        else if (wrapper != null && !wrapper.getDataList().isEmpty()) {
            if (conditionSql.length() > 0) {
                conditionSql.append(SqlConstant.AND);
            }
            conditionSql.append(wrapperConditionHandle(common, wrapper));
        }
        // 优先级3 使用简单的条件
        else if (condition != null && condition.getDataList().size() > 0) {
            if (conditionSql.length() > 0) {
                conditionSql.append(SqlConstant.AND);
            }
            conditionSql.append(SqlConstant.BEGIN_BRACKET);
            conditionSql.append(simpleConditionHandle(common, condition.getDataList()));
            conditionSql.append(SqlConstant.END_BRACKET);
        }
        if (conditionSql.length() != 0) {
            conditionSql.insert(0, ConditionType.WHERE == conditionType ? SqlConstant.WHERE : SqlConstant.HAVING);
        }
        return conditionSql.toString();
    }

    /**
     * 简单条件处理
     *
     * @param common
     * @param conditionDataList
     * @return
     */
    private static String simpleConditionHandle(Common common, List<ConditionData> conditionDataList) {
        StringBuffer conditionSql = new StringBuffer();
        for (int i = 0; i < conditionDataList.size(); i++) {
            Object itemData = conditionDataList.get(i).getItem();
            if (itemData instanceof ConditionInfo) {
                ConditionInfo conditionInfo = (ConditionInfo) itemData;
                // 遍历sql逻辑处理
                if (i != 0 && i < conditionDataList.size()) {
                    conditionSql.append(getLogic(conditionDataList.get(i).getSqlLogic()));
                }
                conditionSql.append(valueOperator(common, conditionInfo));
            } else {
                conditionSql.append(getLogic(conditionDataList.get(i).getSqlLogic()));
                List<ConditionData> dataList = (List<ConditionData>) itemData;
                if (!dataList.isEmpty()) {
                    conditionSql.append(SqlConstant.BEGIN_BRACKET);
                    conditionSql.append(simpleConditionHandle(common, dataList));
                    conditionSql.append(SqlConstant.END_BRACKET);
                }
            }
        }
        return conditionSql.toString();
    }

    /**
     * 条件包装器解析
     *
     * @param common
     * @param wrapper
     * @return
     */
    private static String wrapperConditionHandle(Common common, Wrapper wrapper) {
        StringBuffer conditionSql = new StringBuffer();
        if (!wrapper.getDataList().isEmpty()) {
            conditionSql.append(SqlConstant.BEGIN_BRACKET);
            for (int i = 0; i < wrapper.getDataList().size(); i++) {
                ConditionData data = wrapper.getDataList().get(i);
                // 遍历sql逻辑处理
                if (i != 0 && i < wrapper.getDataList().size()) {
                    conditionSql.append(getLogic(data.getSqlLogic()));
                }
                Object item = data.getItem();
                if (item instanceof Cond) {
                    conditionSql.append(valueOperator(common, (Cond) item));
                } else {
                    conditionSql.append(wrapperConditionHandle(common, (Wrapper) item));
                }
            }
            conditionSql.append(SqlConstant.END_BRACKET);
        }
        return conditionSql.toString();
    }

    /**
     * 乐观锁处理
     *
     * @param common
     * @param bean
     * @return
     */
    private static String versionCondition(Common common, Object bean) {
        if (!(common instanceof Update) || !((Update) common).isOptimisticLock()) {
            return "";
        }
        StringBuffer versionConditionSql = new StringBuffer();
        SqlTable sqlTable = SqlBeanUtil.getSqlTable(bean.getClass());
        Field versionField = null;
        //更新时乐观锁处理
        if (bean != null) {
            versionField = SqlBeanUtil.getVersionField(bean.getClass());
        }
        if (versionField != null) {
            boolean versionEffectiveness = SqlBeanUtil.versionEffectiveness(versionField.getType());
            if (versionEffectiveness) {
                versionConditionSql.append(SqlConstant.BEGIN_BRACKET);
                versionConditionSql.append(SqlBeanUtil.getTableFieldName(versionField, sqlTable));
                Object versionValue = ReflectUtil.instance().get(bean.getClass(), bean, versionField.getName());
                versionConditionSql.append(versionValue == null ? SqlConstant.IS : SqlConstant.EQUAL_TO);
                versionConditionSql.append(SqlBeanUtil.getSqlValue(common, versionValue));
                versionConditionSql.append(SqlConstant.END_BRACKET);
            }
        }
        return versionConditionSql.toString();
    }

    /**
     * 逻辑删除处理（Select）
     *
     * @param common
     * @return
     */
    private static String logicallyDeleteCondition(Common common) {
        if (common instanceof Select && SqlBeanUtil.checkLogically(common.getBeanClass())) {
            StringBuffer logicallyDeleteSql = new StringBuffer();
            SqlTable sqlTable = SqlBeanUtil.getSqlTable(common.getBeanClass());
            Field logicallyDeleteField = SqlBeanUtil.getLogicallyField(common.getBeanClass());
            if (logicallyDeleteField != null) {
                logicallyDeleteSql.append(SqlConstant.BEGIN_BRACKET);
                logicallyDeleteSql.append(SqlBeanUtil.getTableFieldFullName(common, common.getTable().getAlias(), SqlBeanUtil.getTableFieldName(logicallyDeleteField, sqlTable)));
                logicallyDeleteSql.append(SqlConstant.EQUAL_TO);
                DbType dbType = common.getSqlBeanMeta().getDbType();
                if (dbType == DbType.Postgresql) {
                    logicallyDeleteSql.append("'0'");
                } else if (dbType == DbType.H2 || dbType == DbType.Hsql) {
                    logicallyDeleteSql.append("false");
                } else {
                    logicallyDeleteSql.append(0);
                }
                logicallyDeleteSql.append(SqlConstant.END_BRACKET);
            }
            return logicallyDeleteSql.toString();
        }
        return "";
    }

    /**
     * 获取操作符
     *
     * @param conditionInfo
     * @return
     */
    private static String getOperator(ConditionInfo conditionInfo) {
        String operator = "";
        // 优先使用枚举类型的操作符
        if (conditionInfo.getSqlOperator() != null) {
            SqlOperator sqlOperator = conditionInfo.getSqlOperator();
            if (sqlOperator == SqlOperator.IS || sqlOperator == SqlOperator.IS_NULL) {
                operator = SqlConstant.IS;
            } else if (sqlOperator == SqlOperator.IS_NOT || sqlOperator == SqlOperator.IS_NOT_NULL) {
                operator = SqlConstant.IS_NOT;
            } else if (sqlOperator == SqlOperator.IN) {
                operator = SqlConstant.IN;
            } else if (sqlOperator == SqlOperator.NOT_IN) {
                operator = SqlConstant.NOT_IN;
            } else if (sqlOperator == SqlOperator.LIKE || sqlOperator == SqlOperator.LIKE_L || sqlOperator == SqlOperator.LIKE_R) {
                operator = SqlConstant.LIKE;
            } else if (sqlOperator == SqlOperator.NOT_LIKE || sqlOperator == SqlOperator.NOT_LIKE_L || sqlOperator == SqlOperator.NOT_LIKE_R) {
                operator = SqlConstant.NOT_LIKE;
            } else if (sqlOperator == SqlOperator.BETWEEN) {
                operator = SqlConstant.BETWEEN;
            } else if (sqlOperator == SqlOperator.GREATER_THAN) {
                operator = SqlConstant.GREATER_THAN;
            } else if (sqlOperator == SqlOperator.GREAT_THAN_OR_EQUAL_TO) {
                operator = SqlConstant.GREAT_THAN_OR_EQUAL_TO;
            } else if (sqlOperator == SqlOperator.LESS_THAN) {
                operator = SqlConstant.LESS_THAN;
            } else if (sqlOperator == SqlOperator.LESS_THAN_OR_EQUAL_TO) {
                operator = SqlConstant.LESS_THAN_OR_EQUAL_TO;
            } else if (sqlOperator == SqlOperator.EQUAL_TO) {
                operator = SqlConstant.EQUAL_TO;
            } else if (sqlOperator == SqlOperator.NOT_EQUAL_TO) {
                operator = SqlConstant.NOT_EQUAL_TO;
            }
        } else {
            operator = SqlConstant.EQUAL_TO;
        }
        return operator;
    }

    /**
     * 获取逻辑
     *
     * @param sqlLogic
     * @return
     */
    private static String getLogic(SqlLogic sqlLogic) {
        String logic = null;
        if (sqlLogic != null && !"".equals(sqlLogic)) {
            switch (sqlLogic) {
                case AND:
                    logic = SqlConstant.AND;
                    break;
                case OR:
                    logic = SqlConstant.OR;
                    break;
                case ORBracket:
                    logic = SqlConstant.OR_BRACKET;
                    break;
                case ANDBracket:
                    logic = SqlConstant.AND_BRACKET;
                    break;
            }
        } else {
            logic = SqlConstant.AND;
        }
        return logic;
    }

    /**
     * 值操作
     *
     * @param common
     * @param conditionInfo
     * @return
     */
    private static StringBuffer valueOperator(Common common, ConditionInfo conditionInfo) {
        StringBuffer sql = new StringBuffer();
        String operator = getOperator(conditionInfo);
        boolean needEndBracket = false;
        Object[] betweenValues = null;
        Object value = conditionInfo.getValue();
        // 如果操作符为BETWEEN ，IN、NOT IN 则需额外处理
        if (conditionInfo.getSqlOperator() == SqlOperator.BETWEEN) {
            betweenValues = SqlBeanUtil.getObjectArray(value);
            if (betweenValues == null) {
                try {
                    throw new SqlBeanException("between 条件的值必须为Array或ArrayList");
                } catch (SqlBeanException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        } else if (conditionInfo.getSqlOperator() == SqlOperator.IN || conditionInfo.getSqlOperator() == SqlOperator.NOT_IN) {
            needEndBracket = true;
            Object[] in_notInValues = SqlBeanUtil.getObjectArray(value);
            StringBuffer in_notIn = new StringBuffer();
            if (in_notInValues != null && in_notInValues.length > 0) {
                for (int k = 0; k < in_notInValues.length; k++) {
                    in_notIn.append(SqlBeanUtil.getActualValue(common, in_notInValues[k]));
                    in_notIn.append(SqlConstant.COMMA);
                }
                in_notIn.deleteCharAt(in_notIn.length() - SqlConstant.COMMA.length());
                value = in_notIn.toString();
            }
        } else {
            value = conditionInfo.getValue();
            //对like操作符处理
            if (operator.indexOf(SqlConstant.LIKE) > -1) {
                if (conditionInfo.getSqlOperator() == SqlOperator.LIKE || conditionInfo.getSqlOperator() == SqlOperator.LIKE_L || conditionInfo.getSqlOperator() == SqlOperator.NOT_LIKE || conditionInfo.getSqlOperator() == SqlOperator.NOT_LIKE_L) {
                    value = SqlConstant.PERCENT + value;
                }
                if (conditionInfo.getSqlOperator() == SqlOperator.LIKE || conditionInfo.getSqlOperator() == SqlOperator.LIKE_R || conditionInfo.getSqlOperator() == SqlOperator.NOT_LIKE || conditionInfo.getSqlOperator() == SqlOperator.NOT_LIKE_R) {
                    value = value + SqlConstant.PERCENT;
                }
                value = SqlConstant.SINGLE_QUOTATION_MARK + value + SqlConstant.SINGLE_QUOTATION_MARK;
            } else {
                value = SqlBeanUtil.getActualValue(common, value);
            }
        }
        //列字段
        sql.append(SqlBeanUtil.getActualValue(common, conditionInfo.getColumn()));
        //操作符
        sql.append(operator);
        //列字段运算的值
        if (conditionInfo.getSqlOperator() == SqlOperator.BETWEEN) {
            sql.append(SqlBeanUtil.getActualValue(common, betweenValues[0]));
            sql.append(SqlConstant.AND);
            sql.append(SqlBeanUtil.getActualValue(common, betweenValues[1]));
        } else if (conditionInfo.getSqlOperator() == SqlOperator.IS_NULL || conditionInfo.getSqlOperator() == SqlOperator.IS_NOT_NULL) {
            sql.append("NULL ");
        } else {
            sql.append(value);
        }
        // in与not in 额外加结束括号
        if (needEndBracket) {
            sql.append(SqlConstant.END_BRACKET);
        }
        return sql;
    }

    /**
     * 返回MySQL,MariaDB,H2 分页语句
     *
     * @param select
     * @return
     */
    private static void mysqlPageDispose(Select select, StringBuffer sqlSb) {
        if (SqlBeanUtil.isUsePage(select)) {
            Integer[] param = pageParam(select);
            sqlSb.append(SqlConstant.LIMIT);
            sqlSb.append(param[0]);
            sqlSb.append(SqlConstant.COMMA);
            sqlSb.append(param[1]);
        }
    }

    /**
     * 返回Postgresql,Sqlite,Hsql 分页语句
     *
     * @param select
     * @return
     */
    private static void PostgresqlPageDispose(Select select, StringBuffer sqlSb) {
        if (SqlBeanUtil.isUsePage(select)) {
            Integer[] param = pageParam(select);
            sqlSb.append(SqlConstant.LIMIT);
            sqlSb.append(param[1]);
            sqlSb.append(SqlConstant.OFFSET);
            sqlSb.append(param[0]);
        }
    }

    /**
     * Oracle 分页处理
     *
     * @param select
     * @param sqlSb
     */
    private static void oraclePageDispose(Select select, StringBuffer sqlSb) {
        //oracle 分页语句前缀
        if (SqlBeanUtil.isUsePage(select)) {
            Integer[] param = pageParam(select);
            StringBuffer beginSqlSb = new StringBuffer();
            beginSqlSb.append(SqlConstant.SELECT + SqlConstant.ALL + SqlConstant.FROM + SqlConstant.BEGIN_BRACKET);
            beginSqlSb.append(SqlConstant.SELECT + SqlConstant.TB + SqlConstant.POINT + SqlConstant.ALL + SqlConstant.COMMA + SqlConstant.ROWNUM + SqlConstant.RN + SqlConstant.FROM + SqlConstant.BEGIN_BRACKET);
            sqlSb.insert(0, beginSqlSb);
            StringBuffer endSb = new StringBuffer();
            endSb.append(SqlConstant.END_BRACKET + SqlConstant.TB + SqlConstant.WHERE + SqlConstant.ROWNUM + SqlConstant.LESS_THAN_OR_EQUAL_TO);
            endSb.append(param[1]);
            endSb.append(SqlConstant.END_BRACKET + SqlConstant.WHERE + SqlConstant.RN + SqlConstant.GREATER_THAN);
            endSb.append(param[0]);
            sqlSb.append(endSb);
        }
    }

    /**
     * DB2 分页处理
     *
     * @param select
     * @param sqlSb
     */
    private static void db2PageDispose(Select select, StringBuffer sqlSb) {
        //db2 分页语句前缀
        if (SqlBeanUtil.isUsePage(select)) {
            Integer[] param = pageParam(select);
            StringBuffer beginSqlSb = new StringBuffer();
            beginSqlSb.append(SqlConstant.SELECT + SqlConstant.ALL + SqlConstant.FROM + SqlConstant.BEGIN_BRACKET);
            beginSqlSb.append(SqlConstant.SELECT + SqlConstant.T + SqlConstant.POINT + SqlConstant.ALL + SqlConstant.COMMA + SqlConstant.ROWNUMBER);
            beginSqlSb.append(SqlConstant.OVER + SqlConstant.BEGIN_BRACKET + SqlConstant.SPACES + SqlConstant.END_BRACKET + SqlConstant.AS + SqlConstant.RN + SqlConstant.FROM + SqlConstant.BEGIN_BRACKET);
            sqlSb.insert(0, beginSqlSb);
            StringBuffer endSb = new StringBuffer();
            endSb.append(SqlConstant.END_BRACKET + SqlConstant.T + SqlConstant.SPACES + SqlConstant.END_BRACKET + SqlConstant.TB);
            endSb.append(SqlConstant.WHERE + SqlConstant.BEGIN_BRACKET + SqlConstant.TB + SqlConstant.POINT + SqlConstant.RN + SqlConstant.LESS_THAN_OR_EQUAL_TO);
            endSb.append(param[1]);
            endSb.append(SqlConstant.AND + SqlConstant.TB + SqlConstant.POINT + SqlConstant.RN + SqlConstant.GREATER_THAN);
            endSb.append(param[0]);
            endSb.append(SqlConstant.END_BRACKET);
            sqlSb.append(endSb);
        }
    }

    /**
     * 返回Derby 分页语句
     *
     * @param select
     * @return
     */
    private static void derbyPageDispose(Select select, StringBuffer sqlSb) {
        if (SqlBeanUtil.isUsePage(select)) {
            Integer[] param = pageParam(select);
            sqlSb.append(SqlConstant.OFFSET);
            sqlSb.append(param[0]);
            sqlSb.append(" ROWS FETCH NEXT ");
            sqlSb.append(param[1]);
            sqlSb.append(" ROWS ONLY");
        }
    }

    /**
     * 各个数据库的分页参数
     *
     * @param select
     * @return
     */
    public static Integer[] pageParam(Select select) {
        //当前页不能小于0
        if (select.getPage().getPagenum() < 0) {
            throw new SqlBeanException("当前页不能小于0");
        }
        //每页数量不能小于0
        if (select.getPage().getPagesize() < 0) {
            throw new SqlBeanException("每页数量不能小于0");
        }
        Integer[] param;
        //SQLServer2008
        if (DbType.SQLServer == select.getSqlBeanMeta().getDbType()) {
            int pagenum = select.getPage().getStartByZero() ? select.getPage().getPagenum() + 1 : select.getPage().getPagenum() == 0 ? select.getPage().getPagenum() + 1 : select.getPage().getPagenum();
            int top = pagenum * select.getPage().getPagesize();
            int begin = top - select.getPage().getPagesize();
            param = new Integer[]{top, begin};
        }
        //Oracle,DB2
        else if (DbType.Oracle == select.getSqlBeanMeta().getDbType() || DbType.DB2 == select.getSqlBeanMeta().getDbType()) {
            //startIndex = (当前页 * 每页显示的数量)，例如：(0 * 10)
            //endIndex = (当前页 * 每页显示的数量) + 每页显示的数量，例如：10 = (0 * 10) + 10
            //那么如果startIndex=0，endIndex=10，就是查询第0到10条数据
            int pagenum = select.getPage().getStartByZero() ? select.getPage().getPagenum() : select.getPage().getPagenum() > 0 ? select.getPage().getPagenum() - 1 : select.getPage().getPagenum();
            int startIndex = pagenum * select.getPage().getPagesize();
            int endIndex = (pagenum * select.getPage().getPagesize()) + select.getPage().getPagesize();
            param = new Integer[]{startIndex, endIndex};
        }
        //Mysql,MariaDB,Postgresql,Sqlite,Hsql
        else {
            int pagenum = select.getPage().getStartByZero() ? select.getPage().getPagenum() : select.getPage().getPagenum() > 0 ? select.getPage().getPagenum() - 1 : select.getPage().getPagenum();
            int limitOffset = pagenum * select.getPage().getPagesize();
            int limitAmount = select.getPage().getPagesize();
            param = new Integer[]{limitOffset, limitAmount};
        }
        return param;
    }


}
