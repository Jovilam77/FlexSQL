package cn.vonce.sql.dialect;

import cn.vonce.sql.annotation.SqlJSON;
import cn.vonce.sql.bean.Alter;
import cn.vonce.sql.bean.Column;
import cn.vonce.sql.bean.Select;
import cn.vonce.sql.bean.Upsert;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.AlterType;
import cn.vonce.sql.enumerate.JavaMapHsqlType;
import cn.vonce.sql.enumerate.LockType;
import cn.vonce.sql.enumerate.LockWaitMode;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.helper.SqlHelper;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Hsql方言
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/4/16 10:19
 */
public class HsqlDialect extends AbstractDialect<JavaMapHsqlType> {

    private static final Logger logger = Logger.getLogger(HsqlDialect.class.getName());

    @Override
    public JavaMapHsqlType getType(Field field) {
        Class<?> clazz = SqlBeanUtil.getEntityClassFieldType(field);
        for (JavaMapHsqlType javaType : JavaMapHsqlType.values()) {
            for (Class<?> thisClazz : javaType.getClasses()) {
                if (thisClazz == clazz) {
                    return javaType;
                }
            }
        }
        SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
        if (sqlJSON != null) {
            return JavaMapHsqlType.VARCHAR;
        }
        throw new SqlBeanException(field.getDeclaringClass().getName() + "，实体类不支持此字段类型：" + clazz.getSimpleName());
    }

    @Override
    public String getTableListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT t.TABLE_SCHEMA AS schema, t.TABLE_NAME AS name, sc.COMMENT AS remarks ");
        sql.append("FROM information_schema.tables t ");
        sql.append("LEFT JOIN INFORMATION_SCHEMA.SYSTEM_COMMENTS sc ");
        sql.append("ON sc.OBJECT_NAME = t.TABLE_NAME AND sc.OBJECT_TYPE = 'TABLE' ");
        sql.append("WHERE TABLE_TYPE = 'BASE TABLE'");
        sql.append(" AND TABLE_SCHEMA = ");
        if (StringUtil.isNotEmpty(schema)) {
            sql.append("'" + schema + "'");
        } else {
            sql.append("'PUBLIC'");
        }
        if (StringUtil.isNotEmpty(tableName)) {
            sql.append(" AND table_name = '" + tableName + "'");
        }
        return sql.toString();
    }

    @Override
    public String getColumnListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT cl.ORDINAL_POSITION AS cid, ");
        sql.append("cl.COLUMN_NAME AS name,");
        sql.append("cl.DTD_IDENTIFIER AS type, ");
        sql.append("CASE WHEN cl.IS_NULLABLE  = 'NO' THEN 1 ELSE 0 END AS notnull, ");
        sql.append("cl.COLUMN_DEFAULT AS dflt_value, ");
        sql.append("cl.CHARACTER_MAXIMUM_LENGTH AS length, ");
        sql.append("cl.NUMERIC_SCALE AS scale, ");
        sql.append("CASE WHEN kcu.TABLE_NAME = cl.TABLE_NAME AND kcu.POSITION_IN_UNIQUE_CONSTRAINT is null THEN 1 ELSE 0 END AS pk, ");
        sql.append("CASE WHEN kcu.TABLE_NAME = cl.TABLE_NAME AND kcu.POSITION_IN_UNIQUE_CONSTRAINT = 1 THEN 1 ELSE 0 END AS fk, ");
        sql.append("sc.COMMENT AS remarks ");
        sql.append("FROM INFORMATION_SCHEMA.COLUMNS cl ");
        sql.append("LEFT JOIN INFORMATION_SCHEMA.SYSTEM_COMMENTS sc ");
        sql.append("ON sc.OBJECT_NAME = cl.TABLE_NAME AND sc.COLUMN_NAME = cl.COLUMN_NAME ");
        sql.append("LEFT JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu ");
        sql.append("ON kcu.TABLE_NAME = cl.TABLE_NAME AND kcu.COLUMN_NAME = cl.COLUMN_NAME ");
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
        //Hsql count查询不进行分页处理
        if (select.isCount()) {
            return;
        }
        sqlSb.append(SqlConstant.LIMIT);
        sqlSb.append(pageParam[1]);
        sqlSb.append(SqlConstant.OFFSET);
        sqlSb.append(pageParam[0]);
    }

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

    @Override
    public boolean useMergeForUpsert() {
        return true;
    }

    /**
     * 追加行锁子句（HSQLDB 2.x 仅支持 FOR UPDATE / FOR UPDATE NOWAIT，不支持 FOR SHARE / SKIP LOCKED）。
     * <p>
     * FOR SHARE、SKIP LOCKED 在本方言无对应语法，直接告警并忽略；NOWAIT 需 2.3.3+。版本未知（major=0）时按已支持处理。
     *
     * @param sqlSb  SQL 构建器
     * @param select 查询对象
     */
    @Override
    public void appendLockClause(StringBuilder sqlSb, Select select) {
        LockType lockType = select.getLockType();
        if (lockType == null || lockType == LockType.NONE) {
            return;
        }
        int major = select.getSqlBeanMeta().getDatabaseMajorVersion();
        int minor = select.getSqlBeanMeta().getDatabaseMinorVersion();
        // HSQLDB 仅支持 FOR UPDATE（无 FOR SHARE 语法）
        String base;
        if (lockType == LockType.FOR_UPDATE) {
            base = "FOR UPDATE";
        } else if (lockType == LockType.FOR_SHARE) {
            logger.warning("HSQLDB 不支持 FOR SHARE，已忽略该锁子句");
            return;
        } else {
            return;
        }
        StringBuilder lockSb = new StringBuilder();
        lockSb.append(SqlConstant.SPACES).append(base);
        // HSQLDB 的 FOR UPDATE 不接 OF 子句，生成 "FOR UPDATE OF x" 会语法错误。忽略 OF 并告警。
        if ((select.getLockOfTables() != null && !select.getLockOfTables().isEmpty())
                || (select.getLockOfColumns() != null && !select.getLockOfColumns().isEmpty())) {
            logger.warning("HSQLDB 不支持 FOR UPDATE OF 子句（仅 FOR UPDATE），of(...)/ofColumns(...) 已被忽略");
        }
        // 等待模式
        LockWaitMode waitMode = select.getLockWaitMode();
        if (waitMode == LockWaitMode.NOWAIT) {
            // HSQLDB 2.3.3+ 支持 FOR UPDATE NOWAIT
            if (major > 0 && (major < 2 || (major == 2 && minor < 3))) {
                logger.warning("当前数据库（HSQLDB " + major + "." + minor
                        + "）不支持 NOWAIT（需 2.3.3+），已忽略该锁子句");
                return;
            }
            lockSb.append(" NOWAIT");
        } else if (waitMode == LockWaitMode.SKIP_LOCKED) {
            logger.warning("HSQLDB 不支持 SKIP LOCKED，已忽略该锁子句");
            return;
        }
        sqlSb.append(lockSb);
    }

    /**
     * 构造 HSQLDB 的 UPSERT（以 MERGE INTO 实现）。
     * <p>
     * HSQLDB 支持标准 {@code MERGE INTO t T USING (VALUES (...),(...)) AS SRC (cols) ON (...) ...} 语法，
     * 与 SQL Server 同构，直接复用 {@link SqlHelper#buildMergeSql} 公共辅助。
     *
     * @param upsert      UPSERT 对象
     * @param tableName   目标表名（已按本方言格式化）
     * @param fieldNames  已转义的列名列表
     * @param valueRows   每行值表达式列表，元素形如 {@code (v1, v2, ...)}
     * @param valueCells  每行的值单元列表（与 fieldNames 一一对应）
     * @return 完整 MERGE SQL；无法生成（如缺匹配列）时返回 null 交由调用方降级
     */
    @Override
    public String buildMergeSql(Upsert<?> upsert, String tableName, List<String> fieldNames,
                                List<String> valueRows, List<List<String>> valueCells) {
        String escape = SqlBeanUtil.getEscape(upsert);
        boolean toUpper = SqlBeanUtil.isToUpperCase(upsert);
        // 构造 USING 源：表值构造器 (VALUES (cells), ...) AS SRC (fieldNames)
        StringBuilder sourceSql = new StringBuilder();
        sourceSql.append(SqlConstant.BEGIN_BRACKET).append(SqlConstant.VALUES)
                .append(String.join(SqlConstant.COMMA, valueRows))
                .append(SqlConstant.END_BRACKET).append(SqlConstant.SPACES).append("AS SRC").append(SqlConstant.SPACES);
        sourceSql.append(SqlConstant.BEGIN_BRACKET);
        sourceSql.append(String.join(SqlConstant.COMMA, fieldNames));
        sourceSql.append(SqlConstant.END_BRACKET);
        String onClause = buildMergeOnClause(upsert, escape, toUpper);
        if (onClause == null) {
            return null;
        }
        return SqlHelper.buildMergeSql(upsert, tableName, fieldNames, sourceSql.toString(), onClause);
    }

    /**
     * 构造 MERGE 的 ON 匹配条件（T.col = SRC.col）。
     * 优先使用 {@code onConflict} 指定的列；未指定时回退到实体类主键。
     *
     * @return ON 条件文本（如 T."id" = SRC."id"）；无可用匹配列时返回 null
     */
    private String buildMergeOnClause(Upsert<?> upsert, String escape, boolean toUpper) {
        List<Column> conflict = upsert.getConflictColumns();
        if (conflict == null || conflict.isEmpty()) {
            // 未显式指定冲突列，回退到主键
            try {
                Field idField = SqlBeanUtil.getIdField(upsert.getBeanClass());
                conflict = Collections.singletonList(SqlBeanUtil.getColumnByField(idField, upsert.getBeanClass()));
            } catch (SqlBeanException e) {
                logger.warning("UPSERT 未指定 onConflict 且无法解析主键，无法生成 MERGE 的 ON 匹配条件（" + e.getMessage() + "），已降级为普通 INSERT");
                return null;
            }
        }
        StringBuilder on = new StringBuilder();
        for (int i = 0; i < conflict.size(); i++) {
            String col = escape + conflict.get(i).getName(toUpper) + escape;
            on.append("T").append(SqlConstant.POINT).append(col)
                    .append(SqlConstant.EQUAL_TO)
                    .append("SRC").append(SqlConstant.POINT).append(col);
            if (i < conflict.size() - 1) {
                on.append(SqlConstant.AND);
            }
        }
        return on.toString();
    }

}
