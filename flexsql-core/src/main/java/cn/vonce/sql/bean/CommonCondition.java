package cn.vonce.sql.bean;

import cn.vonce.sql.helper.Wrapper;

/**
 * where条件
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2020年3月1日上午10:00:10
 */
public class CommonCondition<T> extends Common {

    /**
     * 链式返回对象
     */
    private T returnObj;
    /**
     * where 条件表达式 优先级一
     */
    private String where = "";
    /**
     * where 条件表达式参数
     */
    private Object[] args = null;
    /**
     * where 条件包装器 优先级二
     */
    private Wrapper whereWrapper;
    /**
     * where 条件 优先级三
     */
    private Condition<T> whereCondition;

    protected void setReturnObj(T returnObj) {
        this.returnObj = returnObj;
        whereCondition = new Condition<>(returnObj);
    }

    /**
     * 获取where sql 内容
     *
     * @return
     */
    public String getWhere() {
        return where;
    }

    /**
     * 设置where sql 内容（原生 SQL 字符串，优先级最高）
     *
     * <p><b>刻意保留的「口子」（escape hatch）：</b>一旦显式传入原生字符串 where，框架将
     * <b>不再自动注入</b>乐观锁条件（{@code Update} + 乐观锁）与逻辑删除过滤
     * （{@code Select} 上的 {@code (deleted = 0)}）。这是因为用户此时已自行掌控过滤条件，
     * 例如需要主动查询<b>已被逻辑删除的数据</b>时，就可以通过
     * {@code where("1 = 1")} / {@code where("deleted = 1")} 等方式绕过自动过滤。
     * </p>
     *
     * <p>与之相对，使用类型安全的条件构建器 {@link #where()}（{@code Condition}）或
     * {@link #where(Wrapper)} 时，框架仍会自动追加上述过滤条件，保证常规查询不会误带出
     * 已删除 / 版本不匹配的数据。租户隔离过滤在任何情况下都始终生效。</p>
     *
     * <p><b>注意：</b>该绕过是全局性的——只要 where 为字符串，逻辑删除 / 乐观锁条件整个被跳过，
     * 不会与你的字符串条件做「AND 合并」。若只想在保留自动过滤的同时追加额外条件，
     * 请改用 {@link #where()} 构建器而非原生字符串。</p>
     *
     * @param where 原生 SQL 条件片段（不含外层 {@code WHERE} 关键字），可含 {@code ?} 占位符或 {@code ${}} 变量
     * @param args  占位符对应的参数；或当片段含 {@code ${}} 且传入 bean 时作为模板变量源
     */
    public T where(String where, Object... args) {
        this.where = where;
        this.args = args;
        return returnObj;
    }

    /**
     * 获取where参数
     *
     * @return
     */
    public Object[] getArgs() {
        return args;
    }

    public Condition<T> where() {
        return whereCondition;
    }

    /**
     * 获得where包装器
     *
     * @return
     */
    public Wrapper getWhereWrapper() {
        return whereWrapper;
    }

    /**
     * 设置Where条件包装器
     *
     * @param wrapper
     */
    public T where(Wrapper wrapper) {
        this.whereWrapper = wrapper;
        return returnObj;
    }

}
