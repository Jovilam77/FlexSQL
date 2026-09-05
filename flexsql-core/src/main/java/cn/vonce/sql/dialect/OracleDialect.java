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
import cn.vonce.sql.enumerate.AlterDifference;
import cn.vonce.sql.enumerate.AlterType;
import cn.vonce.sql.enumerate.JavaMapOracleType;
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
 * Oracle方言
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/4/16 10:13
 */
public class OracleDialect extends AbstractDialect<JavaMapOracleType> {

    private static final Logger logger = Logger.getLogger(OracleDialect.class.getName());

    @Override
    public JavaMapOracleType getType(Field field) {
        Class<?> clazz = SqlBeanUtil.getEntityClassFieldType(field);
        for (JavaMapOracleType javaType : JavaMapOracleType.values()) {
            for (Class<?> thisClazz : javaType.getClasses()) {
                if (thisClazz == clazz) {
                    return javaType;
                }
            }
        }
        SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
        if (sqlJSON != null) {
            return JavaMapOracleType.JSON;
        }
        throw new SqlBeanException(field.getDeclaringClass().getName() + "，实体类不支持此字段类型：" + clazz.getSimpleName());
    }

    @Override
    public String getTableListSql(SqlBeanMeta sqlBeanMeta, String schema, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT t.table_name AS \"name\", c.comments AS \"remarks\" ");
        sql.append("FROM user_tables t ");
        sql.append("LEFT JOIN user_tab_comments c ");
        sql.append("ON c.table_name = t.table_name");
        if (StringUtil.isNotEmpty(tableName)) {
            sql.append(" AND t.table_name = '");
            sql.append((sqlBeanMeta.getSqlBeanConfig().getToUpperCase() != null && sqlBeanMeta.getSqlBeanConfig().getToUpperCase()) ? tableName.toUpperCase() : tableName);
            sql.append("'");
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
        Table table = alterList.get(0).getTable();
        StringBuilder addOrModifySql = new StringBuilder();
        addOrModifySql.append(SqlConstant.ALTER_TABLE);
        addOrModifySql.append(getFullName(alterList.get(0), table));
        for (int i = 0; i < alterList.size(); i++) {
            Alter alter = alterList.get(i);
            if (alter.getType() == AlterType.ADD) {
                addOrModifySql.append(SqlConstant.ADD);
                addOrModifySql.append(SqlConstant.BEGIN_BRACKET);
                addOrModifySql.append(SqlBeanUtil.addColumn(alter, alter.getColumnInfo(), null));
                addOrModifySql.append(SqlConstant.END_BRACKET);
                addOrModifySql.append(SqlConstant.SPACES);
                sqlList.add(addRemarks(false, alter, escape));
            } else if (alter.getType() == AlterType.MODIFY) {
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
                alter.getColumnInfo().setName(alter.getOldColumnName());
                addOrModifySql.append(modifyColumn(alter));
            }
        }
        //新增更改类型信息的语句需要先执行
        sqlList.add(0, addOrModifySql.toString());
        return sqlList;
    }

    /**
     * 更改列信息
     *
     * @param alter
     * @return
     */
    private String modifyColumn(Alter alter) {
        StringBuilder modifySql = new StringBuilder();
        modifySql.append(SqlConstant.MODIFY);
        modifySql.append(SqlConstant.BEGIN_BRACKET);
        ColumnInfo columnInfo = alter.getColumnInfo();
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
        if (alter.getDifferences().contains(AlterDifference.NOT_NULL)) {
            //是否为null
            if ((columnInfo.getNotnull() != null && columnInfo.getNotnull()) || columnInfo.getPk()) {
                modifySql.append(SqlConstant.SPACES);
                modifySql.append(SqlConstant.NOT_NULL);
            } else {
                if (alter.getType() == AlterType.MODIFY && columnInfo.getNotnull() != null && !columnInfo.getNotnull()) {
                    modifySql.append(SqlConstant.SPACES);
                    modifySql.append(SqlConstant.NULL);
                }
            }
        }
        //默认值
        if (StringUtil.isNotBlank(columnInfo.getDfltValue())) {
            modifySql.append(SqlConstant.SPACES);
            modifySql.append(SqlConstant.DEFAULT);
            modifySql.append(SqlConstant.SPACES);
            modifySql.append(SqlBeanUtil.getSqlValue(alter, columnInfo.getDfltValue(), jdbcType));
        }
        modifySql.append(SqlConstant.END_BRACKET);
        modifySql.append(SqlConstant.SPACES);
        return modifySql.toString();
    }

    @Override
    public void appendPageSuffix(StringBuilder sqlSb, Select select, String orderSql, Integer[] pageParam) {
        //Oracle count查询不进行分页处理
        if (select.isCount()) {
            return;
        }
        // Oracle 的 ROWNUM 分页外层包裹非 key-preserving，FOR UPDATE 不能作用于外层；
        // 因此将行锁子句注入到最内层查询（直接作用于基表，保持 key-preserving），
        // 外层 ROWNUM 仍负责 offset/limit 分页。
        String innerLock = buildLockClause(select);
        String sql = sqlSb.toString();
        sqlSb.setLength(0);
        sqlSb.append(SqlConstant.SELECT + SqlConstant.ALL + SqlConstant.FROM + SqlConstant.BEGIN_BRACKET);
        sqlSb.append(SqlConstant.SELECT + SqlConstant.TB + SqlConstant.POINT + SqlConstant.ALL + SqlConstant.COMMA + SqlConstant.ROWNUM + SqlConstant.RN + SqlConstant.FROM + SqlConstant.BEGIN_BRACKET);
        sqlSb.append(sql);
        if (innerLock != null) {
            sqlSb.append(innerLock);
        }
        sqlSb.append(SqlConstant.END_BRACKET + SqlConstant.TB + SqlConstant.WHERE + SqlConstant.ROWNUM + SqlConstant.LESS_THAN_OR_EQUAL_TO);
        sqlSb.append(pageParam[1]);
        sqlSb.append(SqlConstant.END_BRACKET + SqlConstant.WHERE + SqlConstant.RN + SqlConstant.GREATER_THAN);
        sqlSb.append(pageParam[0]);
    }

    @Override
    public void appendLockClause(StringBuilder sqlSb, Select select) {
        // 分页路径已在最内层查询注入行锁，避免重复追加
        if (sqlSb.toString().toUpperCase().contains(" FOR UPDATE")) {
            return;
        }
        String lock = buildLockClause(select);
        if (lock != null) {
            sqlSb.append(lock);
        }
    }

    /**
     * 构建 Oracle 行锁子句文本（含版本门控）。
     * <p>
     * - FOR UPDATE：所有 Oracle 版本均支持；
     * - FOR SHARE：Oracle 不支持（仅支持 FOR UPDATE），返回 null 并由内部告警；
     * - NOWAIT：所有 Oracle 版本均支持；
     * - SKIP LOCKED：需 Oracle 11g R1（major >= 11），
     *   版本未探测（major=0，未做数据库元数据探测）时按已支持处理直接下发。
     * 不支持时返回 null（由调用方跳过，并已在内部告警）。
     *
     * @param select 查询对象
     * @return 行锁子句（含前导空格），无锁或不支持时返回 null
     */
    private String buildLockClause(Select select) {
        LockType lockType = select.getLockType();
        if (lockType == null || lockType == LockType.NONE) {
            return null;
        }
        String base;
        if (lockType == LockType.FOR_UPDATE) {
            base = "FOR UPDATE";
        } else if (lockType == LockType.FOR_SHARE) {
            // Oracle 不支持 FOR SHARE（共享行锁），仅支持 FOR UPDATE
            logger.warning("Oracle 不支持 FOR SHARE（共享行锁），已忽略该锁子句（仅支持 FOR UPDATE）");
            return null;
        } else {
            return null;
        }
        StringBuilder lockSb = new StringBuilder();
        lockSb.append(SqlConstant.SPACES).append(base);
        // Oracle 的 FOR UPDATE OF 指定需锁定的表（多表 JOIN 场景）
        List<String> ofTables = select.getLockOfTables();
        if (ofTables != null && !ofTables.isEmpty()) {
            lockSb.append(" OF ").append(String.join(", ", ofTables));
        }
        LockWaitMode waitMode = select.getLockWaitMode();
        if (waitMode == LockWaitMode.NOWAIT) {
            lockSb.append(" NOWAIT");
        } else if (waitMode == LockWaitMode.SKIP_LOCKED) {
            int major = select.getSqlBeanMeta().getDatabaseMajorVersion();
            // Oracle 自 11g R1 (11.1) 起正式支持 SKIP LOCKED；
            // 版本未探测（major=0）时按已支持处理直接下发
            if (major > 0 && major < 11) {
                logger.warning("当前数据库（" + select.getSqlBeanMeta().getDbType().name() + " " + major
                        + "）不支持 SKIP LOCKED（需 Oracle 11g+），已忽略该锁子句");
                return null;
            }
            lockSb.append(" SKIP LOCKED");
        }
        return lockSb.toString();
    }

    @Override
    public String getSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT USERNAME AS \"name\" FROM ALL_USERS ");
        if (StringUtil.isNotEmpty(schemaName)) {
            sql.append("WHERE USERNAME = ");
            sql.append("'" + this.getSchemaName(sqlBeanMeta, schemaName) + "'");
        }
        return sql.toString();
    }

    @Override
    public String getCreateSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        return null;
    }

    @Override
    public String getDropSchemaSql(SqlBeanMeta sqlBeanMeta, String schemaName) {
        return null;
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
     * 构造 Oracle 的 UPSERT（以 MERGE INTO 实现）。
     * <p>
     * Oracle 没有 INSERT ... ON CONFLICT 语法，故用 {@code MERGE INTO t T USING (...) SRC ON (...) ...} 表达。
     * USING 源为多行 {@code SELECT <cells> FROM dual} 经 {@code UNION ALL} 拼接的派生表；
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
        // 构造 USING 源：每行 SELECT <cell> AS <field> FROM dual，多行用 UNION ALL 连接
        StringBuilder sourceSql = new StringBuilder();
        for (int r = 0; r < valueCells.size(); r++) {
            List<String> cells = valueCells.get(r);
            if (r > 0) {
                sourceSql.append(SqlConstant.UNION_ALL_SPACE);
            }
            sourceSql.append(SqlConstant.SELECT);
            for (int c = 0; c < cells.size(); c++) {
                sourceSql.append(cells.get(c)).append(SqlConstant.SPACES).append(SqlConstant.AS).append(fieldNames.get(c));
                if (c < cells.size() - 1) {
                    sourceSql.append(SqlConstant.COMMA);
                }
            }
            sourceSql.append(SqlConstant.FROM_DUAL);
        }
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
