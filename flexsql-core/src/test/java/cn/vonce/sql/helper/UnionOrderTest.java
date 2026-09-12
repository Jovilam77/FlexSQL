package cn.vonce.sql.helper;

import cn.vonce.sql.bean.Select;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.enumerate.DbType;
import org.junit.Assert;
import org.junit.Test;

/**
 * UNION 子句位置 / 括号回归测试（P1-4）。
 *
 * <p><b>背景</b>：原实现把 {@code UNION / UNION ALL} 分支追加在 {@code ORDER BY / LIMIT / FOR UPDATE}
 * <b>之后</b>，会生成</p>
 * <pre>
 *   SELECT id FROM t_a ORDER BY id DESC LIMIT 0,10 FOR UPDATE UNION SELECT id FROM t_b
 * </pre>
 * <p>MySQL 报 {@code Incorrect usage of UNION and ORDER BY}，PostgreSQL 报
 * {@code syntax error at or near "UNION"} —— 属于「必然报错的 SQL」。</p>
 *
 * <p>修复后：UNION 分支紧接主体 SELECT，ORDER BY / LIMIT / 行锁排在整个复合查询之后；
 * 且分支<b>只在自带 ORDER BY / 分页 / 行锁时</b>才加括号 —— 因为 SQLite 的复合查询 grammar
 * 只允许 {@code select-core} 作为操作数，不接受带括号的分支。</p>
 *
 * @author Jovi
 */
public class UnionOrderTest {

    private static SqlBeanMeta mysqlMeta() {
        SqlBeanMeta m = new SqlBeanMeta();
        m.setDbType(DbType.MySQL);
        SqlBeanConfig cfg = new SqlBeanConfig();
        cfg.setToUpperCase(false);
        m.setSqlBeanConfig(cfg);
        return m;
    }

    private static Select select(String table) {
        Select s = new Select();
        s.setSqlBeanMeta(mysqlMeta());
        s.table(table);
        s.column("id");
        return s;
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    @Test
    public void unionBranchComesBeforeOrderBy() {
        Select main = select("t_a");
        main.union(select("t_b"));
        main.orderByDesc("id");
        String sql = normalize(SqlHelper.buildSelectSql(main));
        int union = sql.indexOf("UNION");
        int orderBy = sql.indexOf("ORDER BY");
        Assert.assertTrue("应生成 UNION: " + sql, union >= 0);
        Assert.assertTrue("应生成 ORDER BY: " + sql, orderBy >= 0);
        Assert.assertTrue("UNION 必须位于 ORDER BY 之前（旧实现顺序相反，必然语法错误）: " + sql,
                union < orderBy);
    }

    @Test
    public void unionAllBranchComesBeforeOrderBy() {
        Select main = select("t_a");
        main.unionAll(select("t_b"));
        main.orderByDesc("id");
        String sql = normalize(SqlHelper.buildSelectSql(main));
        Assert.assertTrue(sql, sql.indexOf("UNION ALL") < sql.indexOf("ORDER BY"));
    }

    @Test
    public void plainUnionBranchIsNotParenthesized() {
        // SQLite 不接受带括号的复合操作数 → 常规分支不加括号，输出 a UNION b 的朴素形式
        Select main = select("t_a");
        main.union(select("t_b"));
        String sql = normalize(SqlHelper.buildSelectSql(main));
        Assert.assertTrue("常规 UNION 分支应输出 `UNION SELECT`: " + sql, sql.contains("UNION SELECT"));
        Assert.assertFalse("常规 UNION 分支不应加括号: " + sql, sql.contains("UNION (SELECT"));
    }

    @Test
    public void branchWithItsOwnOrderIsParenthesized() {
        Select main = select("t_a");
        Select branch = select("t_b");
        branch.orderByDesc("id");
        main.union(branch);
        String sql = normalize(SqlHelper.buildSelectSql(main));
        Assert.assertTrue("自带 ORDER BY 的分支应加括号（否则排序语义会作用于整个复合查询）: " + sql,
                sql.contains("UNION (SELECT"));
    }

    /**
     * UNION 与行锁不能共存：PostgreSQL 明确禁止，MySQL 也无法对 UNION 结果加锁。
     * 修复后应忽略锁子句而不是生成必然报错的 SQL。
     */
    @Test
    public void unionIgnoresLockClause() {
        Select main = select("t_a");
        main.union(select("t_b"));
        main.forUpdate();
        String sql = normalize(SqlHelper.buildSelectSql(main));
        Assert.assertTrue("应生成 UNION: " + sql, sql.contains("UNION"));
        Assert.assertFalse("UNION 查询不应生成 FOR UPDATE: " + sql, sql.contains("FOR UPDATE"));
    }
}
