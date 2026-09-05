package cn.vonce.sql.bean;

import cn.vonce.sql.define.ColumnFun;
import cn.vonce.sql.uitls.LambdaUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * UPSERT / MERGE（存在则更新，不存在则插入）
 * <p>
 * 继承 {@link Insert}，复用其字段/值构建能力，新增三个维度：
 * <ul>
 *     <li>{@code conflictColumns}：冲突判定列（主键/唯一键）。PG 的 {@code ON CONFLICT (cols)}、
 *         Oracle/SQL Server 的 {@code MERGE ... ON (...)} 需要它；MySQL 用之对应的唯一索引隐式判定（可省略）。</li>
 *     <li>{@code updateSetList} / {@code referenceColumns}：冲突时更新的列。
 *         {@code set(col, value)} 为字面量赋值；{@code set(col)} 用「待插入值」赋值（MySQL 走 VALUES(col)、
 *         PG/SQLite 走 EXCLUDED.col、Oracle/SQL Server 走 SRC.col）。</li>
 *     <li>{@code doNothing}：冲突时不更新（PG/SQLite 为 DO NOTHING；Oracle/SQL Server 仅保留插入分支）。</li>
 * </ul>
 * 另提供 {@link #setAll()} 便捷方法：冲突时用待插入值更新所有非冲突列。
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2026年9月5日
 */
public class Upsert<T> extends Insert<T> implements Serializable {

    /**
     * 冲突判定列（主键/唯一键）
     */
    private List<Column> conflictColumns = new ArrayList<>();

    /**
     * 冲突时的字面量赋值列表（col = value）
     */
    private List<SetInfo> updateSetList = new ArrayList<>();

    /**
     * 冲突时用「待插入值」更新的列（col = VALUES(col) / EXCLUDED.col / SRC.col）
     */
    private List<Column> referenceColumns = new ArrayList<>();

    /**
     * 冲突时是否用待插入值更新所有非冲突列
     */
    private boolean updateAll = false;

    /**
     * 冲突时是否不更新（DO NOTHING）
     */
    private boolean doNothing = false;

    public List<Column> getConflictColumns() {
        return conflictColumns;
    }

    public List<SetInfo> getUpdateSetList() {
        return updateSetList;
    }

    public List<Column> getReferenceColumns() {
        return referenceColumns;
    }

    public boolean isUpdateAll() {
        return updateAll;
    }

    public boolean isDoNothing() {
        return doNothing;
    }

    /**
     * 指定冲突判定列（主键/唯一键）
     *
     * @param columns 列信息
     */
    public Upsert<T> onConflict(Column... columns) {
        if (columns != null && columns.length > 0) {
            conflictColumns.addAll(Arrays.asList(columns));
        }
        return this;
    }

    /**
     * 指定冲突判定列（主键/唯一键）
     *
     * @param columnFuns 列信息
     */
    public <R> Upsert<T> onConflict(ColumnFun<T, R>... columnFuns) {
        if (columnFuns != null && columnFuns.length > 0) {
            for (ColumnFun<T, R> columnFun : columnFuns) {
                conflictColumns.add(LambdaUtil.getColumn(columnFun));
            }
        }
        return this;
    }

    /**
     * 冲突时以字面量更新指定列（col = value）
     *
     * @param columnName 列名
     * @param value      值
     */
    public Upsert<T> set(String columnName, Object value) {
        updateSetList.add(new SetInfo(columnName, value));
        return this;
    }

    /**
     * 冲突时以字面量更新指定列（col = value）
     *
     * @param column 列信息
     * @param value  值
     */
    public Upsert<T> set(Column column, Object value) {
        updateSetList.add(new SetInfo(column.getTableAlias(), column.getName(), value));
        return this;
    }

    /**
     * 冲突时以字面量更新指定列（col = value）
     *
     * @param columnFun 列信息
     * @param value     值
     */
    public <R> Upsert<T> set(ColumnFun<T, R> columnFun, Object value) {
        Column column = LambdaUtil.getColumn(columnFun);
        updateSetList.add(new SetInfo(column.getTableAlias(), column.getName(), value));
        return this;
    }

    /**
     * 冲突时用「待插入值」更新指定列（col = VALUES(col) / EXCLUDED.col / SRC.col）
     *
     * @param column 列信息
     */
    public Upsert<T> set(Column column) {
        referenceColumns.add(column);
        return this;
    }

    /**
     * 冲突时用「待插入值」更新指定列（col = VALUES(col) / EXCLUDED.col / SRC.col）
     *
     * @param columnFun 列信息
     */
    public <R> Upsert<T> set(ColumnFun<T, R> columnFun) {
        referenceColumns.add(LambdaUtil.getColumn(columnFun));
        return this;
    }

    /**
     * 冲突时用「待插入值」更新所有非冲突列
     */
    public Upsert<T> setAll() {
        this.updateAll = true;
        return this;
    }

    /**
     * 冲突时不更新（PG/SQLite 为 DO NOTHING；Oracle/SQL Server 仅保留插入分支）
     */
    public Upsert<T> doNothing() {
        this.doNothing = true;
        return this;
    }

}
