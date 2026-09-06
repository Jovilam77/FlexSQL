package cn.vonce.sql.bean;

import cn.vonce.sql.define.ColumnFun;
import cn.vonce.sql.enumerate.JoinType;

import java.io.Serializable;

/**
 * Select
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2019年6月10日上午12:00:00
 */
public class Join implements Serializable {

    public Join() {
    }

    public Join(JoinType joinType, String schema, String tableName, String tableAlias) {
        this.joinType = joinType;
        this.schema = schema;
        this.tableName = tableName;
        this.tableAlias = tableAlias;
    }

    public Join(JoinType joinType, String schema, String tableName, String tableAlias, String tableKeyword, String mainKeyword, String on) {
        this.joinType = joinType;
        this.schema = schema;
        this.tableName = tableName;
        this.tableAlias = tableAlias;
        this.tableKeyword = tableKeyword;
        this.mainKeyword = mainKeyword;
        this.on = on;
    }

    private JoinType joinType;
    private String schema;
    private String tableName;
    private String tableAlias;
    /**
     * 关联实体类（用于行级多租户隔离：内省 @SqlTenantId 以在 ON 子句追加租户过滤）
     * 仅由 @SqlJoin 注解 bean 关联时写入；流式裸表 join 无实体可内省，为 null。
     */
    private Class<?> joinClass;
    @Deprecated
    private String tableKeyword;
    @Deprecated
    private String mainKeyword;
    @Deprecated
    private String on;

    /**
     * Join 条件
     */
    private Condition<Select> joinCondition;

    protected void setReturnObj(Select returnObj) {
        joinCondition = new Condition<>(returnObj);
    }


    public JoinType getJoinType() {
        return joinType;
    }

    public void setJoinType(JoinType joinType) {
        this.joinType = joinType;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableAlias() {
        return tableAlias;
    }

    public void setTableAlias(String tableAlias) {
        this.tableAlias = tableAlias;
    }

    public Class<?> getJoinClass() {
        return joinClass;
    }

    public void setJoinClass(Class<?> joinClass) {
        this.joinClass = joinClass;
    }

    @Deprecated
    public String getTableKeyword() {
        return tableKeyword;
    }

    @Deprecated
    public void setTableKeyword(String tableKeyword) {
        this.tableKeyword = tableKeyword;
    }

    @Deprecated
    public String getMainKeyword() {
        return mainKeyword;
    }

    @Deprecated
    public void setMainKeyword(String mainKeyword) {
        this.mainKeyword = mainKeyword;
    }

    @Deprecated
    public String getOn() {
        return on;
    }

    @Deprecated
    public void setOn(String on) {
        this.on = on;
    }

    public Logic<Select> on(String field, Object value) {
        return joinCondition.eq(field, value);
    }

    public Logic<Select> on(String tableAlias, String field, Object value) {
        return joinCondition.eq(tableAlias, field, value);
    }

    public Logic<Select> on(Column column, Object value) {
        return joinCondition.eq(column, value);
    }

    public <T, R> Logic<Select> on(ColumnFun<T, R> columnFun, Object value) {
        return joinCondition.eq(columnFun, value);
    }

    public <T, K, R> Logic<Select> on(ColumnFun<T, R> columnFun, ColumnFun<K, R> value) {
        return joinCondition.eq(columnFun, value);
    }

    public Condition<Select> on() {
        return joinCondition;
    }

    @Override
    public String toString() {
        return "Join{" +
                "joinType=" + joinType +
                ", schema='" + schema + '\'' +
                ", tableName='" + tableName + '\'' +
                ", tableAlias='" + tableAlias + '\'' +
                ", joinClass=" + joinClass +
                ", on='" + on + '\'' +
                '}';
    }
}
