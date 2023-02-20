package cn.vonce.sql.bean;

import cn.vonce.sql.define.ColumnFunction;
import cn.vonce.sql.define.SqlFun;
import cn.vonce.sql.enumerate.JoinType;
import cn.vonce.sql.enumerate.SqlSort;
import cn.vonce.sql.helper.SqlHelper;
import cn.vonce.sql.helper.Wrapper;
import cn.vonce.sql.uitls.LambdaUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 查询
 *
 * @author Jovi
 * @version 1.0
 * @email 766255988@qq.com
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
    private String[] filterFields = null;
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
     * @param columnNames
     */
    public Select column(String[] columnNames) {
        if (columnNames != null && columnNames.length > 0) {
            for (String columnName : columnNames) {
                this.columnList.add(new Column(columnName));
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
     * @param columnFunction
     * @return
     */
    public <T, R> Select column(ColumnFunction<T, R> columnFunction) {
        this.columnList.add(LambdaUtil.getColumn(columnFunction));
        return this;
    }

    /**
     * 添加column列字段
     *
     * @param columnName
     * @return
     */
    public Select column(String columnName) {
        return column("", columnName, "");
    }

    /**
     * 添加column列字段
     *
     * @param columnName  列列字段名
     * @param columnAlias 别名
     * @return
     */
    public Select column(String columnName, String columnAlias) {
        return column("", columnName, columnAlias);
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
     * @param columnFunction
     * @param columnAlias
     * @return
     */
    public <T, R> Select column(ColumnFunction<T, R> columnFunction, String columnAlias) {
        Column column = LambdaUtil.getColumn(columnFunction);
        column.setAlias(columnAlias);
        columnList.add(column);
        return this;
    }

    /**
     * 添加column列字段
     *
     * @param tableAlias  表别名
     * @param columnName  列列字段名
     * @param columnAlias 别名
     * @return
     */
    public Select column(String tableAlias, String columnName, String columnAlias) {
        columnList.add(new Column(tableAlias, columnName, columnAlias));
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
     * @param columNname 列字段名
     * @return
     */
    public Select groupBy(String columNname) {
        return groupBy("", columNname);
    }

    /**
     * 添加groupBy分组
     *
     * @param column 列字段信息
     * @return
     */
    public Select groupBy(Column column) {
        return groupBy(column.getTableAlias(), column.getName());
    }

    /**
     * 添加groupBy分组
     *
     * @param columnFunction 列字段信息
     * @return
     */
    public <T, R> Select groupBy(ColumnFunction<T, R> columnFunction) {
        Column column = LambdaUtil.getColumn(columnFunction);
        return groupBy(column.getTableAlias(), column.getName());
    }

    /**
     * 添加groupBy分组
     *
     * @param tableAlias 表别名
     * @param columNname 列字段名
     * @return
     */
    public Select groupBy(String tableAlias, String columNname) {
        groupByList.add(new Group(tableAlias, columNname));
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
     * @param columNname 列字段名
     */
    public Select orderByAsc(String columNname) {
        return orderBy("", columNname, SqlSort.ASC);
    }

    /**
     * 添加列字段排序
     *
     * @param columNname 列字段名
     */
    public Select orderByDesc(String columNname) {
        return orderBy("", columNname, SqlSort.DESC);
    }

    /**
     * 添加列字段排序
     *
     * @param column 列字段名
     */
    public Select orderByAsc(Column column) {
        return orderBy(column.getTableAlias(), column.getName(), SqlSort.ASC);
    }

    /**
     * 添加列字段排序
     *
     * @param columnFunction 列字段名
     */
    public <T, R> Select orderByAsc(ColumnFunction<T, R> columnFunction) {
        Column column = LambdaUtil.getColumn(columnFunction);
        return orderBy(column.getTableAlias(), column.getName(), SqlSort.ASC);
    }

    /**
     * 添加列字段排序
     *
     * @param column 列字段名
     */
    public Select orderByDesc(Column column) {
        return orderBy(column.getTableAlias(), column.getName(), SqlSort.DESC);
    }

    /**
     * 添加列字段排序
     *
     * @param columnFunction 列字段名
     */
    public <T, R> Select orderByDesc(ColumnFunction<T, R> columnFunction) {
        Column column = LambdaUtil.getColumn(columnFunction);
        return orderBy(column.getTableAlias(), column.getName(), SqlSort.DESC);
    }

    /**
     * 添加列字段排序
     *
     * @param columName 列字段名
     * @param sqlSort   排序方式
     * @return
     */
    public Select orderBy(String columName, SqlSort sqlSort) {
        orderByList.add(new Order("", columName, sqlSort));
        return this;
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
     * 获取过滤的列字段
     *
     * @return
     */
    public String[] getFilterFields() {
        return filterFields;
    }

    /**
     * 设置过滤的列字段
     *
     * @param filterField
     */
    public Select filterFields(String... filterField) {
        this.filterFields = filterField;
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
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public Select copy() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(this);
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        return (Select) ois.readObject();
    }

}
