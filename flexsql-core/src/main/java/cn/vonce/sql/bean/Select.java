package cn.vonce.sql.bean;

import cn.vonce.sql.define.ColumnFun;
import cn.vonce.sql.define.SqlFun;
import cn.vonce.sql.enumerate.JoinType;
import cn.vonce.sql.enumerate.LockType;
import cn.vonce.sql.enumerate.LockWaitMode;
import cn.vonce.sql.enumerate.SqlSort;
import cn.vonce.sql.helper.SqlHelper;
import cn.vonce.sql.helper.Wrapper;
import cn.vonce.sql.uitls.LambdaUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 查询
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2017年8月18日上午9:00:19
 */
public class Select extends CommonCondition<Select> implements Serializable {

    private static final long serialVersionUID = 1L;

    public Select() {
        super();
        super.setReturnObj(this);
    }

    /**
     * 是否为查询
     */
    private boolean count;
    /**
     * 默认不去重复
     */
    private boolean distinct = false;
    /**
     * 查询的列字段列表
     */
    private List<Column> columnList = new ArrayList<>();
    /**
     * 表连接列表
     */
    private List<Join> joinList = new ArrayList<>();
    /**
     * 分组列表
     */
    private List<Group> groupByList = new ArrayList<>();
    /**
     * 排序列表
     */
    private List<Order> orderByList = new ArrayList<>();
    /**
     * 分页对象
     */
    private Page page = null;
    /**
     * 过滤的字段数组
     */
    private List<Column> filterColumns = new ArrayList<>();
    /**
     * 行锁（悲观锁）类型，默认不加锁
     */
    private LockType lockType = LockType.NONE;
    /**
     * 行锁等待模式，默认 WAIT（一直等待）
     * <p>
     * NOWAIT 立即返回、SKIP_LOCKED 跳过已锁行，分别由方言按版本门控支持。
     */
    private LockWaitMode lockWaitMode = LockWaitMode.WAIT;
    /**
     * 行锁作用的目标表（FOR UPDATE OF ... / FOR SHARE OF ...）
     * <p>
     * 为 null 时表示锁定所有涉及的表；非空时仅锁定指定表（用于多表 JOIN 场景）。
     */
    private List<String> lockOfTables = null;
    /**
     * CTE（WITH 子句）列表
     */
    private List<Cte> ctes = new ArrayList<>();
    /**
     * 是否递归 CTE（WITH RECURSIVE）
     * <p>
     * 注意：是否真正输出 RECURSIVE 关键字由各方言决定
     * （MySQL / PostgreSQL 需要，Oracle / SQL Server 不需要）。
     */
    private boolean recursive;
    /**
     * UNION / UNION ALL 子查询集合（与 unionAlls 一一对应）
     */
    private List<Select> unionSelects = new ArrayList<>();
    /**
     * 对应每个 union 是否为 UNION ALL（true=UNION ALL，false=UNION）
     */
    private List<Boolean> unionAlls = new ArrayList<>();
    /**
     * 无 FROM 子句（如 SELECT 1、SELECT NOW()）
     * <p>
     * 用于 UNION 中的常量锚点（如递归 CTE 的 SELECT 1 AS n）或纯表达式查询。
     */
    private boolean noFrom;
    /**
     * having 条件表达式 优先级一
     */
    private String having = null;
    /**
     * having 条件表达式 参数
     */
    private Object[] havingArgs = null;
    /**
     * having 条件包装器 优先级二
     */
    private Wrapper havingWrapper = new Wrapper();
    /**
     * having 条件 优先级三
     */
    private Condition<Select> havingCondition = new Condition<>(this);

    /**
     * 获取useDistinct是否过滤重复
     *
     * @return
     */
    public boolean isDistinct() {
        return distinct;
    }

    /**
     * 设置useDistinct是否过滤重复
     *
     * @param distinct 是否过滤
     */
    public Select distinct(boolean distinct) {
        this.distinct = distinct;
        return this;
    }

    /**
     * 是否为查询
     *
     * @return
     */
    public boolean isCount() {
        return count;
    }

    /**
     * 是否为查询
     *
     * @param count
     */
    public void count(boolean count) {
        this.count = count;
    }

    /**
     * 获取column sql 内容
     *
     * @return
     */
    public List<Column> getColumnList() {
        return columnList;
    }

    /**
     * 设置column list
     *
     * @param columnList
     */
    public void setColumnList(List<Column> columnList) {
        this.columnList.addAll(columnList);
    }

    /**
     * 设置column
     *
     * @param columNames
     */
    public Select column(String[] columNames) {
        if (columNames != null && columNames.length > 0) {
            for (String columName : columNames) {
                this.columnList.add(new Column(columName));
            }
        }
        return this;
    }

    /**
     * 设置column
     *
     * @param columns
     */
    public Select column(Column... columns) {
        if (columns != null && columns.length > 0) {
            this.columnList.addAll(Arrays.asList(columns));
        }
        return this;
    }

    /**
     * 添加column列字段
     *
     * @param column
     * @return
     */
    public Select column(Column column) {
        this.columnList.add(column);
        return this;
    }

    /**
     * 添加column列字段
     *
     * @param columnFun
     * @return
     */
    public <T, R> Select column(ColumnFun<T, R> columnFun) {
        this.columnList.add(LambdaUtil.getColumn(columnFun));
        return this;
    }

    /**
     * 添加column列字段
     *
     * @param columnFuns
     * @return
     */
    public <T, R> Select column(ColumnFun<T, R>... columnFuns) {
        if (columnFuns != null && columnFuns.length > 0) {
            for (ColumnFun item : columnFuns) {
                this.columnList.add(LambdaUtil.getColumn(item));
            }
        }
        return this;
    }

    /**
     * 添加column列字段
     *
     * @param columName
     * @return
     */
    public Select column(String columName) {
        return column("", columName, "");
    }

    /**
     * 添加column列字段
     *
     * @param columName   列列字段名
     * @param columnAlias 别名
     * @return
     */
    public Select column(String columName, String columnAlias) {
        return column("", columName, columnAlias);
    }

    /**
     * 添加column列字段
     *
     * @param select      子Sql
     * @param columnAlias 别名
     * @return
     */
    public Select column(Select select, String columnAlias) {
        return column(SqlHelper.buildSelectSql(select), columnAlias);
    }

    /**
     * 添加column列字段
     *
     * @param column
     * @param columnAlias
     * @return
     */
    public Select column(Column column, String columnAlias) {
        Column newColumn;
        if (column instanceof SqlFun) {
            newColumn = SqlBeanUtil.copy(column);
            newColumn.setAlias(columnAlias);
        } else {
            newColumn = new Column(column.getTableAlias(), column.getName(), columnAlias);
        }
        columnList.add(newColumn);
        return this;
    }

    /**
     * 添加column列字段
     *
     * @param columnFun
     * @param columnAlias
     * @return
     */
    public <T, R> Select column(ColumnFun<T, R> columnFun, String columnAlias) {
        Column column = LambdaUtil.getColumn(columnFun);
        column.setAlias(columnAlias);
        columnList.add(column);
        return this;
    }

    /**
     * 添加column列字段
     *
     * @param tableAlias  表别名
     * @param columName   列列字段名
     * @param columnAlias 别名
     * @return
     */
    public Select column(String tableAlias, String columName, String columnAlias) {
        columnList.add(new Column(tableAlias, columName, columnAlias));
        return this;
    }

    /**
     * 增加连表
     *
     * @param join
     * @return
     */
    public Join addJoin(Join join) {
        join.setReturnObj(this);
        joinList.add(join);
        return join;
    }

    /**
     * 获取表连接
     */
    public List<Join> getJoin() {
        return joinList;
    }

    /**
     * 添加表连接
     *
     * @param table        关联的表名
     * @param tableKeyword 关联的表关键列字段
     * @param mainKeyword  主表关键列字段
     */
    @Deprecated
    public Select join(String table, String tableKeyword, String mainKeyword) {
        return join(JoinType.INNER_JOIN, "", table, table, tableKeyword, mainKeyword);
    }

    /**
     * 添加表连接
     *
     * @param joinType     连接类型
     * @param table        关联的表名
     * @param tableKeyword 关联的表关键列字段
     * @param mainKeyword  主表关键列字段
     */
    @Deprecated
    public Select join(JoinType joinType, String table, String tableKeyword, String mainKeyword) {
        return join(joinType, "", table, table, tableKeyword, mainKeyword);
    }

    /**
     * 添加表连接
     *
     * @param table        关联的表名
     * @param tableKeyword 关联的表关键列字段
     * @param mainKeyword  主表关键列字段
     */
    @Deprecated
    public Select join(String schema, String table, String tableAlias, String tableKeyword, String mainKeyword) {
        return join(JoinType.INNER_JOIN, schema, table, tableAlias, tableKeyword, mainKeyword);
    }

    /**
     * 添加表连接
     *
     * @param joinType     连接类型
     * @param schema       schema
     * @param table        关联的表名
     * @param tableKeyword 关联的表关键列字段
     * @param mainKeyword  主表关键列字段
     */
    @Deprecated
    public Select join(JoinType joinType, String schema, String table, String tableAlias, String tableKeyword, String mainKeyword) {
        joinList.add(new Join(joinType, schema, table, tableAlias, tableKeyword, mainKeyword, ""));
        return this;
    }

    /**
     * 添加表连接
     *
     * @param table 关联的表名
     * @param on    连接条件
     */
    @Deprecated
    public Select join(String table, String on) {
        return join(JoinType.INNER_JOIN, "", table, table, on);
    }

    /**
     * 添加表连接
     *
     * @param joinType 连接类型
     * @param table    关联的表名
     * @param on       连接条件
     */
    @Deprecated
    public Select join(JoinType joinType, String table, String on) {
        return join(joinType, "", table, table, on);
    }

    /**
     * 添加表连接
     *
     * @param joinType 连接类型
     * @param schema   schema
     * @param table    关联的表名
     * @param on       连接条件
     */
    @Deprecated
    public Select join(JoinType joinType, String schema, String table, String tableAlias, String on) {
        joinList.add(new Join(joinType, schema, table, tableAlias, "", "", on));
        return this;
    }

    public Join innerJoin(Class<?> clazz) {
        Table table = SqlBeanUtil.getTable(clazz);
        return innerJoin(table.getSchema(), table.getName(), table.getAlias());
    }

    public Join innerJoin(String table, String tableAlias) {
        return innerJoin(null, table, tableAlias);
    }

    public Join innerJoin(String schema, String table, String tableAlias) {
        Join join = new Join(JoinType.INNER_JOIN, schema, table, tableAlias);
        join.setReturnObj(this);
        joinList.add(join);
        return join;
    }

    public Join leftJoin(Class<?> clazz) {
        Table table = SqlBeanUtil.getTable(clazz);
        return leftJoin(table.getSchema(), table.getName(), table.getAlias());
    }

    public Join leftJoin(String table, String tableAlias) {
        return leftJoin(null, table, tableAlias);
    }

    public Join leftJoin(String schema, String table, String tableAlias) {
        Join join = new Join(JoinType.LEFT_JOIN, schema, table, tableAlias);
        join.setReturnObj(this);
        joinList.add(join);
        return join;
    }

    public Join rightJoin(Class<?> clazz) {
        Table table = SqlBeanUtil.getTable(clazz);
        return rightJoin(table.getSchema(), table.getName(), table.getAlias());
    }

    public Join rightJoin(String table, String tableAlias) {
        return rightJoin(null, table, tableAlias);
    }

    public Join rightJoin(String schema, String table, String tableAlias) {
        Join join = new Join(JoinType.RIGHT_JOIN, schema, table, tableAlias);
        join.setReturnObj(this);
        joinList.add(join);
        return join;
    }

    public Join fullJoin(Class<?> clazz) {
        Table table = SqlBeanUtil.getTable(clazz);
        return fullJoin(table.getSchema(), table.getName(), table.getAlias());
    }

    public Join fullJoin(String table, String tableAlias) {
        return fullJoin(null, table, tableAlias);
    }

    public Join fullJoin(String schema, String table, String tableAlias) {
        Join join = new Join(JoinType.FULL_JOIN, schema, table, tableAlias);
        join.setReturnObj(this);
        joinList.add(join);
        return join;
    }

    /**
     * 获取groupBy分组列字段
     *
     * @return
     */
    public List<Group> getGroupBy() {
        return groupByList;
    }

    /**
     * 添加groupBy分组
     *
     * @param columName 列字段名
     * @return
     */
    public Select groupBy(String columName) {
        return groupBy("", columName);
    }

    /**
     * 添加groupBy分组
     *
     * @param column 列字段信息
     * @return
     */
    public Select groupBy(Column column) {
        groupByList.add(new Group(column));
        return this;
    }

    /**
     * 添加groupBy分组
     *
     * @param columnFun 列字段信息
     * @return
     */
    public <T, R> Select groupBy(ColumnFun<T, R> columnFun) {
        groupByList.add(new Group(LambdaUtil.getColumn(columnFun)));
        return this;
    }

    /**
     * 添加groupBy分组
     *
     * @param tableAlias 表别名
     * @param columName  列字段名
     * @return
     */
    public Select groupBy(String tableAlias, String columName) {
        groupByList.add(new Group(tableAlias, columName));
        return this;
    }


    /**
     * 获取orderBy排序列字段
     *
     * @return
     */
    public List<Order> getOrderBy() {
        return orderByList;
    }

    /**
     * 添加列字段排序
     *
     * @param columName 列字段名
     */
    public Select orderByAsc(String columName) {
        return orderBy("", columName, SqlSort.ASC);
    }

    /**
     * 添加列字段排序
     *
     * @param columName 列字段名
     */
    public Select orderByDesc(String columName) {
        return orderBy("", columName, SqlSort.DESC);
    }

    /**
     * 添加列字段排序
     *
     * @param column 列字段名
     */
    public Select orderByAsc(Column column) {
        orderByList.add(new Order(column, SqlSort.ASC));
        return this;
    }

    /**
     * 添加列字段排序
     *
     * @param columnFun 列字段名
     */
    public <T, R> Select orderByAsc(ColumnFun<T, R> columnFun) {
        return orderByAsc(LambdaUtil.getColumn(columnFun));
    }

    /**
     * 添加列字段排序
     *
     * @param column 列字段名
     */
    public Select orderByDesc(Column column) {
        orderByList.add(new Order(column, SqlSort.DESC));
        return this;
    }

    /**
     * 添加列字段排序
     *
     * @param columnFun 列字段名
     */
    public <T, R> Select orderByDesc(ColumnFun<T, R> columnFun) {
        return orderByDesc(LambdaUtil.getColumn(columnFun));
    }

    /**
     * 添加列字段排序
     *
     * @param columName 列字段名
     * @param sqlSort   排序方式
     * @return
     */
    public Select orderBy(String columName, SqlSort sqlSort) {
        return orderBy("", columName, sqlSort);
    }

    /**
     * 添加列字段排序
     *
     * @param tableAlias 表别名
     * @param columName  列字段名
     * @param sqlSort    排序方式
     * @return
     */
    public Select orderBy(String tableAlias, String columName, SqlSort sqlSort) {
        orderByList.add(new Order(tableAlias, columName, sqlSort));
        return this;
    }

    /**
     * 添加列字段排序
     *
     * @param column
     * @param sqlSort
     * @return
     */
    public Select orderBy(Column column, SqlSort sqlSort) {
        orderByList.add(new Order(column, sqlSort));
        return this;
    }

    /**
     * 添加列字段排序
     *
     * @param columnFun 列字段名
     * @param sqlSort   排序方式
     * @return
     */
    public <T, R> Select orderBy(ColumnFun<T, R> columnFun, SqlSort sqlSort) {
        return orderBy(LambdaUtil.getColumn(columnFun), sqlSort);
    }

    /**
     * 添加列字段排序
     *
     * @param orders 排序
     * @return
     */
    public Select orderBy(Order[] orders) {
        if (orders != null && orders.length > 0) {
            orderByList.addAll(Arrays.asList(orders));
        }
        return this;
    }

    /**
     * 设置分页参数
     *
     * @param pagenum  当前页(第一页从0开始)
     * @param pagesize 每页显示数量
     */
    public Select page(Integer pagenum, Integer pagesize) {
        this.page = new Page(pagenum, pagesize);
        return this;
    }

    /**
     * 设置分页参数
     *
     * @param pagenum     当前页(默认第一页从0开始)
     * @param pagesize    每页显示数量
     * @param startByZero 第一页是否从0开始
     */
    public Select page(Integer pagenum, Integer pagesize, boolean startByZero) {
        this.page = new Page(pagenum, pagesize, startByZero);
        return this;
    }

    /**
     * 设置分页参数(SqlServer专用)
     *
     * @param idName   主键的名称
     * @param pagenum  当前页(第一页从0开始)
     * @param pagesize 每页显示数量
     */
    public Select page(String idName, Integer pagenum, Integer pagesize) {
        this.page = new Page(idName, pagenum, pagesize);
        return this;
    }

    /**
     * 设置分页参数(SqlServer专用)
     *
     * @param idName      主键的名称
     * @param pagenum     当前页(第一页从0开始)
     * @param pagesize    每页显示数量
     * @param startByZero startByZero 第一页是否从0开始
     */
    public Select page(String idName, Integer pagenum, Integer pagesize, boolean startByZero) {
        this.page = new Page(idName, pagenum, pagesize, startByZero);
        return this;
    }

    /**
     * 获取分页参数
     *
     * @return
     */
    public Page getPage() {
        return this.page;
    }

    /**
     * 设置过滤的列字段
     *
     * @param filterFields
     */
    public Select filterFields(String... filterFields) {
        if (filterFields != null && filterFields.length > 0) {
            for (String filterField : filterFields) {
                this.filterColumns.add(new Column(filterField));
            }
        }
        return this;
    }

    /**
     * 获取过滤的列字段
     *
     * @return
     */
    public List<Column> getFilterColumns() {
        return filterColumns;
    }

    /**
     * 设置过滤的列字段
     *
     * @param filterColumns
     */
    public Select filterFields(Column... filterColumns) {
        if (filterColumns != null && filterColumns.length > 0) {
            for (Column column : filterColumns) {
                this.filterColumns.add(column);
            }
        }
        return this;
    }

    /**
     * 设置过滤的列字段
     *
     * @param columnFuns
     */
    public <T, R> Select filterFields(ColumnFun<T, R>... columnFuns) {
        if (columnFuns != null && columnFuns.length > 0) {
            for (ColumnFun<T, R> columnFun : columnFuns) {
                this.filterColumns.add(LambdaUtil.getColumn(columnFun));
            }
        }
        return this;
    }

    /**
     * 简单的having
     *
     * @return
     */
    public Condition<Select> having() {
        return havingCondition;
    }

    /**
     * 获取行锁类型
     *
     * @return 行锁类型，默认 {@link LockType#NONE}
     */
    public LockType getLockType() {
        return lockType;
    }

    /**
     * 设置行锁类型
     *
     * @param lockType 行锁类型
     * @return
     */
    public Select lockType(LockType lockType) {
        this.lockType = lockType;
        return this;
    }

    /**
     * 获取行锁等待模式
     *
     * @return 等待模式，默认 {@link LockWaitMode#WAIT}
     */
    public LockWaitMode getLockWaitMode() {
        return lockWaitMode;
    }

    /**
     * 设置行锁等待模式
     *
     * @param lockWaitMode 等待模式
     * @return
     */
    public Select lockWaitMode(LockWaitMode lockWaitMode) {
        this.lockWaitMode = lockWaitMode;
        return this;
    }

    /**
     * 获取行锁作用的目标表列表（FOR ... OF ...）
     *
     * @return 目标表名集合，为 null 时锁定所有涉及的表
     */
    public List<String> getLockOfTables() {
        return lockOfTables;
    }

    /**
     * 设置行锁作用的目标表列表
     *
     * @param lockOfTables 目标表名集合
     */
    public void setLockOfTables(List<String> lockOfTables) {
        this.lockOfTables = lockOfTables;
    }

    /**
     * 设置行锁为 FOR UPDATE（悲观行锁）
     * <p>
     * 对已读取的行加排他锁，直到当前事务结束。适用于 MySQL / MariaDB / PostgreSQL / Oracle 等主流数据库。
     *
     * @return
     */
    public Select forUpdate() {
        this.lockType = LockType.FOR_UPDATE;
        return this;
    }

    /**
     * 设置行锁为 FOR UPDATE SKIP LOCKED（跳过已被锁定的行，避免并发等待）
     * <p>
     * 等价于 {@code forUpdate().skipLocked()}。
     * 仅 MySQL 8.0+ / MariaDB 10.3+ / PostgreSQL 9.5+ / Oracle 11g+ 支持；
     * 低版本数据库会自动忽略该子句并输出告警。行锁子句会拼接在 ORDER BY 与 LIMIT 之后。
     *
     * @return
     */
    public Select forUpdateSkipLocked() {
        this.lockType = LockType.FOR_UPDATE;
        this.lockWaitMode = LockWaitMode.SKIP_LOCKED;
        return this;
    }

    /**
     * 设置行锁为 FOR SHARE（共享行锁 / 读锁）
     * <p>
     * 允许其它事务读取被锁定的行，但阻止其加排他锁或修改，适用于「读多写少」的并发控制。
     * 仅 MySQL 8.0+ / PostgreSQL 支持；低版本数据库会自动忽略该子句并输出告警。
     *
     * @return
     */
    public Select forShare() {
        this.lockType = LockType.FOR_SHARE;
        return this;
    }

    /**
     * 设置行锁为 FOR SHARE NOWAIT（共享锁且不等待）
     * <p>
     * 等价于 {@code forShare().nowait()}。
     *
     * @return
     */
    public Select forShareNowait() {
        this.lockType = LockType.FOR_SHARE;
        this.lockWaitMode = LockWaitMode.NOWAIT;
        return this;
    }

    /**
     * 设置行锁等待模式为 NOWAIT：获取不到锁时立即报错返回，不阻塞等待。
     * <p>
     * 可与 {@link #forUpdate()} / {@link #forShare()} 链式调用，例如 {@code forUpdate().nowait()}。
     * 仅 MySQL 8.0+ / PostgreSQL 9.5+ / Oracle（全版本）/ SQL Server 支持。
     *
     * @return
     */
    public Select nowait() {
        this.lockWaitMode = LockWaitMode.NOWAIT;
        return this;
    }

    /**
     * 设置行锁等待模式为 SKIP LOCKED：跳过已被其它事务锁定的行，避免并发等待。
     * <p>
     * 可与 {@link #forUpdate()} / {@link #forShare()} 链式调用，例如 {@code forUpdate().skipLocked()}。
     * 仅 MySQL 8.0+ / MariaDB 10.3+ / PostgreSQL 9.5+ / Oracle 11g+ 支持。
     *
     * @return
     */
    public Select skipLocked() {
        this.lockWaitMode = LockWaitMode.SKIP_LOCKED;
        return this;
    }

    /**
     * 限定行锁仅作用于指定表（FOR UPDATE OF ... / FOR SHARE OF ...）
     * <p>
     * 用于多表 JOIN 场景，仅锁定列出的表所对应的行；不调用本方法则锁定所有涉及的表。
     *
     * @param tables 目标表名（可多个）
     * @return
     */
    public Select of(String... tables) {
        if (tables != null && tables.length > 0) {
            this.lockOfTables = Arrays.asList(tables);
        }
        return this;
    }

    /**
     * 限定行锁仅作用于指定表（FOR UPDATE OF ... / FOR SHARE OF ...）
     *
     * @param tables 目标表名集合
     * @return
     */
    public Select of(List<String> tables) {
        this.lockOfTables = tables;
        return this;
    }

    /**
     * 添加 CTE（WITH 子句）
     *
     * @param ctes CTE 数组
     * @return
     */
    public Select with(Cte... ctes) {
        if (ctes != null) {
            for (Cte cte : ctes) {
                this.ctes.add(cte);
            }
        }
        return this;
    }

    /**
     * 添加基于 Select 子查询的 CTE
     *
     * @param name      CTE 名称
     * @param subSelect 子查询
     * @return
     */
    public Select with(String name, Select subSelect) {
        this.ctes.add(new Cte(name, subSelect));
        return this;
    }

    /**
     * 添加基于原生 SQL 子查询的 CTE
     *
     * @param name   CTE 名称
     * @param rawSql 子查询 SQL
     * @return
     */
    public Select with(String name, String rawSql) {
        this.ctes.add(new Cte(name, rawSql));
        return this;
    }

    /**
     * 标记为递归 CTE（WITH RECURSIVE）
     *
     * @return
     */
    public Select recursive() {
        this.recursive = true;
        return this;
    }

    public List<Cte> getCtes() {
        return ctes;
    }

    public void setCtes(List<Cte> ctes) {
        this.ctes = ctes;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    /**
     * 追加 UNION 子查询（去重合并）
     *
     * @param select 参与 UNION 的查询
     * @return
     */
    public Select union(Select select) {
        if (select != null) {
            this.unionSelects.add(select);
            this.unionAlls.add(false);
        }
        return this;
    }

    /**
     * 追加 UNION ALL 子查询（不去重合并，性能优于 UNION）
     *
     * @param select 参与 UNION ALL 的查询
     * @return
     */
    public Select unionAll(Select select) {
        if (select != null) {
            this.unionSelects.add(select);
            this.unionAlls.add(true);
        }
        return this;
    }

    public List<Select> getUnionSelects() {
        return unionSelects;
    }

    public void setUnionSelects(List<Select> unionSelects) {
        this.unionSelects = unionSelects;
    }

    public List<Boolean> getUnionAlls() {
        return unionAlls;
    }

    public void setUnionAlls(List<Boolean> unionAlls) {
        this.unionAlls = unionAlls;
    }

    /**
     * 标记本查询无 FROM 子句（如 SELECT 1、SELECT NOW()）
     * <p>
     * 常用于 UNION 的常量锚点（递归 CTE 的 SELECT 1 AS n）或纯表达式查询。
     *
     * @return
     */
    public Select noFrom() {
        this.noFrom = true;
        return this;
    }

    public boolean isNoFrom() {
        return noFrom;
    }

    public void setNoFrom(boolean noFrom) {
        this.noFrom = noFrom;
    }

    /**
     * 获得Having包装器
     *
     * @return
     */
    public Wrapper getHavingWrapper() {
        return havingWrapper;
    }

    /**
     * 设置Having条件包装器
     *
     * @param wrapper
     */
    public Select having(Wrapper wrapper) {
        this.havingWrapper = wrapper;
        return this;
    }

    /**
     * 获取where sql 内容
     *
     * @return
     */
    public String getHaving() {
        return having;
    }

    /**
     * 设置having sql 内容
     *
     * @param having
     * @param args
     */
    public Select having(String having, Object... args) {
        this.having = having;
        this.havingArgs = args;
        return this;
    }

    /**
     * 获取Having
     *
     * @return
     */
    public Object[] getHavingArgs() {
        return havingArgs;
    }

    /**
     * 设置table
     *
     * @param name
     */
    public Select table(String name) {
        super.setTable(name, name);
        return this;
    }

    /**
     * 设置table
     *
     * @param name
     * @param aliasName
     */
    public Select table(String name, String aliasName) {
        super.setTable(name, aliasName);
        return this;
    }

    /**
     * 设置table
     *
     * @param name
     * @param aliasName
     */
    public Select table(String schema, String name, String aliasName) {
        super.setTable(schema, name, aliasName);
        return this;
    }

    /**
     * 设置table sql 内容
     *
     * @param clazz 表对应的实体类
     */
    public Select table(Class<?> clazz) {
        super.setTable(clazz);
        return this;
    }

    /**
     * 复制对象
     *
     * @return
     */
    public Select copy() {
        return SqlBeanUtil.copy(this);
    }

}
