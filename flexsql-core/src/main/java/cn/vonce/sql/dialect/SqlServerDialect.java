package cn.vonce.sql.dialect;

import cn.vonce.sql.annotation.SqlJSON;
import cn.vonce.sql.bean.Alter;
import cn.vonce.sql.bean.Column;
import cn.vonce.sql.bean.ColumnInfo;
import cn.vonce.sql.bean.Select;
import cn.vonce.sql.bean.Table;
import cn.vonce.sql.bean.Upsert;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.AlterType;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.enumerate.JavaMapSqlServerType;
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
 * SQLServer方言
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/4/16 10:09
 */
public class SqlServerDialect extends AbstractDialect<JavaMapSqlServerType> {

    private static final Logger logger = Logger.getLogger(SqlServerDialect.class.getName());

    @Override
    public JavaMapSqlServerType getType(Field field) {
        Class<?> clazz = SqlBeanUtil.getEntityClassFieldType(field);
        for (JavaMapSqlServerType javaType : JavaMapSqlServerType.values()) {
            for (Class<?> thisClazz : javaType.getClasses()) {
                if (thisClazz == clazz) {
                    return javaType;
                }
            }
        }
        SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
        if (sqlJSON != null) {
            return JavaMapSqlServerType.NVARCHAR;
        }
        throw new SqlBeanException(field.getDeclaringClass().getName() + "，实体类不支持此字段类型：" + clazz.getSimpleName());
    }

    @Override
    public String getTableListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT t.name, p.value AS remarks ");
        sql.append("FROM sys.tables t ");
        sql.append("INNER JOIN sys.schemas s ON s.schema_id = t.schema_id ");
        sql.append("LEFT JOIN sys.extended_properties p ");
        sql.append("ON p.major_id = t.object_id AND p.minor_id = 0 ");
        sql.append("WHERE t.type= 'U'");
        sql.append(" AND s.name = ");
        if (StringUtil.isNotEmpty(schema)) {
            sql.append("'");
            sql.append(schema);
            sql.append("'");
        } else {
            sql.append("USER_NAME()");
        }
        if (StringUtil.isNotEmpty(tableName)) {
            sql.append(" AND t.name = '" + tableName + "'");
        }
        return sql.toString();
    }

    @Override
    public String getColumnListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.cid, a.name, a.type, (CASE a.notnull WHEN 0 THEN 1 ELSE 0 END) AS notnull, ");
        sql.append("(CASE LEFT(constraint_name, 2) WHEN 'PK' THEN 1 ELSE 0 END) AS pk, ");
        sql.append("(CASE LEFT(constraint_name, 2) WHEN 'FK' THEN 1 ELSE 0 END) AS fk, ");
        sql.append("a.length, a.scale, c.value AS remarks ");
        sql.append("FROM (");
        sql.append("SELECT syscolumns.id, syscolumns.colid AS cid, syscolumns.name AS name, syscolumns.prec AS length, syscolumns.scale, systypes.name AS type, syscolumns.isnullable AS notnull, t.name AS table_name,s.name AS schemaName ");
        sql.append("FROM syscolumns ");
        sql.append("INNER JOIN systypes ON syscolumns.xusertype = systypes.xusertype ");
        sql.append("INNER JOIN sys.tables t ON t.object_id = syscolumns.id ");
        sql.append("INNER JOIN sys.schemas s ON s.schema_id = t.schema_id ");
        sql.append("WHERE s.name = ");
        sql.append(StringUtil.isNotBlank(schema) ? "'" + schema + "'" : "USER_NAME()");
        sql.append("AND t.name = '");
        sql.append(tableName);
        sql.append("') a ");
        sql.append("LEFT JOIN information_schema.key_column_usage b ON a.name = b.column_name AND a.table_name = b.table_name AND b.table_schema = a.schemaName ");
        sql.append("LEFT JOIN sys.extended_properties c ON c.major_id = a.id AND c.minor_id = a.cid ");
        sql.append("ORDER BY a.cid");
        return sql.toString();
    }

    @Override
    public List<String> alterTable(List<Alter> alterList) {
        List<String> sqlList = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        StringBuilder remarksSql = new StringBuilder();
        for (int i = 0; i < alterList.size(); i++) {
            Alter alter = alterList.get(i);
            if (alter.getType() == AlterType.ADD) {
                sql.append(SqlConstant.ALTER_TABLE);
                sql.append(getFullName(alter, alter.getTable(), null));
                sql.append(SqlConstant.ADD);
                sql.append(SqlBeanUtil.addColumn(alter, alter.getColumnInfo(), alter.getAfterColumnName()));
                remarksSql.append(addRemarks(false, alter, null));
            } else if (alter.getType() == AlterType.CHANGE) {
                //改名
                sql.append(SqlConstant.EXEC_SP_RENAME);
                sql.append(getFullName(alter, alter.getTable(), alter.getOldColumnName()));
                sql.append(SqlConstant.COMMA);
                sql.append(SqlConstant.SINGLE_QUOTATION_MARK);
                sql.append(alter.getColumnInfo().getName());
                sql.append(SqlConstant.SINGLE_QUOTATION_MARK);
                sql.append(SqlConstant.COMMA);
                sql.append(SqlConstant.SINGLE_QUOTATION_MARK);
                sql.append(SqlConstant.COLUMN);
                sql.append(SqlConstant.SINGLE_QUOTATION_MARK);
                sql.append(SqlConstant.SPACES);
                sql.append(SqlConstant.SEMICOLON);
                //先改名后修改信息
                StringBuilder modifySql = modifyColumn(alter);
                if (modifySql.length() > 0) {
                    sql.append(SqlConstant.ALTER_TABLE);
                    sql.append(modifySql);
                }
                remarksSql.append(addRemarks(false, alter, null));
            } else if (alter.getType() == AlterType.MODIFY) {
                sql.append(SqlConstant.ALTER_TABLE);
                sql.append(modifyColumn(alter));
                remarksSql.append(addRemarks(false, alter, null));
            } else if (alter.getType() == AlterType.DROP) {
                sql.append(SqlConstant.ALTER_TABLE);
                sql.append(getFullName(alter, alter.getTable(), null));
                sql.append(SqlConstant.DROP);
                sql.append(SqlConstant.COLUMN);
                sql.append(SqlConstant.BEGIN_SQUARE_BRACKETS);
                sql.append(alter.getColumnInfo().getName(SqlBeanUtil.isToUpperCase(alter)));
                sql.append(SqlConstant.END_SQUARE_BRACKETS);
            }
            sql.append(SqlConstant.SPACES);
            sql.append(SqlConstant.SEMICOLON);
        }
        sqlList.add(sql.toString());
        sqlList.add(remarksSql.toString());
        return sqlList;
    }

    /**
     * 获取全名
     *
     * @param alter
     * @param table
     * @param columnName
     * @return
     */
    private String getFullName(Alter alter, Table table, String columnName) {
        StringBuilder sql = new StringBuilder();
        boolean rename = alter.getType() == AlterType.CHANGE && StringUtil.isNotBlank(columnName);
        if (rename) {
            sql.append(SqlConstant.SINGLE_QUOTATION_MARK);
        }
        if (StringUtil.isNotBlank(table.getSchema())) {
            sql.append(SqlConstant.BEGIN_SQUARE_BRACKETS);
            sql.append(table.getSchema());
            sql.append(SqlConstant.END_SQUARE_BRACKETS);
            sql.append(SqlConstant.POINT);
        }
//        sql.append(SqlConstant.BEGIN_SQUARE_BRACKETS);
//        sql.append(SqlConstant.DBO);
//        sql.append(SqlConstant.END_SQUARE_BRACKETS);
//        sql.append(SqlConstant.POINT);
        sql.append(SqlConstant.BEGIN_SQUARE_BRACKETS);
        sql.append(table.getName(SqlBeanUtil.isToUpperCase(alter)));
        sql.append(SqlConstant.END_SQUARE_BRACKETS);
        if (rename) {
            sql.append(SqlConstant.POINT);
            sql.append(SqlConstant.BEGIN_SQUARE_BRACKETS);
            sql.append(columnName);
            sql.append(SqlConstant.END_SQUARE_BRACKETS);
            sql.append(SqlConstant.SINGLE_QUOTATION_MARK);
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
        String fullName = getFullName(alter, alter.getTable(), null);
        modifySql.append(fullName);
        modifySql.append(SqlConstant.ALTER);
        modifySql.append(SqlConstant.COLUMN);
        JdbcType jdbcType = JdbcType.getType(columnInfo.getType());
        modifySql.append(SqlBeanUtil.getTableFieldName(alter, columnInfo.getName()));
        modifySql.append(SqlConstant.SPACES);
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
        //是否为null
        if ((columnInfo.getNotnull() != null && columnInfo.getNotnull()) || columnInfo.getPk()) {
            modifySql.append(SqlConstant.SPACES);
            modifySql.append(SqlConstant.NOT_NULL);
        } else {
            if (columnInfo.getNotnull() != null && !columnInfo.getNotnull()) {
                modifySql.append(SqlConstant.SPACES);
                modifySql.append(SqlConstant.NULL);
            }
        }
        //是否自增
        if (columnInfo.getAutoIncr() != null && columnInfo.getAutoIncr()) {
            if (alter.getSqlBeanMeta().getDbType() == DbType.MySQL || alter.getSqlBeanMeta().getDbType() == DbType.MariaDB) {
                modifySql.append(SqlConstant.SPACES);
                modifySql.append(SqlConstant.AUTO_INCREMENT);
            }
        }
        return modifySql;
    }

    @Override
    public String addRemarks(boolean isTable, Alter item, String escape) {
        String remarks = StringUtil.isNotBlank(item.getColumnInfo().getRemarks()) ? item.getColumnInfo().getRemarks() : "''";
        StringBuilder remarksSql = new StringBuilder();
        String schema = SqlConstant.DBO;
        if (StringUtil.isNotBlank(item.getTable().getSchema())) {
            schema = item.getTable().getSchema();
        }
        remarksSql.append("IF ((SELECT COUNT(*) FROM ::fn_listextendedproperty(");
        remarksSql.append("'MS_Description', 'SCHEMA', N'" + schema + "', 'TABLE', N'" + item.getTable().getName() + (!isTable ? "', 'COLUMN', N'" + item.getColumnInfo().getName() + "'" : "', NULL, NULL"));
        remarksSql.append(")) > 0)");
        remarksSql.append("\n  EXEC sp_updateextendedproperty ");
        remarksSql.append("'MS_Description', N'" + remarks + "', 'SCHEMA', N'" + schema + "', 'TABLE', N'" + item.getTable().getName() + "'" + (!isTable ? (", 'COLUMN', N'" + item.getColumnInfo().getName() + "'") : ""));
        remarksSql.append("\nELSE");
        remarksSql.append("\n  EXEC sp_addextendedproperty ");
        remarksSql.append("'MS_Description', N'" + remarks + "', 'SCHEMA', N'" + schema + "', 'TABLE', N'" + item.getTable().getName() + "'" + (!isTable ? (", 'COLUMN', N'" + item.getColumnInfo().getName() + "'") : ""));
        remarksSql.append(SqlConstant.SEMICOLON);
        return remarksSql.toString();
    }

    @Override
    public void appendPageBeforePrefix(StringBuilder sqlSb, Select select, String orderSql, Integer[] pageParam) {
        sqlSb.append(SqlConstant.SELECT);
        sqlSb.append(SqlConstant.ALL);
        sqlSb.append(SqlConstant.FROM);
        sqlSb.append(SqlConstant.BEGIN_BRACKET);
    }

    @Override
    public void appendPageAfterSelect(StringBuilder sqlSb, Select select, String orderSql, Integer[] pageParam) {
        sqlSb.append(SqlConstant.TOP);
        sqlSb.append(pageParam[0]);
        sqlSb.append(SqlConstant.ROW_NUMBER + SqlConstant.OVER + SqlConstant.BEGIN_BRACKET + orderSql + SqlConstant.END_BRACKET + SqlConstant.ROWNUM + SqlConstant.COMMA);
    }

    @Override
    public void appendPageSuffix(StringBuilder sqlSb, Select select, String orderSql, Integer[] pageParam) {
        sqlSb.append(SqlConstant.END_BRACKET);
        sqlSb.append(SqlConstant.T);
        sqlSb.append(SqlConstant.WHERE);
        sqlSb.append(SqlConstant.T + SqlConstant.POINT + SqlConstant.ROWNUM);
        sqlSb.append(SqlConstant.GREATER_THAN);
        sqlSb.append(pageParam[1]);
    }

    /**
     * 表级锁提示（SQL Server 用 WITH (UPDLOCK[, READPAST | NOWAIT]) 实现行锁语义）
     * <p>
     * SQL Server 没有 FOR UPDATE / FOR SHARE 语法，而是用表提示表达悲观行锁：
     * - FOR_UPDATE / FOR_SHARE → WITH (UPDLOCK)            （对已读取行加更新锁，阻塞直至锁释放）
     * - NOWAIT                → WITH (UPDLOCK, NOWAIT)     （获取不到锁立即报错）
     * - SKIP_LOCKED           → WITH (UPDLOCK, READPAST)   （跳过已被其它事务锁定的行）
     * <p>
     * FOR SHARE 在 SQL Server 无严格等价语义，统一用 UPDLOCK 表达并提示。
     * 表提示必须紧贴 FROM / JOIN 后的表名，因此由 buildSelectSql 在拼接表名后立即调用本方法
     * （主表与每个 join 表都会各自注入一次，符合 SQL Server 表提示语法）。
     * UPDLOCK / NOWAIT / READPAST 在所有受支持的 SQL Server 版本中均可用，无需版本门控；
     * 版本未探测（major=0）时同样直接下发，与 MySQL/PostgreSQL 的处理口径一致。
     * 由于锁已通过表提示注入，appendLockClause（尾部子句钩子）对 SQL Server 保持默认 no-op。
     * 注：OF 表限制在 SQL Server 表提示中无对应写法，故此处忽略 lockOfTables。
     *
     * @param sqlSb  SQL构建器
     * @param select 查询对象
     */
    @Override
    public void appendTableHint(StringBuilder sqlSb, Select select) {
        LockType lockType = select.getLockType();
        if (lockType == null || lockType == LockType.NONE) {
            return;
        }
        // FOR SHARE 在 SQL Server 无直接等价，统一映射为 UPDLOCK
        LockWaitMode waitMode = select.getLockWaitMode();
        sqlSb.append(SqlConstant.SPACES);
        sqlSb.append("WITH ");
        sqlSb.append(SqlConstant.BEGIN_BRACKET);
        if (waitMode == LockWaitMode.SKIP_LOCKED) {
            sqlSb.append("UPDLOCK, READPAST");
        } else if (waitMode == LockWaitMode.NOWAIT) {
            sqlSb.append("UPDLOCK, NOWAIT");
        } else {
            sqlSb.append("UPDLOCK");
        }
        sqlSb.append(SqlConstant.END_BRACKET);
    }

    @Override
    public String getSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT name FROM sys.schemas ");
        if (StringUtil.isNotEmpty(schemaName)) {
            sql.append("WHERE name = ");
            sql.append("'" + this.getSchemaName(sqlBeanMeta, schemaName) + "'");
        }
        return sql.toString();
    }

    @Override
    public String getCreateSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        StringBuilder sql = new StringBuilder();
        sql.append("IF NOT EXISTS (SELECT name FROM sys.schemas WHERE name = N'");
        sql.append(this.getSchemaName(sqlBeanMeta, schemaName));
        sql.append("') BEGIN EXEC ('CREATE SCHEMA [");
        sql.append(this.getSchemaName(sqlBeanMeta, schemaName));
        sql.append("]'); END");
        return sql.toString();
    }

    @Override
    public String getDropSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        StringBuilder sql = new StringBuilder();
        sql.append("IF EXISTS (SELECT name FROM sys.schemas WHERE name = N'");
        sql.append(this.getSchemaName(sqlBeanMeta, schemaName));
        sql.append("') BEGIN EXEC ('DROP SCHEMA [");
        sql.append(this.getSchemaName(sqlBeanMeta, schemaName));
        sql.append("]'); END");
        return sql.toString();
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
     * 构造 SQL Server 的 UPSERT（以 MERGE INTO 实现）。
     * <p>
     * SQL Server 没有 INSERT ... ON CONFLICT 语法，故用 {@code MERGE INTO t T USING (...) SRC ON (...) ...} 表达。
     * USING 源为多行表值构造器 {@code (VALUES (...), (...)) AS SRC (col1, col2, ...)}；
     * ON 匹配条件优先取 {@code onConflict} 指定的列，未指定时回退到主键。
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
        // valueRows 每个元素已是 (v1, v2, ...) 形式，直接拼接为 VALUES 构造器
        StringBuilder sourceSql = new StringBuilder();
        sourceSql.append(SqlConstant.BEGIN_BRACKET).append(SqlConstant.VALUES)
                .append(String.join(SqlConstant.COMMA, valueRows))
                .append(SqlConstant.END_BRACKET).append(SqlConstant.SPACES).append("AS SRC").append(SqlConstant.SPACES);
        sourceSql.append(SqlConstant.BEGIN_BRACKET);
        sourceSql.append(String.join(SqlConstant.COMMA, fieldNames));
        sourceSql.append(SqlConstant.END_BRACKET);
        // ON 匹配条件：冲突列优先，否则回退主键
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
     * @return ON 条件文本（如 T.[id] = SRC.[id]）；无可用匹配列时返回 null
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
