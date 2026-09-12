package cn.vonce.sql.helper;

import cn.vonce.sql.annotation.*;
import cn.vonce.sql.define.SqlEnum;
import cn.vonce.sql.define.SqlFun;
import cn.vonce.sql.uitls.ReflectUtil;
import cn.vonce.sql.uitls.StringUtil;
import cn.vonce.sql.bean.*;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.dialect.SqlDialect;
import cn.vonce.sql.enumerate.*;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.provider.DynSchemaContextHolder;
import cn.vonce.sql.provider.TenantContextHolder;
import cn.vonce.sql.uitls.SqlBeanUtil;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * SQL 语句助手
 *
 * @author jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2017年6月2日下午5:41:59
 */
public class SqlHelper {

    private static final Logger logger = Logger.getLogger(SqlHelper.class.getName());

    /**
     * 生成select sql语句
     *
     * @param select
     * @return
     */
    public static String buildSelectSql(Select select) {
        SqlBeanUtil.check(select);
        // 动态Schema（多租户）：渲染期统一解析（主表 FROM 与 UNION 子查询均复用），非法则抛异常
        String dynSchema = resolveDynamicSchema();
        StringBuilder sqlSb = new StringBuilder();
        Integer[] pageParam = null;
        String orderSql = orderBySql(select);
        SqlDialect dialect = select.getSqlBeanMeta().getDbType().getSqlDialect();

        //计算分页参数
        if (SqlBeanUtil.isUsePage(select)) {
            pageParam = pageParam(select);
        }

        //方言分页前缀（在SELECT关键字之前插入，如SQL Server的 SELECT ALL FROM ( 外层包裹）
        if (pageParam != null) {
            dialect.appendPageBeforePrefix(sqlSb, select, orderSql, pageParam);
        }

        // CTE（WITH 子句）前缀，必须位于 SELECT 关键字之前，且在分页外层包裹之外
        // （SQL Server 分页用 insert(0, "SELECT ALL FROM (") 前置包裹，CTE 需在其外，故在本行前置于 0）
        dialect.appendCtePrefix(sqlSb, select);

        //标准Sql
        sqlSb.append(select.isDistinct() ? SqlConstant.SELECT_DISTINCT : SqlConstant.SELECT);

        //方言分页SELECT列前缀（在SELECT关键字之后、列字段列表之前插入）
        if (pageParam != null) {
            dialect.appendPageAfterSelect(sqlSb, select, orderSql, pageParam);
        }

        // 是否含 UNION / UNION ALL 分支。count 查询同样需要保留分支，否则分页总数只统计主查询。
        boolean hasUnionBranches = select.getUnionSelects() != null && !select.getUnionSelects().isEmpty();
        // count + union：内层必须渲染原始列（而不是 COUNT(*)），再由最外层包一层 COUNT(*)，
        // 与 count+distinct 的包裹方式同构；否则会退化成「两个 COUNT(*) 的 UNION」→ 返回多行而非总数。
        boolean countOverUnion = select.isCount() && hasUnionBranches && !select.isDistinct();

        //标准Sql
        if (select.isCount() && !select.isDistinct() && !countOverUnion) {
            sqlSb.append(SqlConstant.COUNT + SqlConstant.BEGIN_BRACKET + SqlConstant.ALL + SqlConstant.END_BRACKET);
        } else {
            sqlSb.append(column(select));
        }
        if (!select.isNoFrom()) {
            sqlSb.append(SqlConstant.FROM);
            String baseSchema = dynSchema != null ? dynSchema : select.getTable().getSchema();
            sqlSb.append(SqlBeanUtil.fromFullName(baseSchema, select.getTable().getName(), select.getTable().getAlias(), select));
            // 表级锁提示（如 SQL Server 的 WITH (UPDLOCK, READPAST)），紧贴主表名之后注入
            dialect.appendTableHint(sqlSb, select);
            // RIGHT / FULL JOIN 的「行保留侧」租户条件不能放进 ON（不匹配行仍会被保留，导致其它租户行泄漏），
            // 由 joinSql 收集后并入外层 WHERE
            StringBuilder joinTenantWhere = new StringBuilder();
            sqlSb.append(joinSql(select, dialect, joinTenantWhere));
            String where = whereSql(select, null);
            if (joinTenantWhere.length() > 0) {
                if (where.isEmpty()) {
                    sqlSb.append(SqlConstant.WHERE);
                    sqlSb.append(joinTenantWhere);
                } else {
                    sqlSb.append(where);
                    sqlSb.append(SqlConstant.AND);
                    sqlSb.append(joinTenantWhere);
                }
            } else {
                sqlSb.append(where);
            }
        }
        String groupBySql = groupBySql(select);
        sqlSb.append(groupBySql);
        sqlSb.append(havingSql(select));
        // UNION / UNION ALL 子查询：必须紧接主体 SELECT、位于 ORDER BY / 分页 / 行锁之前。
        // 原实现把它们放在 ORDER BY/LIMIT/行锁之后，会生成
        //   "... WHERE ... ORDER BY x LIMIT 0,10 FOR UPDATE UNION SELECT ..."
        // → MySQL 报 Incorrect usage of UNION and ORDER BY/LIMIT，PostgreSQL 报 syntax error at or near "UNION"。
        // ORDER BY / LIMIT / OFFSET 按 SQL 标准语义属于整条 UNION 结果，故排在所有分支之后；
        // 分支只有在自身带 ORDER BY/分页/行锁时才补括号（SQLite 的复合查询 grammar 不接受带括号的操作数）。
        // 关联子查询主表同样覆盖动态 schema（与 setSchema 主表逻辑一致，纯渲染期覆盖）
        if (hasUnionBranches) {
            for (int i = 0; i < select.getUnionSelects().size(); i++) {
                Select unionSelect = select.getUnionSelects().get(i);
                if (unionSelect.getSqlBeanMeta() == null) {
                    unionSelect.setSqlBeanMeta(select.getSqlBeanMeta());
                }
                if (dynSchema != null && unionSelect.getTable() != null) {
                    unionSelect.getTable().setSchema(dynSchema);
                }
                boolean wrap = needsUnionBranchParens(unionSelect);
                sqlSb.append(select.getUnionAlls().get(i) ? SqlConstant.UNION_ALL : SqlConstant.UNION);
                if (wrap) {
                    sqlSb.append(SqlConstant.BEGIN_BRACKET);
                }
                sqlSb.append(SqlHelper.buildSelectSql(unionSelect));
                if (wrap) {
                    sqlSb.append(SqlConstant.END_BRACKET);
                }
            }
        }
        if (!select.isCount()) {
            sqlSb.append(orderSql);
        }
        //方言分页后缀（在COUNT包裹之前处理，以保持SQL Server原始行为）
        if (pageParam != null) {
            dialect.appendPageSuffix(sqlSb, select, orderSql, pageParam);
        }
        //行锁子句（必须在 LIMIT/OFFSET 之后、COUNT 包裹之前追加；count 查询不加锁）
        if (!select.isCount()) {
            if (hasUnionBranches && select.getLockType() != null && select.getLockType() != LockType.NONE) {
                // PostgreSQL 明确禁止 FOR UPDATE/FOR SHARE 与 UNION 同用；MySQL 也无法对 UNION 结果加锁。
                // 直接忽略锁子句，避免生成必然报错的 SQL。
                logger.warning("UNION 查询不支持行锁（FOR UPDATE / FOR SHARE），已忽略该锁子句");
            } else {
                dialect.appendLockClause(sqlSb, select);
            }
        }
        //标准Sql 如果是克隆的select则为分页时的count
        if ((select.isCount() && select.isDistinct())
                || (select.isCount() && StringUtil.isNotEmpty(groupBySql))
                || countOverUnion) {
            sqlSb.insert(0, SqlConstant.SELECT + SqlConstant.COUNT + SqlConstant.BEGIN_BRACKET + SqlConstant.ALL + SqlConstant.END_BRACKET + SqlConstant.FROM + SqlConstant.BEGIN_BRACKET);
            sqlSb.append(SqlConstant.END_BRACKET + SqlConstant.AS + SqlConstant.T);
        }
        return sqlSb.toString();
    }

    /**
     * UNION 分支是否需要括号包裹。
     * <p>SQL 标准规定 ORDER BY / LIMIT 只能出现在整条复合查询的末尾，但各数据库对「分支自带
     * ORDER BY / LIMIT」的容忍度不同：MySQL / PostgreSQL 接受 {@code (SELECT ... ORDER BY ...)}，
     * 而 SQLite 的复合查询 grammar 只允许 {@code select-core} 作为操作数，完全不允许括号。
     * 因此只在分支自身确实带排序 / 分页 / 行锁时才加括号，其余情况输出最朴素的 {@code a UNION b} 形式，
     * 保证 SQLite 也能通过。</p>
     *
     * @param unionSelect UNION 分支
     * @return 是否需要括号包裹
     */
    private static boolean needsUnionBranchParens(Select unionSelect) {
        if (unionSelect == null) {
            return false;
        }
        boolean hasOrder = unionSelect.getOrderBy() != null && !unionSelect.getOrderBy().isEmpty();
        boolean hasPage = unionSelect.getPage() != null;
        boolean hasLock = unionSelect.getLockType() != null && unionSelect.getLockType() != LockType.NONE;
        return hasOrder || hasPage || hasLock;
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
        StringBuilder sqlSb = new StringBuilder();
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
            logger.warning("Failed to build insert SQL: " + e.getMessage());
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
        StringBuilder sqlSb = new StringBuilder();
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
        StringBuilder sqlSb = new StringBuilder();
        sqlSb.append(SqlConstant.CREATE_TABLE);
        sqlSb.append(SqlBeanUtil.getTableName(create.getTable(), create));
        sqlSb.append(SqlConstant.BEGIN_BRACKET);
        List<Field> fieldList = SqlBeanUtil.getBeanAllField(create.getBeanClass());
        SqlTable sqlTable = SqlBeanUtil.getSqlTable(create.getBeanClass());
        DbType dbType = create.getSqlBeanMeta().getDbType();
        Class<?> constantClass = SqlBeanUtil.getConstantClass(create.getBeanClass());
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
        StringBuilder backupSql = new StringBuilder();
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
        StringBuilder copySql = new StringBuilder();
        StringBuilder columnSql = new StringBuilder();
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
            StringBuilder targetColumnSql = new StringBuilder();
            for (Column column : copy.getTargetColumns()) {
                targetColumnSql.append(column.getName());
                targetColumnSql.append(SqlConstant.COMMA);
            }
            targetColumnSql.delete(targetColumnSql.length() - SqlConstant.COMMA.length(), targetColumnSql.length());
            copySql.append(targetColumnSql);
        } else if (columnSql.length() > 0) {
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
        // 校验表名和schema名（仅允许安全标识符字符，防止SQL注入）
        validateTableName(drop.getTable());

        StringBuilder dropSql = new StringBuilder();
        String tableName = SqlBeanUtil.getTableName(drop.getTable(), drop);
        if (drop.getSqlBeanMeta().getDbType() == DbType.MySQL || drop.getSqlBeanMeta().getDbType() == DbType.MariaDB || drop.getSqlBeanMeta().getDbType() == DbType.Postgresql || drop.getSqlBeanMeta().getDbType() == DbType.H2) {
            dropSql.append("DROP TABLE IF EXISTS ");
            dropSql.append(tableName);
        } else if (drop.getSqlBeanMeta().getDbType() == DbType.SQLServer) {
            dropSql.append("IF OBJECT_ID(N'" + tableName + "', N'U') IS NOT NULL ");
            dropSql.append("DROP TABLE " + tableName + " ");
        } else {
            dropSql.append("DROP TABLE ");
            dropSql.append(tableName);
        }
        return dropSql.toString();
    }

    /**
     * 校验表名和schema名是否安全
     * <p>
     * 只允许字母、数字、下划线、$、#等安全标识符字符，防止SQL注入。
     *
     * @param table 表信息
     * @throws SqlBeanException 当表名或schema名包含非法字符时抛出
     */
    private static void validateTableName(Table table) {
        String schema = table.getSchema();
        String name = table.getName();
        if (StringUtil.isNotEmpty(schema) && !schema.matches("[a-zA-Z_$#][a-zA-Z0-9_$#]*")) {
            throw new SqlBeanException("Schema name in DROP statement contains invalid characters: " + schema);
        }
        if (StringUtil.isNotEmpty(name) && !name.matches("[a-zA-Z_$#][a-zA-Z0-9_$#]*")) {
            throw new SqlBeanException("Table name in DROP statement contains invalid characters: " + name);
        }
    }

    /**
     * 返回column语句
     *
     * @param select
     * @return
     */
    private static String column(Select select) {
        StringBuilder columnSql = new StringBuilder();
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
     * @param select         查询对象
     * @param dialect        方言
     * @param deferredWhere  OUT 参数：无法放进 ON 的租户条件（RIGHT/FULL JOIN 的行保留侧），
     *                       由调用方并入外层 WHERE；不需要时传 null
     * @return JOIN 子句
     */
    private static String joinSql(Select select, SqlDialect dialect, StringBuilder deferredWhere) {
        StringBuilder joinSql = new StringBuilder();
        if (select != null && select.getJoin().size() != 0) {
            // 动态Schema（多租户）优先级最高，覆盖关联表 schema（与 setSchema 主表逻辑一致）
            String dynSchema = resolveDynamicSchema();
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
                // 关联表 schema：动态 schema 存在时覆盖（注解 join / 流式 join 均覆盖）
                String schema = dynSchema != null ? dynSchema : join.getSchema();
                String tableName = join.getTableName();
                String tableAlias = join.getTableAlias();
                joinSql.append(SqlBeanUtil.fromFullName(schema, tableName, tableAlias, select));
                // 表级锁提示同样紧贴每个 join 表名之后注入
                dialect.appendTableHint(joinSql, select);
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
                // 关联表行级多租户隔离。放置位置取决于该关联表是否为「行保留侧」：
                //   - INNER / LEFT JOIN：关联表是非保留侧，条件放 ON 既完成过滤、又不破坏保留侧的行（放 WHERE 反而会退化成内连接语义）；
                //   - RIGHT JOIN：关联表是保留侧；ON 只决定匹配与否，不匹配的关联表行仍会保留（左表列补 NULL），
                //     其它租户的行会因此出现在结果里 → 必须并入外层 WHERE 才能真正过滤；
                //   - FULL JOIN：两侧都是保留侧，同理必须放 WHERE。
                String joinTenantSql = joinTenantCondition(join, select);
                if (!joinTenantSql.isEmpty()) {
                    boolean preservingSide = join.getJoinType() == JoinType.RIGHT_JOIN
                            || join.getJoinType() == JoinType.FULL_JOIN;
                    if (preservingSide && deferredWhere != null) {
                        if (deferredWhere.length() > 0) {
                            deferredWhere.append(SqlConstant.AND);
                        }
                        deferredWhere.append(joinTenantSql);
                    } else {
                        joinSql.append(SqlConstant.AND);
                        joinSql.append(joinTenantSql);
                    }
                }
            }
        }
        return joinSql.toString();
    }

    /**
     * 解析动态Schema（多租户上下文），并做合法性校验以防 SQL 注入。
     * 返回 null 表示未设置动态 schema。
     */
    private static String resolveDynamicSchema() {
        String dynSchema = DynSchemaContextHolder.getSchema();
        if (StringUtil.isEmpty(dynSchema)) {
            return null;
        }
        if (!SqlBeanUtil.isValidSqlIdentifier(dynSchema)) {
            throw new SqlBeanException("非法的动态Schema名称（可能存在SQL注入风险）：" + dynSchema);
        }
        return dynSchema;
    }

    /**
     * 关联表租户隔离条件：仅对 @SqlJoin 关联实体（joinClass 可内省 @SqlTenantId）生效，
     * 在 ON 子句追加 (joinAlias.tenant_id = <上下文租户>)，与主表 tenantCondition 同构。
     * 流式裸表 join 无实体类（joinClass 为 null），不追加。
     */
    private static String joinTenantCondition(Join join, Select select) {
        Class<?> clazz = join.getJoinClass();
        if (clazz == null || !SqlBeanUtil.checkTenant(clazz)) {
            return "";
        }
        Object tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return "";
        }
        Field tenantField = SqlBeanUtil.getTenantField(clazz);
        if (tenantField == null) {
            return "";
        }
        SqlTable sqlTable = SqlBeanUtil.getSqlTable(clazz);
        String columnName = SqlBeanUtil.getTableFieldName(tenantField, sqlTable);
        StringBuilder sb = new StringBuilder();
        sb.append(SqlConstant.BEGIN_BRACKET);
        sb.append(SqlBeanUtil.getTableFieldFullName(select, join.getTableAlias(), columnName));
        sb.append(SqlConstant.EQUAL_TO);
        sb.append(SqlBeanUtil.getSqlValue(select, tenantId));
        sb.append(SqlConstant.END_BRACKET);
        return sb.toString();
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
        List<?> objectList = insert.getBean();
        List<Field> fieldList = SqlBeanUtil.getBeanAllField(insert.getBeanClass());
        StringBuilder sql = new StringBuilder();
        appendInsertPrefix(sql, insert, objectList);
        StringBuilder fieldSql = new StringBuilder();
        List<String> valueSqlList = buildInsertValues(insert, objectList, fieldList, fieldSql, null, null);
        assembleInsertBody(sql, tableName, fieldSql, valueSqlList, insert, objectList);
        return sql.toString();
    }

    /**
     * 追加INSERT前缀（Oracle多行使用INSERT ALL INTO）
     */
    private static void appendInsertPrefix(StringBuilder sql, Insert insert, List<?> objectList) {
        if (insert.getSqlBeanMeta().getDbType() == DbType.Oracle && objectList != null && objectList.size() > 1) {
            sql.append(SqlConstant.INSERT_ALL_INTO);
        } else {
            sql.append(SqlConstant.INSERT_INTO);
        }
    }

    /**
     * 构建字段SQL和值SQL列表（选择Bean模式或Column模式）
     * <p>
     * fieldNames / valueCells 可为 null：传入时分别收集「已转义列名列表」与「每行值单元列表」，供 UPSERT 复用。
     */
    private static List<String> buildInsertValues(Insert insert, List<?> objectList,
                                                  List<Field> fieldList, StringBuilder fieldSql,
                                                  List<String> fieldNames, List<List<String>> valueCells) {
        StringBuilder valueSql = new StringBuilder();
        List<String> valueSqlList = new ArrayList<>();
        if (objectList != null && !objectList.isEmpty()) {
            SqlTable sqlTable = SqlBeanUtil.getSqlTable(insert.getBeanClass());
            buildBeanValues(insert, objectList, fieldList, sqlTable, fieldSql, valueSql, valueSqlList, fieldNames, valueCells);
        } else {
            buildColumnValues(insert, fieldSql, valueSql, valueSqlList, fieldNames, valueCells);
        }
        return valueSqlList;
    }

    /**
     * Bean模式：从实体对象列表构建字段SQL和值SQL
     */
    private static void buildBeanValues(Insert insert, List<?> objectList, List<Field> fieldList,
                                        SqlTable sqlTable, StringBuilder fieldSql,
                                        StringBuilder valueSql, List<String> valueSqlList,
                                        List<String> fieldNames, List<List<String>> valueCells) {
        for (int i = 0; i < objectList.size(); i++) {
            //每次必须清空
            valueSql.delete(0, valueSql.length());
            List<String> rowCells = valueCells != null ? new ArrayList<>() : null;
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
                        if (fieldNames != null) {
                            fieldNames.add(tableFieldName);
                        }
                    }
                }
                if (sqlId != null && sqlId.type() == IdType.AUTO) {
                    continue;
                }
                Object value = ReflectUtil.instance().get(objectList.get(i).getClass(), objectList.get(i), field.getName());
                if (sqlJSON != null) {
                    value = SqlBeanUtil.getJSONValue(sqlJSON, value);
                }
                // 租户字段：始终以上下文租户ID覆盖，业务层不可指定租户（防止越权写入）
                if (field.isAnnotationPresent(SqlTenantId.class)) {
                    Object ctxTenant = TenantContextHolder.getTenantId();
                    if (ctxTenant != null) {
                        String tenantCell = SqlBeanUtil.getSqlValue(insert, ctxTenant, jdbcType);
                        valueSql.append(tenantCell);
                        valueSql.append(SqlConstant.COMMA);
                        if (rowCells != null) {
                            rowCells.add(tenantCell);
                        }
                        continue;
                    }
                }
                String cell;
                //如果此字段为id且需要生成唯一id
                if (sqlId != null && sqlId.type() != IdType.AUTO && sqlId.type() != IdType.NORMAL) {
                    if (StringUtil.isEmpty(value)) {
                        value = insert.getSqlBeanMeta().getSqlBeanConfig().getUniqueIdProcessor().uniqueId(sqlId.type());
                        ReflectUtil.instance().set(objectList.get(i).getClass(), objectList.get(i), field.getName(), value);
                    }
                    cell = SqlBeanUtil.getSqlValue(insert, value, jdbcType);
                } else if (field.isAnnotationPresent(SqlLogically.class) && value == null) {
                    //如果标识逻辑删除的字段为空则自动填充
                    Object defaultValue = SqlBeanUtil.assignInitialValue(SqlBeanUtil.getEntityClassFieldType(field));
                    cell = SqlBeanUtil.getSqlValue(insert, defaultValue, jdbcType);
                    ReflectUtil.instance().set(objectList.get(i).getClass(), objectList.get(i), field.getName(), field.getType() == Boolean.class || field.getType() == boolean.class ? false : 0);
                } else if (value == null && sqlDefaultValue != null && (sqlDefaultValue.with() == FillWith.INSERT || sqlDefaultValue.with() == FillWith.TOGETHER)) {
                    Object defaultValue = SqlHelper.setDefaultValue(objectList.get(i).getClass(), objectList.get(i), field);
                    cell = SqlBeanUtil.getSqlValue(insert, defaultValue, jdbcType);
                } else {
                    cell = SqlBeanUtil.getSqlValue(insert, value, jdbcType);
                }
                valueSql.append(cell);
                valueSql.append(SqlConstant.COMMA);
                if (rowCells != null) {
                    rowCells.add(cell);
                }
            }
            valueSql.deleteCharAt(valueSql.length() - SqlConstant.COMMA.length());
            valueSql.append(SqlConstant.END_BRACKET);
            valueSqlList.add(valueSql.toString());
            if (rowCells != null) {
                valueCells.add(rowCells);
            }
            //只有在循环第一遍的时候才会处理
            if (i == 0) {
                fieldSql.deleteCharAt(fieldSql.length() - SqlConstant.COMMA.length());
                fieldSql.append(SqlConstant.END_BRACKET);
            }
        }
    }

    /**
     * Column模式：从显式指定的列和值列表构建字段SQL和值SQL
     */
    private static void buildColumnValues(Insert insert, StringBuilder fieldSql,
                                          StringBuilder valueSql, List<String> valueSqlList,
                                          List<String> fieldNames, List<List<String>> valueCells) {
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
            String tableFieldName = SqlBeanUtil.getTableFieldName(insert, columnList.get(i).getName());
            fieldSql.append(tableFieldName);
            if (fieldNames != null) {
                fieldNames.add(tableFieldName);
            }
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
            List<String> rowCells = valueCells != null ? new ArrayList<>() : null;
            valueSql.append(SqlConstant.BEGIN_BRACKET);
            for (int j = 0; j < valueList.size(); j++) {
                String cell = SqlBeanUtil.getSqlValue(insert, valueList.get(j));
                valueSql.append(cell);
                if (rowCells != null) {
                    rowCells.add(cell);
                }
                if (j < columnList.size() - 1) {
                    valueSql.append(SqlConstant.COMMA);
                }
            }
            valueSql.append(SqlConstant.END_BRACKET);
            valueSqlList.add(valueSql.toString());
            if (rowCells != null) {
                valueCells.add(rowCells);
            }
        }
    }

    /**
     * UPSERT 解析结果（字段名与值单元），供 INSERT 系方言后缀与 MERGE 系方言整句构造复用
     */
    private static class InsertParts {
        String tableName;
        List<String> fieldNames;          // 已按方言转义的列名
        List<String> valueRows;           // 每行值表达式，元素形如 (v1, v2, ...)
        List<List<String>> valueCells;    // 每行的值单元列表（MERGE 源构造用）
    }

    /**
     * 解析 UPSERT 的字段名与值（复用 INSERT 的字段/值构建逻辑）
     */
    private static InsertParts buildUpsertParts(Upsert<?> upsert) {
        InsertParts parts = new InsertParts();
        // 解析目标表名：优先取显式 setTable，未设置时回退到实体类 @SqlTable 注解
        Table table = upsert.getTable();
        if (table == null || StringUtil.isEmpty(table.getName())) {
            table = SqlBeanUtil.getTable(upsert.getBeanClass());
        }
        parts.tableName = SqlBeanUtil.getTableName(table, upsert);
        List<?> objectList = upsert.getBean();
        List<Field> fieldList = objectList != null && !objectList.isEmpty()
                ? SqlBeanUtil.getBeanAllField(upsert.getBeanClass()) : null;
        StringBuilder fieldSql = new StringBuilder();
        parts.fieldNames = new ArrayList<>();
        parts.valueCells = new ArrayList<>();
        parts.valueRows = buildInsertValues(upsert, objectList, fieldList, fieldSql, parts.fieldNames, parts.valueCells);
        return parts;
    }

    /**
     * 生成 UPSERT（存在则更新，不存在则插入）SQL 语句
     * <p>
     * 按方言分派：
     * - INSERT 系（MySQL / PostgreSQL / SQLite）：{@code INSERT ...} + {@code appendUpsertSuffix} 后缀；
     * - MERGE 系（Oracle / SQL Server）：由 {@code buildMergeSql} 直接产出完整 MERGE 语句。
     *
     * @param upsert UPSERT 对象
     * @return UPSERT SQL
     */
    public static String buildUpsertSql(Upsert<?> upsert) {
        SqlBeanUtil.check(upsert);
        InsertParts parts = buildUpsertParts(upsert);
        DbType dbType = upsert.getSqlBeanMeta().getDbType();
        SqlDialect dialect = dbType.getSqlDialect();

        if (dialect.useMergeForUpsert()) {
            String mergeSql = dialect.buildMergeSql(upsert, parts.tableName, parts.fieldNames, parts.valueRows, parts.valueCells);
            if (mergeSql != null) {
                return mergeSql;
            }
            // 方言未实现 MERGE 时降级为普通 INSERT，避免产生无效 SQL
            logger.warning("方言 " + dbType.name() + " 声明使用 MERGE 但未实现 buildMergeSql，UPSERT 降级为普通 INSERT");
        }

        // INSERT 系方言：INSERT INTO t (cols) VALUES (...)[, (...)] + 后缀
        StringBuilder sql = new StringBuilder();
        boolean doNothing = upsert.isDoNothing();
        if ((dbType == DbType.MySQL || dbType == DbType.MariaDB) && doNothing) {
            // MySQL 无 DO NOTHING，用 INSERT IGNORE 表达「冲突则跳过」
            sql.append("INSERT IGNORE INTO ");
        } else {
            sql.append(SqlConstant.INSERT_INTO);
        }
        sql.append(parts.tableName);
        sql.append(SqlConstant.BEGIN_BRACKET);
        sql.append(String.join(SqlConstant.COMMA, parts.fieldNames));
        sql.append(SqlConstant.END_BRACKET);
        sql.append(SqlConstant.VALUES);
        sql.append(String.join(SqlConstant.COMMA, parts.valueRows));
        dialect.appendUpsertSuffix(sql, upsert, parts.fieldNames, parts.valueRows);
        return sql.toString();
    }

    /**
     * 构建 UPSERT 冲突时的更新赋值片段（形如 "col = expr"，未用逗号连接）。
     * <p>
     * 三种来源的赋值都会被纳入：
     * - 字面量：{@code set(col, value)} → {@code targetPrefix + col = <value>}
     * - 引用待插入值：{@code setAll()}（排除冲突列）/ {@code set(col)} → {@code targetPrefix + col = refExpr(col)}
     *
     * @param upsert       UPSERT 对象
     * @param fieldNames   已转义列名（用于 setAll 排除冲突列的判断）
     * @param targetPrefix 更新目标前缀（INSERT 系为空串；MERGE 系为 "T." 等）
     * @param refExpr      引用「待插入值」的表达式生成器（传入已转义列名，返回如 VALUES(col) / EXCLUDED.col / SRC.col）
     * @return 赋值片段列表
     */
    public static List<String> buildUpsertAssignments(Upsert<?> upsert, List<String> fieldNames,
                                              String targetPrefix, Function<String, String> refExpr) {
        List<String> assigns = new ArrayList<>();
        String escape = SqlBeanUtil.getEscape(upsert);
        boolean toUpper = SqlBeanUtil.isToUpperCase(upsert);
        // 1) 显式字面量赋值（用户显式声明的目标列，不做过滤）
        for (SetInfo setInfo : upsert.getUpdateSetList()) {
            String col = escape + setInfo.getName(toUpper) + escape;
            Object val = SqlBeanUtil.getActualValue(upsert, setInfo.getValue());
            assigns.add(targetPrefix + col + SqlConstant.EQUAL_TO + val);
        }
        // 2) 需以「待插入值」更新的列
        Set<String> refCols = new LinkedHashSet<>();
        if (upsert.isUpdateAll()) {
            List<String> conflictEscaped = new ArrayList<>();
            for (Column c : upsert.getConflictColumns()) {
                conflictEscaped.add(escape + c.getName(toUpper) + escape);
            }
            for (String f : fieldNames) {
                if (!conflictEscaped.contains(f)) {
                    refCols.add(f);
                }
            }
        }
        for (Column c : upsert.getReferenceColumns()) {
            refCols.add(escape + c.getName(toUpper) + escape);
        }
        // 3) 过滤只读审计字段与租户字段（口径与常规 UPDATE 的 SET 子句一致，见 setSql）。
        //    否则 setAll() 会生成 create_by = SRC.create_by / create_time = SRC.create_time，
        //    导致每次 upsert 已存在的行都覆盖创建人/创建时间，并可能写入租户列。
        Set<String> skipped = upsertSkippedRefColumns(upsert, escape, toUpper);
        for (String f : refCols) {
            if (skipped.contains(f)) {
                continue;
            }
            assigns.add(targetPrefix + f + SqlConstant.EQUAL_TO + refExpr.apply(f));
        }
        return assigns;
    }

    /**
     * 收集 UPSERT 冲突更新分支中应跳过的列（返回已转义的列名，便于与 fieldNames 直接比较）。
     * <p>只读审计字段（{@code @SqlDefaultValue(readonly = true)}）与租户字段（{@code @SqlTenantId}）
     * 不应被「待插入值」覆盖，与 {@link #setSql(Update)} 中常规 UPDATE 的过滤口径保持一致。</p>
     *
     * @param upsert  UPSERT 对象
     * @param escape  转义符
     * @param toUpper 是否转大写
     * @return 应跳过的已转义列名集合；无实体类信息时返回空集合
     */
    private static Set<String> upsertSkippedRefColumns(Upsert<?> upsert, String escape, boolean toUpper) {
        Set<String> skipped = new LinkedHashSet<>();
        Class<?> beanClass = upsert.getBeanClass();
        if (beanClass == null) {
            return skipped;
        }
        Table table = SqlBeanUtil.getTable(beanClass);
        SqlTable sqlTable = SqlBeanUtil.getSqlTable(beanClass);
        for (Field field : SqlBeanUtil.getBeanAllField(beanClass)) {
            if (SqlBeanUtil.isIgnore(field)) {
                continue;
            }
            SqlDefaultValue sqlDefaultValue = field.getAnnotation(SqlDefaultValue.class);
            boolean readonly = sqlDefaultValue != null && sqlDefaultValue.readonly();
            boolean tenant = field.isAnnotationPresent(SqlTenantId.class);
            if (!readonly && !tenant) {
                continue;
            }
            Column column = SqlBeanUtil.getTableColumn(field, table, sqlTable);
            if (column != null) {
                skipped.add(escape + column.getName(toUpper) + escape);
            }
        }
        return skipped;
    }

    /**
     * 组装完整 MERGE 语句（Oracle / SQL Server 等 MERGE 系方言通用）。
     * <p>
     * sourceSql 已是完整的 USING 源表达式（含别名），由方言自行构造。
     *
     * @param upsert      UPSERT 对象（用于判断是否 doNothing / 取更新项）
     * @param tableName   目标表名
     * @param fieldNames  已转义列名
     * @param sourceSql   USING 源表达式（含别名）
     * @param onClause    ON 匹配条件（如 T."id" = SRC."id"）
     * @return 完整 MERGE SQL
     */
    public static String buildMergeSql(Upsert<?> upsert, String tableName, List<String> fieldNames,
                               String sourceSql, String onClause) {
        StringBuilder sql = new StringBuilder();
        sql.append(SqlConstant.MERGE_INTO).append(tableName).append(SqlConstant.SPACES).append("T")
                .append(SqlConstant.USING).append(SqlConstant.BEGIN_BRACKET).append(sourceSql).append(SqlConstant.END_BRACKET);
        sql.append(SqlConstant.ON).append(SqlConstant.BEGIN_BRACKET).append(onClause).append(SqlConstant.END_BRACKET);
        // WHEN MATCHED THEN UPDATE SET ...
        if (!upsert.isDoNothing()) {
            List<String> assigns = buildUpsertAssignments(upsert, fieldNames, "T" + SqlConstant.POINT,
                    col -> "SRC" + SqlConstant.POINT + col);
            if (!assigns.isEmpty()) {
                sql.append(SqlConstant.WHEN_MATCHED).append(String.join(SqlConstant.COMMA, assigns));
            }
        }
        // WHEN NOT MATCHED THEN INSERT (fields) VALUES (SRC.f, ...)
        sql.append(SqlConstant.WHEN_NOT_MATCHED).append(SqlConstant.BEGIN_BRACKET)
                .append(String.join(SqlConstant.COMMA, fieldNames)).append(SqlConstant.END_BRACKET)
                .append(SqlConstant.VALUES).append(SqlConstant.BEGIN_BRACKET);
        List<String> insertVals = new ArrayList<>();
        for (String f : fieldNames) {
            insertVals.add("SRC" + SqlConstant.POINT + f);
        }
        sql.append(String.join(SqlConstant.COMMA, insertVals)).append(SqlConstant.END_BRACKET);
        return sql.toString();
    }

    /**
     * 组装完整的INSERT语句（Oracle走多表插入语法）
     */
    private static void assembleInsertBody(StringBuilder sql, String tableName, StringBuilder fieldSql,
                                           List<String> valueSqlList, Insert insert, List<?> objectList) {
        if (insert.getSqlBeanMeta().getDbType() == DbType.Oracle) {
            for (int k = 0; k < valueSqlList.size(); k++) {
                if (k > 0) {
                    sql.append(SqlConstant.INTO);
                }
                sql.append(tableName);
                sql.append(fieldSql);
                sql.append(SqlConstant.VALUES);
                sql.append(valueSqlList.get(k));
            }
            if (objectList != null && objectList.size() > 1) {
                sql.append(SqlConstant.SELECT_DUAL);
            }
        } else {
            for (int k = 0; k < valueSqlList.size(); k++) {
                if (k == 0) {
                    sql.append(tableName);
                    sql.append(fieldSql);
                    sql.append(SqlConstant.VALUES);
                }
                sql.append(valueSqlList.get(k));
                sql.append(SqlConstant.COMMA);
            }
            sql.deleteCharAt(sql.length() - SqlConstant.COMMA.length());
        }
    }

    /**
     * 返回set语句
     *
     * @param update
     * @return
     */
    private static String setSql(Update update) {
        StringBuilder setSql = new StringBuilder();
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
                // 只读审计字段（如 createTime / createBy）：UPDATE 的 SET 子句中跳过，防止业务层误改创建信息
                if (sqlDefaultValue != null && sqlDefaultValue.readonly()) {
                    continue;
                }
                // 租户字段：UPDATE 的 SET 子句中跳过，防止业务层误改租户归属
                if (field.isAnnotationPresent(SqlTenantId.class)) {
                    continue;
                }
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
                    // 不支持的类型 setDefaultValue 返回 null，回落到字段原值，避免把有效数据误写为 NULL
                    Object fillValue = (defaultValue == null) ? objectValue : defaultValue;
                    setSql.append(SqlBeanUtil.getSqlValue(update, fillValue));
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
        SqlDefaultValue sqlDefaultValue = field.getAnnotation(SqlDefaultValue.class);
        Class<?> fieldType = field.getType();
        // 注入当前操作人（createBy / updateBy 等）
        if (sqlDefaultValue != null && sqlDefaultValue.user()) {
            Object userValue = SqlBeanUtil.getCurrentUser(fieldType);
            // 未注册解析器 / 解析失败时不填充，由调用方回落（UPDATE 用原值，INSERT 写 NULL，与字段本身为 null 等价）
            if (userValue == null) {
                return null;
            }
            if (SqlEnum.class.isAssignableFrom(fieldType)) {
                SqlEnum sqlEnum = (userValue instanceof SqlEnum) ? (SqlEnum) userValue : SqlBeanUtil.matchEnum(field, userValue);
                if (sqlEnum == null) {
                    sqlEnum = ((SqlEnum[]) fieldType.getEnumConstants())[0];
                }
                ReflectUtil.instance().set(clazz, bean, field.getName(), sqlEnum);
                return sqlEnum.getCode();
            }
            ReflectUtil.instance().set(clazz, bean, field.getName(), userValue);
            return userValue;
        }
        // 原有逻辑：按字段类型赋予默认值
        Object defaultValue = SqlBeanUtil.assignInitialValue(SqlBeanUtil.getEntityClassFieldType(field));
        if (SqlEnum.class.isAssignableFrom(fieldType)) {
            //优先根据泛型类型的默认值来匹配，匹配不到则获取第一个枚举
            SqlEnum sqlEnum = SqlBeanUtil.matchEnum(field, defaultValue);
            if (sqlEnum == null) {
                SqlEnum[] sqlEnums = (SqlEnum[]) fieldType.getEnumConstants();
                sqlEnum = sqlEnums[0];
            }
            ReflectUtil.instance().set(clazz, bean, field.getName(), sqlEnum);
            return sqlEnum.getCode();
        }
        // 不支持的类型（如 java.time.Instant、Android 下的 LocalDateTime 等）assignInitialValue 返回 null，
        // 此时不覆盖已有字段值，避免 UPDATE_EVERYTIME 把有效数据误写为 NULL
        if (defaultValue == null) {
            return null;
        }
        ReflectUtil.instance().set(clazz, bean, field.getName(), defaultValue);
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
        StringBuilder groupByAndOrderBySql = new StringBuilder();
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
        StringBuilder conditionSql = new StringBuilder();
        if (ConditionType.WHERE == conditionType) {
            if (StringUtil.isBlank(conditionString)) {
                conditionSql.append(versionCondition(common, bean));
                conditionSql.append(logicallyDeleteCondition(common));
            }
            // 行级多租户隔离：实体声明 @SqlTenantId 且当前上下文有租户ID时，强制追加 tenant_id 过滤。
            // 与逻辑删除不同，租户过滤始终生效（无论是否显式传入 where），防止跨租户数据越权访问。
            String tenantCond = tenantCondition(common);
            if (tenantCond.length() > 0) {
                if (conditionSql.length() > 0) {
                    conditionSql.append(SqlConstant.AND);
                }
                conditionSql.append(tenantCond);
            }
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
        StringBuilder conditionSql = new StringBuilder();
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
        StringBuilder conditionSql = new StringBuilder();
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
        StringBuilder versionConditionSql = new StringBuilder();
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
            Field logicallyDeleteField = SqlBeanUtil.getLogicallyField(common.getBeanClass());
            if (logicallyDeleteField != null) {
                // 获取逻辑删除策略
                SqlLogically sqlLogically = logicallyDeleteField.getAnnotation(SqlLogically.class);
                if (sqlLogically != null && sqlLogically.strategy() == LogicallyStrategy.NOT_FILTER) {
                    // 如果策略为 NOT_FILTER，不拼接逻辑删除条件
                    return "";
                }
                
                StringBuilder logicallyDeleteSql = new StringBuilder();
                SqlTable sqlTable = SqlBeanUtil.getSqlTable(common.getBeanClass());
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
                return logicallyDeleteSql.toString();
            }
        }
        return "";
    }

    /**
     * 租户隔离处理（SELECT / UPDATE / DELETE / BACKUP / COPY）
     * <p>
     * 若实体声明了 @SqlTenantId 字段且当前租户上下文存在，则生成 {@code (tableAlias.tenant_id = <值>)} 条件片段。
     * 返回值不带 WHERE 关键字，由调用方在 conditionHandle 中按需用 AND 连接。
     * <p>
     * BACKUP / COPY 同样参与隔离：两者都会把源表数据整表/按条件搬走，若不注入租户条件会跨租户搬运数据。
     * 注意这两者的 FROM 子句只输出表名、<b>不输出别名</b>（见 {@link #buildBackup} / {@link #buildCopy}），
     * 因此这里使用不带限定符的列名（单表语句下不会产生歧义），避免生成 {@code alias.col} 这种必报错的引用。
     *
     * @param common
     * @return 租户过滤条件片段；不满足注入条件时返回空串
     */
    private static String tenantCondition(Common common) {
        Class<?> clazz = common.getBeanClass();
        boolean supported = common instanceof Select || common instanceof Update || common instanceof Delete
                || common instanceof Backup || common instanceof Copy;
        if (clazz == null || !supported) {
            return "";
        }
        if (common.getTable() == null) {
            return "";
        }
        if (!SqlBeanUtil.checkTenant(clazz)) {
            return "";
        }
        Object tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return "";
        }
        Field tenantField = SqlBeanUtil.getTenantField(clazz);
        if (tenantField == null) {
            return "";
        }
        SqlTable sqlTable = SqlBeanUtil.getSqlTable(clazz);
        String columnName = SqlBeanUtil.getTableFieldName(tenantField, sqlTable);
        // BACKUP / COPY 的 FROM 没有别名，只能使用非限定列名
        String qualifier = (common instanceof Backup || common instanceof Copy)
                ? null : common.getTable().getAlias();
        StringBuilder tenantSql = new StringBuilder();
        tenantSql.append(SqlConstant.BEGIN_BRACKET);
        tenantSql.append(SqlBeanUtil.getTableFieldFullName(common, qualifier, columnName));
        tenantSql.append(SqlConstant.EQUAL_TO);
        tenantSql.append(SqlBeanUtil.getSqlValue(common, tenantId));
        tenantSql.append(SqlConstant.END_BRACKET);
        return tenantSql.toString();
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
    private static StringBuilder valueOperator(Common common, ConditionInfo conditionInfo) {
        StringBuilder sql = new StringBuilder();
        // EXISTS / NOT EXISTS：一元前缀操作符，操作数为子查询（Select 或原生 SQL），无左列与右值。
        // 子查询复用 buildSelectSql 递归构建，并继承当前 sqlBeanMeta（与 UNION 子查询同源处理）。
        SqlOperator op = conditionInfo.getSqlOperator();
        if (op == SqlOperator.EXISTS || op == SqlOperator.NOT_EXISTS) {
            Object operand = conditionInfo.getValue();
            String subSql;
            if (operand instanceof Select) {
                Select sub = (Select) operand;
                if (sub.getSqlBeanMeta() == null) {
                    sub.setSqlBeanMeta(common.getSqlBeanMeta());
                }
                subSql = SqlHelper.buildSelectSql(sub);
            } else if (operand instanceof RawValue) {
                Object raw = ((RawValue) operand).getValue();
                subSql = raw != null ? raw.toString() : "";
            } else {
                subSql = operand != null ? operand.toString() : "";
            }
            sql.append(op == SqlOperator.NOT_EXISTS ? SqlConstant.NOT_EXISTS : SqlConstant.EXISTS);
            sql.append(SqlConstant.BEGIN_BRACKET);
            sql.append(subSql);
            sql.append(SqlConstant.END_BRACKET);
            return sql;
        }
        String operator = getOperator(conditionInfo);
        // IN / 比较 子查询：操作数为 Select 子查询（类型安全）。
        // 与 EXISTS 同源处理：递归 buildSelectSql 并继承 common.getSqlBeanMeta()，
        // 自动获得租户/动态 schema 等隔离装饰（与 UNION 子查询同源）。
        // 仅 IN / NOT IN / = / <> / > / >= / < / <= 这些可带子查询的操作符走此分支；
        // 其余操作符（LIKE / BETWEEN / IS NULL 等）不适用子查询，继续既有逻辑。
        if (conditionInfo.getValue() instanceof Select) {
            SqlOperator subOp = conditionInfo.getSqlOperator();
            boolean subqueryable = subOp == SqlOperator.IN || subOp == SqlOperator.NOT_IN
                    || subOp == SqlOperator.EQUAL_TO || subOp == SqlOperator.NOT_EQUAL_TO
                    || subOp == SqlOperator.GREATER_THAN || subOp == SqlOperator.GREAT_THAN_OR_EQUAL_TO
                    || subOp == SqlOperator.LESS_THAN || subOp == SqlOperator.LESS_THAN_OR_EQUAL_TO;
            if (subqueryable) {
                Select sub = (Select) conditionInfo.getValue();
                if (sub.getSqlBeanMeta() == null) {
                    sub.setSqlBeanMeta(common.getSqlBeanMeta());
                }
                String subSql = SqlHelper.buildSelectSql(sub);
                StringBuilder sb = new StringBuilder();
                sb.append(SqlBeanUtil.getActualValue(common, conditionInfo.getColumn()));
                sb.append(operator);
                // IN / NOT IN 常量已含 " ("，无需重复左括号；比较操作符需显式包裹括号
                if (subOp == SqlOperator.IN || subOp == SqlOperator.NOT_IN) {
                    sb.append(subSql);
                } else {
                    sb.append(SqlConstant.BEGIN_BRACKET);
                    sb.append(subSql);
                    sb.append(SqlConstant.END_BRACKET);
                }
                return sb;
            }
        }
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
                    logger.warning("Failed to process BETWEEN value: " + e.getMessage());
                    return null;
                }
            }
        } else if (conditionInfo.getSqlOperator() == SqlOperator.IN || conditionInfo.getSqlOperator() == SqlOperator.NOT_IN) {
            needEndBracket = true;
            Object[] in_notInValues = SqlBeanUtil.getObjectArray(value);
            StringBuilder in_notIn = new StringBuilder();
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
                // 转义用户输入中的通配符，防止通配符注入
                String originalValue = value != null ? value.toString() : "";
                String escapedValue = SqlBeanUtil.filterLikeWildcard(originalValue);
                
                if (conditionInfo.getSqlOperator() == SqlOperator.LIKE || conditionInfo.getSqlOperator() == SqlOperator.LIKE_L || conditionInfo.getSqlOperator() == SqlOperator.NOT_LIKE || conditionInfo.getSqlOperator() == SqlOperator.NOT_LIKE_L) {
                    escapedValue = SqlConstant.PERCENT + escapedValue;
                }
                if (conditionInfo.getSqlOperator() == SqlOperator.LIKE || conditionInfo.getSqlOperator() == SqlOperator.LIKE_R || conditionInfo.getSqlOperator() == SqlOperator.NOT_LIKE || conditionInfo.getSqlOperator() == SqlOperator.NOT_LIKE_R) {
                    escapedValue = escapedValue + SqlConstant.PERCENT;
                }
                value = SqlConstant.SINGLE_QUOTATION_MARK + escapedValue + SqlConstant.SINGLE_QUOTATION_MARK;
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
