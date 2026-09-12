package cn.vonce.sql.define;

import cn.vonce.sql.bean.Column;
import cn.vonce.sql.bean.Common;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.uitls.SqlBeanUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * P1 方言标注 / 渲染形态回归测试（2026-09-12）。
 *
 * <p>锁定 code review 报告 {@code .workbuddy/reports/code-review-2026-09-12.md} 中 P1-1/2/3 的修复：</p>
 * <ul>
 *   <li><b>P1-1 {@code dateTrunc}</b>：原来标注 MySQL 8.0 / MariaDB 10.3 / HSQLDB / SQLite 支持，
 *       实际这些库<b>都没有</b> {@code DATE_TRUNC}（MySQL、MariaDB 均无；SQL Server 2022 才有且 unit 是关键字）。
 *       现在只保留 PostgreSQL 与 H2。</li>
 *   <li><b>P1-2 {@code bitAnd/bitOr/bitXor}</b>：原来标注的方言全错（{@code bit_and} 是聚合函数）。
 *       现在统一渲染成运算符形式 {@code (a & b)}，且只标注真正支持该运算符的方言；
 *       PostgreSQL 的位异或是 {@code #} 而非 {@code ^}，故 {@code bitXor} 不含 PG。</li>
 *   <li><b>P1-3 其它错标</b>：{@code sha1} 去掉了 H2/HSQLDB/Derby（H2 实测无 SHA1）；
 *       {@code regexpLike} 去掉 MariaDB（MariaDB 只有 {@code REGEXP}/{@code RLIKE} 运算符）并给 PG 加 15+ 版本门；
 *       {@code regexpLike/Replace} 补上 H2（实测可用）、去掉 SQLite；
 *       {@code charLength/octetLength/bitLength} 排除 Oracle / SQL Server / SQLite / Derby。</li>
 * </ul>
 *
 * <p>注：断言里的反引号 / 双引号是框架按方言自动加的标识符转义（MySQL 反引号、PG 双引号），
 * 与本次修复无关，但精确断言可以顺带锁住整条 SQL。</p>
 *
 * @author Jovi
 */
public class SqlFunP1DialectTest {

    // ---------------- 工具 ----------------

    private static SqlBeanMeta meta(DbType dbType) {
        return meta(dbType, 0, 0);
    }

    private static SqlBeanMeta meta(DbType dbType, int major, int minor) {
        SqlBeanMeta m = new SqlBeanMeta();
        m.setDbType(dbType);
        m.setDatabaseMajorVersion(major);
        m.setDatabaseMinorVersion(minor);
        SqlBeanConfig cfg = new SqlBeanConfig();
        cfg.setToUpperCase(false);
        m.setSqlBeanConfig(cfg);
        return m;
    }

    private static Common common(DbType dbType) {
        Common c = new Common();
        c.setSqlBeanMeta(meta(dbType));
        return c;
    }

    private static String render(SqlFun fun, DbType dbType) {
        return SqlBeanUtil.getSqlFunction(common(dbType), fun);
    }

    /** 断言在指定方言（可带版本）通过校验。 */
    private static void supported(String label, SqlFun fun, DbType dbType, int major, int minor) {
        Assert.assertNull(label + " 应支持 " + dbType + " " + major + "." + minor,
                fun.checkDialect(meta(dbType, major, minor)));
    }

    /** 断言在指定方言（可带版本）被拒绝。 */
    private static void rejected(String label, SqlFun fun, DbType dbType, int major, int minor) {
        Assert.assertNotNull(label + " 不应支持 " + dbType + " " + major + "." + minor,
                fun.checkDialect(meta(dbType, major, minor)));
    }

    // ---------------- P1-1 dateTrunc ----------------

    @Test
    public void dateTruncOnlyPostgresAndH2() {
        SqlFun f = SqlFun.dateTrunc("day", new Column("created_at"));
        supported("dateTrunc", f, DbType.Postgresql, 0, 0);
        supported("dateTrunc", f, DbType.H2, 0, 0);
        // MySQL / MariaDB 根本没有 DATE_TRUNC（旧标注给了 MySQL 8.0 / MariaDB 10.3，是错的）
        rejected("dateTrunc", f, DbType.MySQL, 99, 0);
        rejected("dateTrunc", f, DbType.MariaDB, 99, 0);
        rejected("dateTrunc", f, DbType.SQLite, 99, 0);
        rejected("dateTrunc", f, DbType.Hsql, 99, 0);
        rejected("dateTrunc", f, DbType.Oracle, 99, 0);
        rejected("dateTrunc", f, DbType.SQLServer, 99, 0);
        rejected("dateTrunc", f, DbType.DB2, 99, 0);
        rejected("dateTrunc", f, DbType.Derby, 99, 0);
    }

    @Test
    public void dateTruncFieldIsRenderedAsStringLiteral() {
        // PG / H2 的第一个参数都是 'day' 这种字符串字面量，不能是裸标识符
        String sql = render(SqlFun.dateTrunc("day", new Column("created_at")), DbType.Postgresql);
        Assert.assertEquals("date_trunc('day', \"created_at\")", sql);
        // 关键：字段没有被 RawValue 化（否则会变成 date_trunc(day, ...) 这种非法形式）
        Assert.assertFalse("第一个参数必须是字符串字面量: " + sql, sql.contains("date_trunc(day"));
    }

    // ---------------- P1-2 位运算 ----------------

    @Test
    public void bitAndRendersOperatorForm() {
        String sql = render(SqlFun.bitAnd(new Column("flags"), 2), DbType.MySQL);
        Assert.assertEquals("(`flags` & 2)", sql);
        Assert.assertFalse("不能再生成 bit_and(...) 聚合函数: " + sql, sql.contains("bit_and"));
    }

    @Test
    public void bitOrAndXorRenderOperatorForm() {
        Assert.assertEquals("(`flags` | 4)", render(SqlFun.bitOr(new Column("flags"), 4), DbType.MySQL));
        Assert.assertEquals("(`flags` ^ 8)", render(SqlFun.bitXor(new Column("flags"), 8), DbType.MySQL));
    }

    @Test
    public void bitwiseOperatorsDialectMatrix() {
        SqlFun and = SqlFun.bitAnd(new Column("flags"), 1);
        SqlFun or = SqlFun.bitOr(new Column("flags"), 1);
        SqlFun xor = SqlFun.bitXor(new Column("flags"), 1);

        // & / | 只在这些库有运算符
        for (SqlFun f : new SqlFun[]{and, or}) {
            supported("bitOp", f, DbType.MySQL, 0, 0);
            supported("bitOp", f, DbType.MariaDB, 0, 0);
            supported("bitOp", f, DbType.Postgresql, 0, 0);
            supported("bitOp", f, DbType.SQLServer, 0, 0);
            supported("bitOp", f, DbType.SQLite, 0, 0);
            rejected("bitOp", f, DbType.Oracle, 99, 0);
            rejected("bitOp", f, DbType.DB2, 99, 0);
            // H2 实测 '&'/'|' 报语法错误（只有 BITAND/BITOR 函数）
            rejected("bitOp", f, DbType.H2, 99, 0);
            rejected("bitOp", f, DbType.Hsql, 99, 0);
            rejected("bitOp", f, DbType.Derby, 99, 0);
        }

        // ^：PG 的位异或是 # 不是 ^；SQLite 无位异或运算符
        supported("bitXor", xor, DbType.MySQL, 0, 0);
        supported("bitXor", xor, DbType.MariaDB, 0, 0);
        supported("bitXor", xor, DbType.SQLServer, 0, 0);
        rejected("bitXor", xor, DbType.Postgresql, 99, 0);
        rejected("bitXor", xor, DbType.SQLite, 99, 0);
        rejected("bitXor", xor, DbType.H2, 99, 0);
        rejected("bitXor", xor, DbType.Oracle, 99, 0);
    }

    // ---------------- P1-3 加密 / 正则 / 长度 ----------------

    @Test
    public void sha1DoesNotClaimH2HsqlDerby() {
        SqlFun f = SqlFun.sha1(new Column("pwd"));
        supported("sha1", f, DbType.MySQL, 0, 0);
        supported("sha1", f, DbType.MariaDB, 0, 0);
        // H2 实测没有 SHA1 函数；旧标注给了 H2 / HSQLDB / Derby，都是错的
        rejected("sha1", f, DbType.H2, 99, 0);
        rejected("sha1", f, DbType.Hsql, 99, 0);
        rejected("sha1", f, DbType.Derby, 99, 0);
        rejected("sha1", f, DbType.Postgresql, 99, 0);
    }

    @Test
    public void sha2DoesNotClaimH2() {
        SqlFun f = SqlFun.sha2(new Column("pwd"), 256);
        supported("sha2", f, DbType.MySQL, 8, 0);
        supported("sha2", f, DbType.MariaDB, 10, 5);
        rejected("sha2", f, DbType.H2, 99, 0);
        rejected("sha2", f, DbType.Hsql, 99, 0);
        rejected("sha2", f, DbType.Postgresql, 99, 0);
    }

    @Test
    public void lengthFunctionsExcludeOracleSqlServerSqlite() {
        SqlFun charLen = SqlFun.charLength(new Column("name"));
        SqlFun octetLen = SqlFun.octetLength(new Column("name"));
        SqlFun bitLen = SqlFun.bitLength(new Column("name"));
        for (SqlFun f : new SqlFun[]{charLen, octetLen, bitLen}) {
            supported("lengthFun", f, DbType.MySQL, 0, 0);
            supported("lengthFun", f, DbType.MariaDB, 0, 0);
            supported("lengthFun", f, DbType.Postgresql, 0, 0);
            supported("lengthFun", f, DbType.H2, 0, 0);
            rejected("lengthFun", f, DbType.Oracle, 99, 0);
            rejected("lengthFun", f, DbType.SQLServer, 99, 0);
            rejected("lengthFun", f, DbType.SQLite, 99, 0);
            rejected("lengthFun", f, DbType.Derby, 99, 0);
        }
        Assert.assertEquals("char_length(`name`)", render(charLen, DbType.MySQL));
        Assert.assertEquals("octet_length(`name`)", render(octetLen, DbType.MySQL));
        Assert.assertEquals("bit_length(`name`)", render(bitLen, DbType.MySQL));
    }

    @Test
    public void regexpLikeExcludesMariaDbAndSqliteAndRequiresPg15() {
        SqlFun f = SqlFun.regexpLike(new Column("name"), "^a.*");
        supported("regexpLike", f, DbType.MySQL, 8, 0);
        supported("regexpLike", f, DbType.Postgresql, 15, 0);
        supported("regexpLike", f, DbType.Oracle, 0, 0);
        // H2 实测支持 REGEXP_LIKE，旧标注却把它列为不支持
        supported("regexpLike", f, DbType.H2, 0, 0);
        // MariaDB 没有 REGEXP_LIKE 函数（只有 REGEXP / RLIKE 运算符）
        rejected("regexpLike", f, DbType.MariaDB, 99, 0);
        // SQLite 只有 REGEXP 运算符且需自行注册函数
        rejected("regexpLike", f, DbType.SQLite, 99, 0);
        // MySQL 8.0 之前、PG 15 之前不支持
        rejected("regexpLike", f, DbType.MySQL, 5, 7);
        rejected("regexpLike", f, DbType.Postgresql, 14, 0);
    }

    @Test
    public void regexpSubstrRequiresPg15() {
        SqlFun f = SqlFun.regexpSubstr(new Column("name"), "\\d+");
        supported("regexpSubstr", f, DbType.Postgresql, 15, 0);
        supported("regexpSubstr", f, DbType.MySQL, 8, 0);
        supported("regexpSubstr", f, DbType.H2, 0, 0);
        rejected("regexpSubstr", f, DbType.Postgresql, 14, 0);
        rejected("regexpSubstr", f, DbType.SQLite, 99, 0);
    }

    @Test
    public void regexpReplaceSupportsH2AndRejectsSqlite() {
        SqlFun f = SqlFun.regexpReplace(new Column("name"), "a", "b");
        supported("regexpReplace", f, DbType.H2, 0, 0);
        supported("regexpReplace", f, DbType.MySQL, 8, 0);
        supported("regexpReplace", f, DbType.MariaDB, 10, 0);
        supported("regexpReplace", f, DbType.Postgresql, 0, 0);
        rejected("regexpReplace", f, DbType.SQLite, 99, 0);
        rejected("regexpReplace", f, DbType.SQLServer, 99, 0);
    }

    /**
     * 运算符型函数（funName 为空）被拒绝时，异常信息不能出现「SqlFun. 」这种缺名字的提示。
     */
    @Test
    public void operatorFunctionGivesReadableErrorLabel() {
        SqlFun f = SqlFun.bitAnd(new Column("flags"), 1);
        String check = f.checkDialect(meta(DbType.H2));
        Assert.assertNotNull(check);
        Assert.assertFalse("错误标识不能为空: '" + check + "'", check.trim().isEmpty());
        String msg = f.buildException(meta(DbType.H2)).getMessage();
        Assert.assertFalse("异常信息不能缺函数名: " + msg, msg.contains("SqlFun. "));
        Assert.assertTrue("异常信息应包含可读标识: " + msg, msg.contains("operator"));
    }
}
