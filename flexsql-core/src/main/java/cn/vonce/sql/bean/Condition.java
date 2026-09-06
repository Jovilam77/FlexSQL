package cn.vonce.sql.bean;

import cn.vonce.sql.define.ColumnFun;
import cn.vonce.sql.enumerate.SqlLogic;
import cn.vonce.sql.enumerate.SqlOperator;
import cn.vonce.sql.uitls.LambdaUtil;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 简单的条件
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2022年3月15日上午10:00:10
 */
public class Condition<Action> implements Serializable {

    private Action action;
    private SqlLogic sqlLogic;
    private Logic<Action> logic = new Logic(this);
    private List<ConditionData> dataList = new ArrayList<>();

    public Condition() {
    }

    protected Condition(Action action) {
        this.action = action;
    }

    protected Action getAction() {
        return action;
    }

    protected SqlLogic getSqlLogic() {
        return sqlLogic;
    }

    protected void setSqlLogic(SqlLogic sqlLogic) {
        this.sqlLogic = sqlLogic;
    }

    /**
     * 获得条件模型列表
     *
     * @return
     */
    public List<ConditionData> getDataList() {
        return this.dataList;
    }

    /**
     * 设置条件模型列表
     *
     * @param dataList
     */
    public void setDataList(List<ConditionData> dataList) {
        this.dataList = dataList;
    }

    /**
     * 等于
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> eq(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.EQUAL_TO, value)));
        return logic;
    }

    /**
     * 等于
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> eq(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.EQUAL_TO, value)));
        return logic;
    }

    /**
     * 等于
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> eq(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.EQUAL_TO, value)));
        return logic;
    }

    /**
     * 等于
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> eq(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.EQUAL_TO, value)));
        return logic;
    }

    /**
     * 等于
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, K, R> Logic<Action> eq(ColumnFun<T, R> columnFun, ColumnFun<K, R> value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.EQUAL_TO, LambdaUtil.getColumn(value))));
        return logic;
    }

    /**
     * 不等于
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> notEq(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.NOT_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 不等于
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> notEq(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.NOT_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 不等于
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> notEq(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.NOT_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 不等于
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> notEq(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.NOT_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 不等于
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, K, R> Logic<Action> notEq(ColumnFun<T, R> columnFun, ColumnFun<K, R> value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.NOT_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 小于
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> lt(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.LESS_THAN, value)));
        return logic;
    }

    /**
     * 小于
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> lt(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.LESS_THAN, value)));
        return logic;
    }

    /**
     * 小于
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> lt(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.LESS_THAN, value)));
        return logic;
    }

    /**
     * 小于
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> lt(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.LESS_THAN, value)));
        return logic;
    }

    /**
     * 小于
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, K, R> Logic<Action> lt(ColumnFun<T, R> columnFun, ColumnFun<K, R> value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.LESS_THAN, value)));
        return logic;
    }

    /**
     * 大于
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> gt(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.GREATER_THAN, value)));
        return logic;
    }

    /**
     * 大于
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> gt(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.GREATER_THAN, value)));
        return logic;
    }

    /**
     * 大于
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> gt(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.GREATER_THAN, value)));
        return logic;
    }

    /**
     * 大于
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> gt(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.GREATER_THAN, value)));
        return logic;
    }

    /**
     * 大于
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, K, R> Logic<Action> gt(ColumnFun<T, R> columnFun, ColumnFun<K, R> value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.GREATER_THAN, value)));
        return logic;
    }

    /**
     * 小于等于
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> ltEq(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.LESS_THAN_OR_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 小于等于
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> ltEq(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.LESS_THAN_OR_EQUAL_TO, value)));
        return logic;
    }


    /**
     * 小于等于
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> ltEq(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.LESS_THAN_OR_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 小于等于
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> ltEq(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.LESS_THAN_OR_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 小于等于
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, K, R> Logic<Action> ltEq(ColumnFun<T, R> columnFun, ColumnFun<K, R> value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.LESS_THAN_OR_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 大于等于
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> gtEq(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.GREAT_THAN_OR_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 大于等于
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> gtEq(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.GREAT_THAN_OR_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 大于等于
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> gtEq(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.GREAT_THAN_OR_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 大于等于
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> gtEq(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.GREAT_THAN_OR_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 大于等于
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, K, R> Logic<Action> gtEq(ColumnFun<T, R> columnFun, ColumnFun<K, R> value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.GREAT_THAN_OR_EQUAL_TO, value)));
        return logic;
    }

    /**
     * 全模糊 包含
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> like(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.LIKE, value)));
        return logic;
    }

    /**
     * 全模糊 包含
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> like(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.LIKE, value)));
        return logic;
    }


    /**
     * 全模糊 包含
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> like(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.LIKE, value)));
        return logic;
    }

    /**
     * 全模糊 包含
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> like(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.LIKE, value)));
        return logic;
    }

    /**
     * 左模糊 包含
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> likeL(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.LIKE_L, value)));
        return logic;
    }

    /**
     * 左模糊 包含
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> likeL(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.LIKE_L, value)));
        return logic;
    }


    /**
     * 左模糊 包含
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> likeL(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.LIKE_L, value)));
        return logic;
    }

    /**
     * 左模糊 包含
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> likeL(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.LIKE_L, value)));
        return logic;
    }

    /**
     * 右模糊 包含
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> likeR(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.LIKE_R, value)));
        return logic;
    }

    /**
     * 右模糊 包含
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> likeR(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.LIKE_R, value)));
        return logic;
    }


    /**
     * 右模糊 包含
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> likeR(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.LIKE_R, value)));
        return logic;
    }

    /**
     * 右模糊 包含
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> likeR(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.LIKE_R, value)));
        return logic;
    }

    /**
     * 全模糊 不包含
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> notLike(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.NOT_LIKE, value)));
        return logic;
    }

    /**
     * 全模糊 不包含
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> notLike(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.NOT_LIKE, value)));
        return logic;
    }

    /**
     * 全模糊 不包含
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> notLike(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.NOT_LIKE, value)));
        return logic;
    }

    /**
     * 全模糊 不包含
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> notLike(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.NOT_LIKE, value)));
        return logic;
    }

    /**
     * 左模糊 不包含
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> notLikeL(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.NOT_LIKE_L, value)));
        return logic;
    }

    /**
     * 左模糊 不包含
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> notLikeL(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.NOT_LIKE_L, value)));
        return logic;
    }

    /**
     * 左模糊 不包含
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> notLikeL(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.NOT_LIKE_L, value)));
        return logic;
    }

    /**
     * 左模糊 不包含
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> notLikeL(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.NOT_LIKE_L, value)));
        return logic;
    }

    /**
     * 右模糊 不包含
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> notLikeR(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.NOT_LIKE_R, value)));
        return logic;
    }

    /**
     * 右模糊 不包含
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> notLikeR(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.NOT_LIKE_R, value)));
        return logic;
    }

    /**
     * 右模糊 不包含
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> notLikeR(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.NOT_LIKE_R, value)));
        return logic;
    }

    /**
     * 右模糊 不包含
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> notLikeR(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.NOT_LIKE_R, value)));
        return logic;
    }

    /**
     * 是
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> is(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.IS, value)));
        return logic;
    }

    /**
     * 是
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> is(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.IS, value)));
        return logic;
    }

    /**
     * 是
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> is(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.IS, value)));
        return logic;
    }

    /**
     * 是
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> is(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.IS, value)));
        return logic;
    }

    /**
     * 是
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, K, R> Logic<Action> is(ColumnFun<T, R> columnFun, ColumnFun<K, R> value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.IS, value)));
        return logic;
    }

    /**
     * 不是
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> isNot(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.IS_NOT, value)));
        return logic;
    }

    /**
     * 不是
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> isNot(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.IS_NOT, value)));
        return logic;
    }


    /**
     * 不是
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> isNot(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.IS_NOT, value)));
        return logic;
    }

    /**
     * 不是
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> isNot(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.IS_NOT, value)));
        return logic;
    }

    /**
     * 不是
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, K, R> Logic<Action> isNot(ColumnFun<T, R> columnFun, ColumnFun<K, R> value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.IS_NOT, value)));
        return logic;
    }

    /**
     * 是null
     *
     * @param field
     * @return
     */
    public Logic<Action> isNull(String field) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.IS_NULL, null)));
        return logic;
    }

    /**
     * 是null
     *
     * @param tableAlias
     * @param field
     * @return
     */
    public Logic<Action> isNull(String tableAlias, String field) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.IS_NULL, null)));
        return logic;
    }

    /**
     * 是null
     *
     * @param column
     * @return
     */
    public Logic<Action> isNull(Column column) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.IS_NULL, null)));
        return logic;
    }

    /**
     * 是null
     *
     * @param columnFun
     * @return
     */
    public <T, R> Logic<Action> isNull(ColumnFun<T, R> columnFun) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.IS_NULL, null)));
        return logic;
    }

    /**
     * 不是null
     *
     * @param field
     * @return
     */
    public Logic<Action> isNotNull(String field) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.IS_NOT_NULL, null)));
        return logic;
    }

    /**
     * 不是null
     *
     * @param tableAlias
     * @param field
     * @return
     */
    public Logic<Action> isNotNull(String tableAlias, String field) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.IS_NOT_NULL, null)));
        return logic;
    }

    /**
     * 不是null
     *
     * @param column
     * @return
     */
    public Logic<Action> isNotNull(Column column) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.IS_NOT_NULL, null)));
        return logic;
    }

    /**
     * 不是null
     *
     * @param columnFun
     * @return
     */
    public <T, R> Logic<Action> isNotNull(ColumnFun<T, R> columnFun) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.IS_NOT_NULL, null)));
        return logic;
    }

    /**
     * 包含
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> in(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.IN, value)));
        return logic;
    }

    /**
     * 包含
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> in(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.IN, value)));
        return logic;
    }

    /**
     * 包含
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> in(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.IN, value)));
        return logic;
    }

    /**
     * 包含
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> in(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.IN, value)));
        return logic;
    }

    /**
     * 不包含
     *
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> notIn(String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.NOT_IN, value)));
        return logic;
    }

    /**
     * 不包含
     *
     * @param tableAlias
     * @param field
     * @param value
     * @return
     */
    public Logic<Action> notIn(String tableAlias, String field, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.NOT_IN, value)));
        return logic;
    }

    /**
     * 不包含
     *
     * @param column
     * @param value
     * @return
     */
    public Logic<Action> notIn(Column column, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.NOT_IN, value)));
        return logic;
    }

    /**
     * 不包含
     *
     * @param columnFun
     * @param value
     * @return
     */
    public <T, R> Logic<Action> notIn(ColumnFun<T, R> columnFun, Object value) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.NOT_IN, value)));
        return logic;
    }

    /**
     * 存在（EXISTS 子查询）
     *
     * @param subQuery 子查询（Select 形式）
     * @return
     */
    public Logic<Action> exists(Select subQuery) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo((Column) null, SqlOperator.EXISTS, subQuery)));
        return logic;
    }

    /**
     * 存在（EXISTS 原生子查询）
     *
     * @param rawSql 原生 SQL 子查询（不含外层括号）
     * @return
     */
    public Logic<Action> exists(String rawSql) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo((Column) null, SqlOperator.EXISTS, new RawValue(rawSql))));
        return logic;
    }

    /**
     * 不存在（NOT EXISTS 子查询）
     *
     * @param subQuery 子查询（Select 形式）
     * @return
     */
    public Logic<Action> notExists(Select subQuery) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo((Column) null, SqlOperator.NOT_EXISTS, subQuery)));
        return logic;
    }

    /**
     * 不存在（NOT EXISTS 原生子查询）
     *
     * @param rawSql 原生 SQL 子查询（不含外层括号）
     * @return
     */
    public Logic<Action> notExists(String rawSql) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo((Column) null, SqlOperator.NOT_EXISTS, new RawValue(rawSql))));
        return logic;
    }

    /**
     * 介于
     *
     * @param field
     * @param value1
     * @param value2
     * @return
     */
    public Logic<Action> between(String field, Object value1, Object value2) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(null, field, SqlOperator.BETWEEN, new Object[]{value1, value2})));
        return logic;
    }

    /**
     * 介于
     *
     * @param tableAlias
     * @param field
     * @param value1
     * @param value2
     * @return
     */
    public Logic<Action> between(String tableAlias, String field, Object value1, Object value2) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(tableAlias, field, SqlOperator.BETWEEN, new Object[]{value1, value2})));
        return logic;
    }

    /**
     * 介于
     *
     * @param column
     * @param value1
     * @param value2
     * @return
     */
    public Logic<Action> between(Column column, Object value1, Object value2) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(column, SqlOperator.BETWEEN, new Object[]{value1, value2})));
        return logic;
    }

    /**
     * 介于
     *
     * @param columnFun
     * @param value1
     * @param value2
     * @return
     */
    public <T, R> Logic<Action> between(ColumnFun<T, R> columnFun, Object value1, Object value2) {
        dataList.add(new ConditionData(sqlLogic, newConditionInfo(LambdaUtil.getColumn(columnFun), SqlOperator.BETWEEN, new Object[]{value1, value2})));
        return logic;
    }

    private ConditionInfo newConditionInfo(String tableAlias, String name, SqlOperator sqlOperator, Object value) {
        return new ConditionInfo(null, tableAlias, name, value, sqlOperator);
    }

    private ConditionInfo newConditionInfo(Column column, SqlOperator sqlOperator, Object value) {
        return new ConditionInfo(null, column, value, sqlOperator);
    }

}
