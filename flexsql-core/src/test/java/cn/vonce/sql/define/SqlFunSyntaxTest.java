package cn.vonce.sql.define;

import cn.vonce.sql.bean.Column;
import cn.vonce.sql.bean.Common;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.uitls.SqlBeanUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * {@code CAST} / {@code EXTRACT} / {@code POSITION} 的参数分隔符回归测试。
 *
 * <p><b>背景（2026-09-12 修复的 P0）</b>：这三个函数是「关键字 + 空格」型语法
 * （{@code CAST(x AS t)} / {@code EXTRACT(f FROM d)} / {@code POSITION(s IN x)}），
 * 但渲染器无条件用逗号连接参数、且关键字被当作普通 {@code String} 加了引号，实际输出为：</p>
 * <pre>
 *   cast('col', 'AS', 'INTEGER' )
 *   extract(YEAR, 'FROM', 'col' )
 *   position(sub, 'IN', 'col' )
 * </pre>
 * <p>这些 SQL 在任何数据库上都无法执行，而原来的测试只用 {@code contains("AS")} 断言，
 * 对错误输出同样成立 —— 属于「静默生成非法 SQL」。本测试用<b>精确断言</b>锁死语法。</p>
 *
 * <p>同时锁定两处细节：① 关键字不能被加引号；② 参数之间不能出现逗号；
 * ③ 普通函数仍用逗号分隔且不再有多余的尾随空格（旧实现 {@code deleteCharAt} 只删逗号、
 * 留下 {@code "md5('x' )"} 的空格）。</p>
 *
 * @author Jovi
 */
public class SqlFunSyntaxTest {

    private static Common common(DbType dbType) {
        SqlBeanMeta meta = new SqlBeanMeta();
        meta.setDbType(dbType);
        meta.setDatabaseMajorVersion(8);
        meta.setDatabaseMinorVersion(0);
        // 渲染 Column 时会读 SqlBeanConfig（toUpperCase），必须给一个空配置
        meta.setSqlBeanConfig(new SqlBeanConfig());
        Common common = new Common();
        common.setSqlBeanMeta(meta);
        return common;
    }

    private static String render(SqlFun fun) {
        return SqlBeanUtil.getSqlFunction(common(DbType.MySQL), fun);
    }

    // ---------------- CAST ----------------

    @Test
    public void castRendersStandardSyntax() {
        String sql = render(SqlFun.cast("123", "INTEGER"));
        Assert.assertEquals("cast('123' AS INTEGER)", sql);
    }

    @Test
    public void castDoesNotQuoteKeywordOrType() {
        String sql = render(SqlFun.cast("123", "INTEGER"));
        Assert.assertFalse("CAST 参数间不能有逗号", sql.contains(","));
        Assert.assertFalse("AS 关键字不能被加引号", sql.contains("'AS'"));
        Assert.assertFalse("目标类型不能被加引号", sql.contains("'INTEGER'"));
    }

    @Test
    public void castKeepsValueExpressionAndRawType() {
        // 列按列名渲染（不加引号），带参数的复杂类型原样输出
        String sql = render(SqlFun.cast(new Column("age"), "VARCHAR(50)"));
        Assert.assertTrue("应以 cast( 开头: " + sql, sql.startsWith("cast("));
        Assert.assertTrue("类型应原样拼在 AS 之后: " + sql, sql.endsWith(" AS VARCHAR(50))"));
        Assert.assertFalse("目标类型不能被加引号", sql.contains("'VARCHAR(50)'"));
    }

    // ---------------- EXTRACT ----------------

    @Test
    public void extractRendersFieldFromDate() {
        String sql = render(SqlFun.extract("YEAR", "2024-01-01"));
        Assert.assertEquals("extract(YEAR FROM '2024-01-01')", sql);
    }

    @Test
    public void extractDoesNotQuoteKeywords() {
        String sql = render(SqlFun.extract("YEAR", "2024-01-01"));
        Assert.assertFalse("EXTRACT 参数间不能有逗号", sql.contains(","));
        Assert.assertFalse("FROM 关键字不能被加引号", sql.contains("'FROM'"));
        Assert.assertFalse("提取字段不能被加引号", sql.contains("'YEAR'"));
        Assert.assertTrue("字段与 FROM 之间应有空格", sql.contains("YEAR FROM "));
    }

    // ---------------- POSITION ----------------

    @Test
    public void positionRendersInKeyword() {
        String sql = render(SqlFun.position("ab", "abcdef"));
        Assert.assertEquals("position('ab' IN 'abcdef')", sql);
    }

    @Test
    public void positionDoesNotQuoteKeyword() {
        String sql = render(SqlFun.position("ab", "abcdef"));
        Assert.assertFalse("POSITION 参数间不能有逗号", sql.contains(","));
        Assert.assertFalse("IN 关键字不能被加引号", sql.contains("'IN'"));
        Assert.assertTrue("子串与 IN 之间应有空格", sql.contains("'ab' IN "));
    }

    // ---------------- 回归：普通函数不受影响 ----------------

    @Test
    public void normalFunctionsStillCommaSeparated() {
        Assert.assertEquals("coalesce('a', 'b')", render(SqlFun.coalesce("a", "b")));
        Assert.assertEquals("ifnull('a', 'b')", render(SqlFun.ifNull("a", "b")));
    }

    @Test
    public void singleArgFunctionHasNoTrailingSpace() {
        // 旧实现会输出 md5('abc' ) —— 逗号被删掉但空格留下
        Assert.assertEquals("md5('abc')", render(SqlFun.md5("abc")));
    }

    @Test
    public void zeroArgFunctionRendersEmptyParens() {
        Assert.assertEquals("pi()", render(SqlFun.pi()));
        Assert.assertEquals("current_timestamp()", render(SqlFun.currentTimestamp()));
    }

    // ---------------- 分隔符机制本身 ----------------

    @Test
    public void separatorDefaultsToCommaAndIsOptInPerInstance() {
        Assert.assertEquals(SqlConstant.COMMA, SqlFun.trim("x").getSeparator());
        Assert.assertEquals(SqlConstant.SPACES, SqlFun.cast("x", "INTEGER").getSeparator());
        // 关键：spaceSeparated 是实例级标记，不能在创建后泄漏到其它函数实例
        Assert.assertEquals(SqlConstant.COMMA, SqlFun.trim("x").getSeparator());
        Assert.assertEquals(SqlConstant.SPACES, SqlFun.position("a", "b").getSeparator());
        Assert.assertEquals(SqlConstant.SPACES, SqlFun.extract("YEAR", "d").getSeparator());
    }

    @Test
    public void argSeparatorFallsBackToCommaWhenBlank() {
        Assert.assertEquals(SqlConstant.COMMA, SqlFun.trim("x").argSeparator(null).getSeparator());
        Assert.assertEquals(SqlConstant.COMMA, SqlFun.trim("x").argSeparator("").getSeparator());
        Assert.assertEquals("|", SqlFun.trim("x").argSeparator("|").getSeparator());
    }
}
