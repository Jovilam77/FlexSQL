package cn.vonce.sql.dialect;

import cn.vonce.sql.annotation.SqlJSON;
import cn.vonce.sql.bean.Alter;
import cn.vonce.sql.bean.Select;
import cn.vonce.sql.bean.Table;
import cn.vonce.sql.bean.Upsert;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.AlterType;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.enumerate.JavaMapMySqlType;
import cn.vonce.sql.enumerate.LockType;
import cn.vonce.sql.enumerate.LockWaitMode;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.helper.SqlHelper;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Mysql方言
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/4/16 9:41
 */
public class MysqlDialect extends AbstractDialect<JavaMapMySqlType> {

    private static final Logger logger = Logger.getLogger(MysqlDialect.class.getName());

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
    public void appendLockClause(StringBuilder sqlSb, Select select) {
        LockType lockType = select.getLockType();
        if (lockType == null || lockType == LockType.NONE) {
            return;
        }
        int major = select.getSqlBeanMeta().getDatabaseMajorVersion();
        // MySQL 8.0+ 支持 FOR SHARE / NOWAIT / SKIP LOCKED；5.x 仅支持 FOR UPDATE
        String base;
        if (lockType == LockType.FOR_UPDATE) {
            base = "FOR UPDATE";
        } else if (lockType == LockType.FOR_SHARE) {
            if (major > 0 && major < 8) {
                logger.warning("当前数据库（" + select.getSqlBeanMeta().getDbType().name() + " " + major
                        + "）不支持 FOR SHARE（需 MySQL 8.0+），已忽略该锁子句");
                return;
            }
            base = "FOR SHARE";
        } else {
            return;
        }
        StringBuilder lockSb = new StringBuilder();
        lockSb.append(SqlConstant.SPACES).append(base);
        // MySQL 8.0+ 的 FOR UPDATE / FOR SHARE OF 接受【表名/别名】（有别名时必须用别名）
        List<String> ofTables = select.getLockOfTables();
        if (ofTables != null && !ofTables.isEmpty()) {
            lockSb.append(" OF ").append(String.join(", ", ofTables));
        } else if (select.getLockOfColumns() != null && !select.getLockOfColumns().isEmpty()) {
            logger.warning("MySQL 的 OF 子句需要【表名/别名】，ofColumns(...) 已被忽略；请改用 of(表名...)");
        }
        // 等待模式
        LockWaitMode waitMode = select.getLockWaitMode();
        if (waitMode == LockWaitMode.NOWAIT) {
            if (major > 0 && major < 8) {
                logger.warning("当前数据库（" + select.getSqlBeanMeta().getDbType().name() + " " + major
                        + "）不支持 NOWAIT（需 MySQL 8.0+），已忽略该锁子句");
                return;
            }
            lockSb.append(" NOWAIT");
        } else if (waitMode == LockWaitMode.SKIP_LOCKED) {
            if (major > 0 && major < 8) {
                logger.warning("当前数据库（" + select.getSqlBeanMeta().getDbType().name() + " " + major
                        + "）不支持 SKIP LOCKED（需 MySQL 8.0+），已忽略该锁子句");
                return;
            }
            lockSb.append(" SKIP LOCKED");
        }
        sqlSb.append(lockSb);
    }

    @Override
    public void appendUpsertSuffix(StringBuilder sqlSb, Upsert<?> upsert, List<String> fieldNames, List<String> valueRows) {
        if (upsert.isDoNothing()) {
            // MySQL 无原生 DO NOTHING，已在 SqlHelper.buildUpsertSql 中切换为 INSERT IGNORE，此处不再追加后缀
            return;
        }
        List<String> assigns = SqlHelper.buildUpsertAssignments(upsert, fieldNames, "",
                col -> SqlConstant.VALUES_FUNC + SqlConstant.BEGIN_BRACKET + col + SqlConstant.END_BRACKET);
        if (assigns.isEmpty()) {
            logger.warning("UPSERT 未指定任何更新项（setAll/set/doNothing），MySQL 下已忽略 ON DUPLICATE KEY UPDATE，降级为普通 INSERT");
            return;
        }
        sqlSb.append(SqlConstant.ON_DUPLICATE_KEY_UPDATE);
        sqlSb.append(String.join(SqlConstant.COMMA, assigns));
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
