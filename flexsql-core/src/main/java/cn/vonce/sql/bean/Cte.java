package cn.vonce.sql.bean;

import java.util.ArrayList;
import java.util.List;

/**
 * 公共表表达式（CTE / WITH 子句）
 * <p>
 * 用法示例：
 * <pre>
 *   new Select().table(User.class)
 *       .with("recent", new Select().table(Order.class).column(Order::getId).where().gt(Order::getCreateTime, ...))
 *       .where().eq(User::getStatus, 1);
 *   // WITH recent AS (SELECT "id" FROM "d_order" WHERE ...) SELECT ... FROM "d_user" ...
 * </pre>
 *
 * @author Jovi
 * @date 2026/9/5
 */
public class Cte {

    /** CTE 名称 */
    private String name;

    /** 可选的输出列名列表，如 (a, b, c) */
    private List<String> columns;

    /** 子查询（Select 形式） */
    private Select subSelect;

    /** 子查询（原生 SQL 形式），与 subSelect 二选一 */
    private String rawSql;

    public Cte() {
    }

    public Cte(String name, Select subSelect) {
        this.name = name;
        this.subSelect = subSelect;
    }

    public Cte(String name, String rawSql) {
        this.name = name;
        this.rawSql = rawSql;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public Select getSubSelect() {
        return subSelect;
    }

    public void setSubSelect(Select subSelect) {
        this.subSelect = subSelect;
    }

    public String getRawSql() {
        return rawSql;
    }

    public void setRawSql(String rawSql) {
        this.rawSql = rawSql;
    }

    /**
     * 便捷方法：追加一列到输出列名列表
     *
     * @param column 列名
     */
    public Cte column(String column) {
        if (this.columns == null) {
            this.columns = new ArrayList<>();
        }
        this.columns.add(column);
        return this;
    }

}
