package cn.vonce.sql.dialect;

import cn.vonce.sql.annotation.SqlJSON;
import cn.vonce.sql.bean.Alter;
import cn.vonce.sql.bean.Column;
import cn.vonce.sql.bean.ColumnInfo;
import cn.vonce.sql.bean.Select;
import cn.vonce.sql.bean.Upsert;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.AlterDifference;
import cn.vonce.sql.enumerate.AlterType;
import cn.vonce.sql.enumerate.JavaMapDB2Type;
import cn.vonce.sql.enumerate.JdbcType;
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
 * DB2方言
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/4/16 9:54
 */
public class DB2Dialect extends AbstractDialect<JavaMapDB2Type> {

    private static final Logger logger = Logger.getLogger(DB2Dialect.class.getName());

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

    @Override
    public boolean useRecursiveKeyword() {
        return false;
    }

    @Override
    public boolean useMergeForUpsert() {
        return true;
    }

    /**
     * 追加行锁子句（DB2 仅支持 FOR UPDATE；NOWAIT / SKIP LOCKED 需 11.5+，且 SKIP LOCKED 语法为 SKIP LOCKED DATA）。
     * <p>
     * DB2 无 FOR SHARE 语法，遇到直接告警忽略；SKIP LOCKED 在 DB2 中写作 {@code SKIP LOCKED DATA}（位置与标准不同）。
     * 版本未知（major=0）时按已支持处理。
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
        // DB2 仅支持 FOR UPDATE（无 FOR SHARE）
        String base;
        if (lockType == LockType.FOR_UPDATE) {
            base = "FOR UPDATE";
        } else if (lockType == LockType.FOR_SHARE) {
            logger.warning("DB2 不支持 FOR SHARE，已忽略该锁子句");
            return;
        } else {
            return;
        }
        StringBuilder lockSb = new StringBuilder();
        lockSb.append(SqlConstant.SPACES).append(base);
        // OF 表限制：仅锁定指定表（多表 JOIN 场景）
        List<String> ofTables = select.getLockOfTables();
        if (ofTables != null && !ofTables.isEmpty()) {
            lockSb.append(" OF ").append(String.join(", ", ofTables));
        }
        // 等待模式
        LockWaitMode waitMode = select.getLockWaitMode();
        if (waitMode == LockWaitMode.NOWAIT) {
            // DB2 11.5+ 支持 NOWAIT / WAIT n
            if (major > 0 && major < 11) {
                logger.warning("当前数据库（DB2 " + major
                        + "）不支持 NOWAIT（需 11.5+），已忽略该锁子句");
                return;
            }
            lockSb.append(" NOWAIT");
        } else if (waitMode == LockWaitMode.SKIP_LOCKED) {
            // DB2 语法为 SKIP LOCKED DATA（非 SKIP LOCKED），需 11.5+
            if (major > 0 && major < 11) {
                logger.warning("当前数据库（DB2 " + major
                        + "）不支持 SKIP LOCKED（需 11.5+），已忽略该锁子句");
                return;
            }
            lockSb.append(" SKIP LOCKED DATA");
        }
        sqlSb.append(lockSb);
    }

    /**
     * 构造 DB2 的 UPSERT（以 MERGE INTO 实现）。
     * <p>
     * DB2 11.5+ 支持标准 {@code MERGE INTO t T USING (VALUES (...),(...)) AS SRC (cols) ON (...) ...} 语法，
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
