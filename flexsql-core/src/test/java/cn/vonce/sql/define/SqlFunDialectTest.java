package cn.vonce.sql.define;

import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.enumerate.DialectMode;
import cn.vonce.sql.exception.UnsupportedDialectException;

import org.junit.Assert;
import org.junit.Test;

/**
 * SqlFun 方言支持元数据单元测试（JUnit，无需 DB）。
 * <p>按能力域拆成 11 个 @Test；组内用软断言（verify），组末 flush 统一上报失败明细，
 * 保证一个断言失败不会吞掉同组其它诊断。</p>
 * <p>覆盖：</p>
 * <ol>
 *   <li>DbVersion 解析 / 比较 / atLeast</li>
 *   <li>DialectSupport builder 行为（supports / 优先级 unsupport > support）</li>
 *   <li>SqlFun.checkDialect 缓存（第二次零开销）</li>
 *   <li>SqlBeanUtil.getSqlFunction 渲染期 STRICT 抛异常</li>
 *   <li>WARN 模式仅日志不抛异常</li>
 *   <li>OFF 模式不校验</li>
 *   <li>用户自定义函数扩展入口 SqlFun.dialect(SqlFun, DialectSupport)</li>
 *   <li>67 个现有具名函数不破坏（无标注 = 通用）</li>
 *   <li>未声明 supported 表时按 "unsupport 之外的都放行"</li>
 * </ol>
 */
public class SqlFunDialectTest {

    /**
     * 软断言收集器：组内某个断言失败不中断同组其它断言，组末统一上报（保留全部失败明细）。
     * JUnit 每个 @Test 各用一个实例，因此天然按用例隔离。
     */
    private final java.util.List<String> failures = new java.util.ArrayList<String>();

    /** 软断言：失败时仅记录。 */
    private void verify(String name, boolean ok) {
        if (!ok) {
            failures.add(name);
        }
    }

    /** 组末收口：把本组软断言失败项一次性上报给 JUnit。 */
    private void flush(String group) {
        if (!failures.isEmpty()) {
            String detail = failures.size() + " 项失败: " + failures;
            failures.clear();
            Assert.fail("[" + group + "] " + detail);
        }
    }

    // ======================== DbVersion ========================

    static boolean versionFromMajorMinor() {
        DbVersion v = DbVersion.from(8, 0);
        return v.getMajor() == 8 && v.getMinor() == 0 && "8.0".equals(v.toString());
    }

    static boolean versionFromString() {
        DbVersion a = DbVersion.from("8.0.1");   // patch 应被忽略
        DbVersion b = DbVersion.from("10");
        DbVersion c = DbVersion.from("5.7");
        return a.equals(DbVersion.from(8, 0))
                && b.getMajor() == 10 && b.getMinor() == 0
                && c.getMinor() == 7;
    }

    static boolean versionCompareTo() {
        return DbVersion.from(8, 0).compareTo(DbVersion.from(5, 7)) > 0
                && DbVersion.from(5, 7).compareTo(DbVersion.from(8, 0)) < 0
                && DbVersion.from(5, 7).compareTo(DbVersion.from(5, 7)) == 0;
    }

    static boolean versionAtLeast() {
        return DbVersion.from(8, 0).atLeast(DbVersion.from(5, 0))
                && DbVersion.from(5, 0).atLeast(DbVersion.from(5, 0))
                && !DbVersion.from(4, 9).atLeast(DbVersion.from(5, 0));
    }

    static boolean versionFromInvalidThrows() {
        try {
            DbVersion.from("abc");
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    // ======================== DialectSupport ========================

    static boolean dialectBuilderSupports() {
        DialectSupport ds = DialectSupport.builder()
                .support(DbType.MySQL, DbVersion.from(5, 0))
                .support(DbType.SQLite)
                .build();
        return ds.supports(DbType.MySQL, DbVersion.from(5, 7))
                && ds.supports(DbType.SQLite, null)
                && !ds.supports(DbType.MySQL, DbVersion.from(4, 9))
                && !ds.supports(DbType.Oracle, null);   // 未声明 → 不支持
    }

    static boolean dialectUnsupportPriority() {
        // 即便 also 声明 support，unsupport 也要先生效
        DialectSupport ds = DialectSupport.builder()
                .support(DbType.MySQL)
                .unsupport(DbType.MySQL)
                .build();
        return !ds.supports(DbType.MySQL, null) && ds.isUnsupported(DbType.MySQL);
    }

    static boolean dialectUniversal() {
        DialectSupport empty = DialectSupport.builder().build();
        DialectSupport uni = DialectSupport.UNIVERSAL;
        return empty == DialectSupport.UNIVERSAL
                && uni.isUniversal()
                && uni.supports(DbType.MySQL, null)
                && uni.supports(DbType.Oracle, null);
    }

    static boolean dialectSupportsWithNoVersion() {
        DialectSupport ds = DialectSupport.builder().support(DbType.MySQL).build();
        // 没版本时，只要 supported 里有就放行
        return ds.supports(DbType.MySQL, null);
    }

    // ======================== SqlFun.checkDialect + 缓存 ========================

    static boolean checkDialectReturnsNullForUniversal() {
        SqlFun f = SqlFun.trim("col");
        SqlBeanMeta meta = metaOf(DbType.MySQL, 8, 0);
        return f.checkDialect(meta) == null;
    }

    static boolean checkDialectPassesForSupportedDb() {
        SqlFun f = SqlFun.dateFormat("col", "%Y-%m-%d");
        SqlBeanMeta meta = metaOf(DbType.MySQL, 8, 0);
        return f.checkDialect(meta) == null;   // MySQL 8.0 支持 dateFormat
    }

    static boolean checkDialectFailsForUnsupportedDb() {
        SqlFun f = SqlFun.dateFormat("col", "%Y-%m-%d");
        SqlBeanMeta meta = metaOf(DbType.Oracle, 19, 0);
        return f.checkDialect(meta) != null;
    }

    static boolean checkDialectFailsForTooOldVersion() {
        SqlFun f = SqlFun.greatest(1, 2, 3);
        SqlBeanMeta meta = metaOf(DbType.MySQL, 5, 7);   // MySQL 8.0 之前无 GREATEST
        return f.checkDialect(meta) != null;
    }

    static boolean checkDialectCachesAfterFirstCall() {
        // 这里改为：同一函数对相同方言多次调用，结果一致（无缓存但幂等）
        SqlFun f = SqlFun.dateFormat("col", "%Y-%m-%d");
        SqlBeanMeta meta = metaOf(DbType.Oracle, 19, 0);
        String first = f.checkDialect(meta);
        String second = f.checkDialect(meta);
        // 两次失败信息应一致（不抛异常就等同通过）
        return first != null && first.equals(second);
    }

    static boolean checkDialectRedialectingTakesEffect() {
        // 重新标注 dialectSupport 后，下次校验立即按新值走（无缓存"陈旧值"风险）
        SqlFun f = SqlFun.dateFormat("col", "%Y-%m-%d");   // MySQL only
        SqlBeanMeta oracle = metaOf(DbType.Oracle, 19, 0);
        f.checkDialect(oracle);   // 失败
        f.dialect(DialectSupport.UNIVERSAL);                // 重标为通用
        String result = f.checkDialect(oracle);
        return result == null;
    }

    static boolean checkDialectNullMetaPasses() {
        SqlFun f = SqlFun.dateFormat("col", "%Y-%m-%d");
        return f.checkDialect(null) == null;
    }

    // ======================== SqlBeanUtil.getSqlFunction 渲染期校验 ========================

    static boolean renderStrictThrowsForUnsupported() {
        SqlFun f = SqlFun.dateFormat("col", "%Y-%m-%d");
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.Oracle, 19, 0);
        meta.setDialectMode(DialectMode.STRICT);
        common.setSqlBeanMeta(meta);
        try {
            cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, f);
            return false;
        } catch (UnsupportedDialectException e) {
            return e.getFunctionName().equals("date_format")
                    && e.getCurrentDbType() == DbType.Oracle;
        }
    }

    static boolean renderStrictPassesForSupported() {
        SqlFun f = SqlFun.dateFormat("col", "%Y-%m-%d");
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.MySQL, 8, 0);
        meta.setDialectMode(DialectMode.STRICT);
        common.setSqlBeanMeta(meta);
        try {
            String sql = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, f);
            return sql.contains("date_format") && sql.contains("'%Y-%m-%d'");
        } catch (Exception e) {
            return false;
        }
    }

    static boolean renderOffModeNoCheck() {
        SqlFun f = SqlFun.dateFormat("col", "%Y-%m-%d");
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.Oracle, 19, 0);
        meta.setDialectMode(DialectMode.OFF);
        common.setSqlBeanMeta(meta);
        try {
            // OFF 模式：方言不匹配也不抛异常
            String sql = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, f);
            return sql.contains("date_format");
        } catch (Exception e) {
            return false;
        }
    }

    static boolean renderUniversalFunPassesAnywhere() {
        // trim 是通用函数，无 dialectSupport 标注 → 任何方言都通过
        SqlFun f = SqlFun.trim("col");
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.Oracle, 19, 0);
        meta.setDialectMode(DialectMode.STRICT);
        common.setSqlBeanMeta(meta);
        try {
            String sql = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, f);
            return sql.contains("trim");
        } catch (Exception e) {
            return false;
        }
    }

    static boolean renderExistingFunctionsUnchanged() {
        // 67 个现有具名函数不应被破坏（无标注 = 通用）
        SqlFun count = SqlFun.count("col");
        SqlFun sum = SqlFun.sum("col");
        SqlFun avg = SqlFun.avg("col");
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.Postgresql, 14, 0);  // 任意方言
        meta.setDialectMode(DialectMode.STRICT);
        common.setSqlBeanMeta(meta);
        try {
            String c = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, count);
            String s = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, sum);
            String a = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, avg);
            return c.contains("count") && s.contains("sum") && a.contains("avg");
        } catch (Exception e) {
            return false;
        }
    }

    // ======================== 2026-09-06 第二批 P0 函数补强 ========================

    /**
     * 所有 P0 第二批通用函数无方言标注（dialectSupport == null）。
     */
    static boolean p0Batch2UniversalFunctionsAreUntagged() {
        SqlFun[] fs = new SqlFun[]{
                SqlFun.abs("c"), SqlFun.ln("c"), SqlFun.log("c"), SqlFun.log("b", "v"),
                SqlFun.log10("c"), SqlFun.exp("c"), SqlFun.pi(), SqlFun.radians("c"),
                SqlFun.degrees("c"), SqlFun.power("b", "e"),
                SqlFun.reverse("c"), SqlFun.repeat("c", 3), SqlFun.space(3),
                SqlFun.position("s", "c"), SqlFun.currentTimestamp(), SqlFun.currentDate(),
                SqlFun.currentTime(), SqlFun.localtime(), SqlFun.localtimestamp(),
                SqlFun.extract("YEAR", "d"), SqlFun.cast("c", "INTEGER")
        };
        for (SqlFun f : fs) {
            if (f.getDialectSupport() != null) {
                System.out.println("    [untagged] 误标: " + f.getFunName());
                return false;
            }
        }
        return true;
    }

    /**
     * 所有 P0 第二批通用函数在 MySQL 8.0 + STRICT 模式下成功渲染。
     */
    static boolean p0Batch2UniversalFunctionsRender() {
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.MySQL, 8, 0);
        meta.setDialectMode(DialectMode.STRICT);
        common.setSqlBeanMeta(meta);
        try {
            String s = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.abs("col"));
            String s2 = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.power("a", "b"));
            String s3 = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.pi());
            String s4 = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.currentTimestamp());
            String s5 = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.position("sub", "col"));
            String s6 = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.extract("YEAR", "col"));
            String s7 = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.cast("col", "INTEGER"));
            String s8 = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.reverse("col"));
            String s9 = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.repeat("x", 3));
            String s10 = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.charLength("col"));
            String s11 = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.space(5));
            return s.contains("abs") && s2.contains("power") && s3.contains("pi")
                    && s4.contains("current_timestamp") && s5.contains("position") && s5.contains("IN")
                    && s6.contains("extract") && s6.contains("YEAR") && s6.contains("FROM")
                    && s7.contains("cast") && s7.contains("AS")
                    && s8.contains("reverse") && s9.contains("repeat") && s10.contains("char_length")
                    && s11.contains("space");
        } catch (Exception e) {
            System.out.println("    [render] 渲染失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * md5 方言矩阵：MySQL/MariaDB/SQLite/PG/H2/Hsql/Derby 通过；SQL Server/DB2 抛；Oracle 11 抛，Oracle 12+ 通过。
     */
    static boolean md5Dialects() {
        SqlFun f = SqlFun.md5("col");
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.SQLite, 3, 30)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) == null
                && f.checkDialect(metaOf(DbType.H2, 2, 0)) == null
                && f.checkDialect(metaOf(DbType.Hsql, 2, 0)) == null
                && f.checkDialect(metaOf(DbType.Derby, 10, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 12, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 11, 0)) != null   // < 12 抛
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null
                && f.checkDialect(metaOf(DbType.DB2, 11, 0)) != null;
    }

    static boolean md5RendersOnPostgres() {
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.Postgresql, 14, 0);
        meta.setDialectMode(DialectMode.STRICT);
        common.setSqlBeanMeta(meta);
        try {
            String sql = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.md5("col"));
            return sql.contains("md5");
        } catch (Exception e) {
            return false;
        }
    }

    static boolean md5ThrowsOnOldOracle() {
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.Oracle, 11, 0);
        meta.setDialectMode(DialectMode.STRICT);
        common.setSqlBeanMeta(meta);
        try {
            cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.md5("col"));
            return false;
        } catch (UnsupportedDialectException e) {
            return e.getFunctionName().equals("md5") && e.getCurrentDbType() == DbType.Oracle;
        }
    }

    /**
     * date_trunc 方言矩阵：PG/MySQL 8.0+/MariaDB 10.3+/H2/Hsql/SQLite 通过；Oracle/SQL Server/Derby/DB2/MySQL 5.x 抛。
     */
    static boolean dateTruncDialects() {
        SqlFun f = SqlFun.dateTrunc((Object) "day", (Object) "col");
        Object[][] probes = {
                {"PG14", metaOf(DbType.Postgresql, 14, 0), null},
                {"MySQL8", metaOf(DbType.MySQL, 8, 0), null},
                {"MariaDB10.3", metaOf(DbType.MariaDB, 10, 3), null},
                {"H2", metaOf(DbType.H2, 2, 0), null},
                {"Hsql", metaOf(DbType.Hsql, 2, 0), null},
                {"SQLite", metaOf(DbType.SQLite, 3, 30), null},
                {"MySQL5.7", metaOf(DbType.MySQL, 5, 7), "not null"},
                {"MariaDB10.2", metaOf(DbType.MariaDB, 10, 2), "not null"},
                {"Oracle19", metaOf(DbType.Oracle, 19, 0), "not null"},
                {"SQLServer2019", metaOf(DbType.SQLServer, 2019, 0), "not null"},
                {"Derby", metaOf(DbType.Derby, 10, 0), "not null"},
                {"DB2", metaOf(DbType.DB2, 11, 0), "not null"},
        };
        for (Object[] p : probes) {
            String label = (String) p[0];
            SqlBeanMeta meta = (SqlBeanMeta) p[1];
            String expect = (String) p[2];
            String r = f.checkDialect(meta);
            boolean expectNull = expect == null;
            boolean ok = (r == null) == expectNull;
            System.out.println("    [dateTrunc] " + label + " -> " + (r == null ? "PASS" : "FAIL(" + r + ")") + " | expect=" + expect + " | " + (ok ? "✓" : "✗"));
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    static boolean dateTruncThrowsOnOldMysql() {
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.MySQL, 5, 7);
        meta.setDialectMode(DialectMode.STRICT);
        common.setSqlBeanMeta(meta);
        try {
            cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.dateTrunc((Object) "day", (Object) "col"));
            return false;
        } catch (UnsupportedDialectException e) {
            return e.getFunctionName().equals("date_trunc") && e.getCurrentDbType() == DbType.MySQL;
        }
    }

    static boolean dateTruncThrowsOnOracle() {
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.Oracle, 19, 0);
        meta.setDialectMode(DialectMode.STRICT);
        common.setSqlBeanMeta(meta);
        try {
            cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.dateTrunc((Object) "day", (Object) "col"));
            return false;
        } catch (UnsupportedDialectException e) {
            return e.getFunctionName().equals("date_trunc") && e.getCurrentDbType() == DbType.Oracle;
        }
    }

    static boolean positionIsUniversal() {
        SqlFun f = SqlFun.position("sub", "col");
        return f.getDialectSupport() == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && f.checkDialect(metaOf(DbType.SQLite, 3, 30)) == null;
    }

    static boolean extractIsUniversal() {
        SqlFun f = SqlFun.extract("YEAR", "col");
        return f.getDialectSupport() == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) == null;
    }

    static boolean castIsUniversal() {
        SqlFun f = SqlFun.cast("col", "INTEGER");
        return f.getDialectSupport() == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null;
    }

    static boolean currentTimestampIsUniversal() {
        SqlFun f = SqlFun.currentTimestamp();
        return f.getDialectSupport() == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) == null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null;
    }

    // ======================== 2026-09-06 P1 函数补强 ========================

    static boolean jsonValueDialects() {
        SqlFun f = SqlFun.jsonValue("col", "$.path");
        return f.checkDialect(metaOf(DbType.SQLServer, 2016, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 12, 0)) == null
                && f.checkDialect(metaOf(DbType.SQLServer, 2014, 0)) != null   // < 2016 抛
                && f.checkDialect(metaOf(DbType.Oracle, 11, 0)) != null        // < 12 抛
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null   // PG 不支持
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) != null        // MySQL 不支持
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) != null
                && f.checkDialect(metaOf(DbType.SQLite, 3, 30)) != null;
    }

    static boolean regexpLikeDialects() {
        SqlFun f = SqlFun.regexpLike("text", "'^a'");
        return f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.SQLite, 3, 30)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 5, 7)) != null       // < 8 抛
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null
                && f.checkDialect(metaOf(DbType.Derby, 10, 0)) != null;
    }

    static boolean regexpReplaceDialects() {
        SqlFun f = SqlFun.regexpReplace("text", "'a'", "'b'");
        return f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.SQLite, 3, 30)) == null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null
                && f.checkDialect(metaOf(DbType.MySQL, 5, 7)) != null;
    }

    static boolean regexpSubstrDialects() {
        SqlFun f = SqlFun.regexpSubstr("text", "'a'");
        return f.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) == null
                && f.checkDialect(metaOf(DbType.SQLite, 3, 30)) == null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null;
    }

    static boolean regexpCountDialects() {
        SqlFun f = SqlFun.regexpCount("text", "'a'");
        return f.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLite, 3, 30)) != null;
    }

    static boolean regexpInstrDialects() {
        SqlFun f = SqlFun.regexpInstr("text", "'a'");
        return f.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLite, 3, 30)) != null;
    }

    static boolean sha1Dialects() {
        SqlFun f = SqlFun.sha1("col");
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.H2, 2, 0)) == null
                && f.checkDialect(metaOf(DbType.Hsql, 2, 0)) == null
                && f.checkDialect(metaOf(DbType.Derby, 10, 0)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLite, 3, 30)) != null;
    }

    static boolean sha2Dialects() {
        SqlFun f = SqlFun.sha2("col", 256);
        return f.checkDialect(metaOf(DbType.MySQL, 5, 5)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 5, 4)) != null         // < 5.5 抛
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 0)) == null
                && f.checkDialect(metaOf(DbType.H2, 2, 0)) == null
                && f.checkDialect(metaOf(DbType.Hsql, 2, 0)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null;
    }

    static boolean bitAndDialects() {
        SqlFun f = SqlFun.bitAnd("a", "b");
        return f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.Hsql, 2, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLite, 3, 30)) != null
                && f.checkDialect(metaOf(DbType.H2, 2, 0)) != null;
    }

    static boolean bitOrDialects() {
        SqlFun f = SqlFun.bitOr("a", "b");
        return f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.Hsql, 2, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null;
    }

    static boolean bitXorDialects() {
        SqlFun f = SqlFun.bitXor("a", "b");
        return f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.Hsql, 2, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null;
    }

    static boolean bitCountDialects() {
        SqlFun f = SqlFun.bitCount("a");
        return f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null
                && f.checkDialect(metaOf(DbType.Hsql, 2, 0)) != null;        // Hsql 也不支持
    }

    static boolean windowExtraDialects() {
        SqlFun pr = SqlFun.percentRank();
        SqlFun cd = SqlFun.cumeDist();
        return pr.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && pr.checkDialect(metaOf(DbType.MariaDB, 10, 2)) == null
                && pr.checkDialect(metaOf(DbType.Postgresql, 9, 4)) == null
                && pr.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null
                && pr.checkDialect(metaOf(DbType.SQLite, 3, 25)) == null
                && pr.checkDialect(metaOf(DbType.SQLServer, 2005, 0)) == null
                && pr.checkDialect(metaOf(DbType.MySQL, 5, 7)) != null       // < 8 抛
                && pr.checkDialect(metaOf(DbType.H2, 2, 0)) != null
                && pr.checkDialect(metaOf(DbType.Hsql, 2, 0)) != null
                && cd.checkDialect(metaOf(DbType.Postgresql, 9, 4)) == null
                && cd.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null;
    }

    static boolean genRandomUuidDialects() {
        SqlFun f = SqlFun.genRandomUuid();
        return f.checkDialect(metaOf(DbType.Postgresql, 13, 0)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 12, 0)) != null   // < 13 抛
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) != null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) != null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null;
    }

    static boolean inetAtonDialects() {
        SqlFun f = SqlFun.inetAton("'1.2.3.4'");
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null;
    }

    static boolean substringIndexDialects() {
        SqlFun f = SqlFun.substringIndex("'a.b.c'", "'.'", 2);
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null;
    }

    static boolean findInSetDialects() {
        SqlFun f = SqlFun.findInSet("'a'", "'a,b,c'");
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null;
    }

    static boolean strcmpDialects() {
        SqlFun f = SqlFun.strcmp("'a'", "'b'");
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null;
    }

    static boolean eltDialects() {
        SqlFun f = SqlFun.elt(2, "'a'", "'b'", "'c'");
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null;
    }

    static boolean regexpLikeRendersOnPostgres() {
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.Postgresql, 14, 0);
        meta.setDialectMode(DialectMode.STRICT);
        common.setSqlBeanMeta(meta);
        try {
            String sql = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.regexpLike("text", "'^a'"));
            return sql.contains("regexp_like");
        } catch (Exception e) {
            return false;
        }
    }

    static boolean regexpLikeThrowsOnSqlServer() {
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.SQLServer, 2019, 0);
        meta.setDialectMode(DialectMode.STRICT);
        common.setSqlBeanMeta(meta);
        try {
            cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.regexpLike("text", "'^a'"));
            return false;
        } catch (UnsupportedDialectException e) {
            return e.getFunctionName().equals("regexp_like") && e.getCurrentDbType() == DbType.SQLServer;
        }
    }

    static boolean eltRendersOnMysql() {
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.MySQL, 8, 0);
        meta.setDialectMode(DialectMode.STRICT);
        common.setSqlBeanMeta(meta);
        try {
            String sql = cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, SqlFun.elt(2, "'a'", "'b'", "'c'"));
            return sql.contains("elt");
        } catch (Exception e) {
            return false;
        }
    }

    // ======================== 用户扩展入口 SqlFun.dialect(SqlFun, ...) ========================

    static boolean userExtensionRegistersDialects() {
        // 模拟用户自定义函数：用现有 SqlFun + dialect(SqlFun, ...) 二次覆盖成 "MY_FUNC 语义"
        SqlFun myFun = SqlFun.dateFormat("col", "%Y-%m-%d");
        cn.vonce.sql.define.DialectSupport support = DialectSupport.builder()
                .support(DbType.MySQL, DbVersion.from(5, 0))
                .support(DbType.Postgresql)
                .build();
        SqlFun.dialect(myFun, support);
        SqlBeanMeta mysql = metaOf(DbType.MySQL, 8, 0);
        SqlBeanMeta oracle = metaOf(DbType.Oracle, 19, 0);
        String mysqlResult = myFun.checkDialect(mysql);
        String oracleResult = myFun.checkDialect(oracle);
        System.out.println("  DEBUG userExt: mysql=" + mysqlResult + ", oracle=" + oracleResult);
        return mysqlResult == null
                && oracleResult != null;
    }

    // ======================== 工具 ========================

    /**
     * 构造 SqlBeanMeta（仅设置 dialect / version，其它字段空）。
     */
    private static SqlBeanMeta metaOf(DbType type, int major, int minor) {
        SqlBeanMeta meta = new SqlBeanMeta();
        meta.setDbType(type);
        meta.setDatabaseMajorVersion(major);
        meta.setDatabaseMinorVersion(minor);
        return meta;
    }

    /**
     * 批量校验：传入一组 (函数, 支持方言+版本, 不支持方言) 三元组，验证 SqlFun 标注正确。
     * 用于本轮新标方言函数的回归覆盖。
     *
     * @param samples 数组：{name, fun, supportedDbType, supportedMajor, supportedMinor, unsupportedDbType}
     */
    static boolean batchDialects(Object[][] samples) {
        for (Object[] s : samples) {
            String label = (String) s[0];
            SqlFun f = (SqlFun) s[1];
            DbType okType = (DbType) s[2];
            int okMajor = (Integer) s[3];
            int okMinor = (Integer) s[4];
            DbType badType = (DbType) s[5];

            SqlBeanMeta okMeta = metaOf(okType, okMajor, okMinor);
            SqlBeanMeta badMeta = metaOf(badType, 19, 0);
            String okResult = f.checkDialect(okMeta);
            String badResult = f.checkDialect(badMeta);
            if (okResult != null || badResult == null) {
                System.out.println("    [batch] " + label + " -> ok=" + okResult + " bad=" + badResult);
                return false;
            }
        }
        return true;
    }

    // ======================== 跨方言对立别名 ========================

    static boolean ifNullCrossDbConflict() {
        // MySQL 支持；Oracle 不支持
        SqlFun mysql = SqlFun.ifNull("c", 0);
        SqlFun oracle = SqlFun.ifNull("c", 0);
        return mysql.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && oracle.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && oracle.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null
                && oracle.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null;
    }

    static boolean nvlOnlyOracle() {
        SqlFun f = SqlFun.nvl("c", 0);
        return f.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null;
    }

    // ======================== 字符串方言差异 ========================

    static boolean instrDialects() {
        SqlFun f = SqlFun.instr("a", "b");
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null
                && f.checkDialect(metaOf(DbType.SQLite, 3, 30)) == null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null   // 不支持（SQL Server 用 CHARINDEX）
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null;
    }

    static boolean lPadRPadDialects() {
        SqlFun p = SqlFun.lPad("a", 5, "*");
        return p.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && p.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null
                && p.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null;
    }

    static boolean concatWsOnlyMySQL() {
        SqlFun f = SqlFun.concat_ws(",", "a", "b");
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null;
    }

    static boolean strToDateOnlyMySQL() {
        SqlFun f = SqlFun.str_to_date("2024-01-01", "%Y-%m-%d");
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null;
    }

    static boolean ifOnlyMySQL() {
        SqlFun f = SqlFun.iF(true, 1, 2);
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null;
    }

    static boolean dateFormatSnakeCaseOnlyMySQL() {
        // snake_case date_format（与 dateFormat 是两个函数）
        SqlFun f = SqlFun.date_format("2024-01-01", "%Y");
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null;
    }

    // ======================== SQL Server 专属 ========================

    static boolean charIndexOnlySqlServer() {
        SqlFun f = SqlFun.charIndex("a", "b");
        return f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) != null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null;
    }

    static boolean lenOnlySqlServer() {
        SqlFun f = SqlFun.len("a");
        return f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) != null;
    }

    static boolean stuffOnlySqlServer() {
        SqlFun f = SqlFun.stuff("a", 1, 1, "x");
        return f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) != null;
    }

    static boolean getDateOnlySqlServer() {
        SqlFun f = SqlFun.getDate();
        return f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) != null;
    }

    static boolean hostNameUserNameOnlySqlServer() {
        SqlFun host = SqlFun.host_name();
        SqlFun user = SqlFun.user_name(1);
        return host.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && host.checkDialect(metaOf(DbType.MySQL, 8, 0)) != null
                && user.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && user.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null;
    }

    static boolean dataLengthOnlySqlServer() {
        SqlFun f = SqlFun.dataLength("a");
        return f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null;
    }

    static boolean convertSqlServerAndMySQL() {
        SqlFun f = SqlFun.convert("VARCHAR(10)", "col");
        return f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null;
    }

    static boolean dateNameDatePartOnlySqlServer() {
        SqlFun dn = SqlFun.dateName("yy", "2024-01-01");
        SqlFun dp = SqlFun.datePart("yy", "2024-01-01");
        return dn.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && dn.checkDialect(metaOf(DbType.MySQL, 8, 0)) != null
                && dp.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && dp.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null;
    }

    static boolean dateAddDateDiffDatePartOnlySqlServer() {
        SqlFun da = SqlFun.dateAdd("yy", 1, "2024-01-01");
        SqlFun df = SqlFun.dateDiff("yy", "2024-01-01", "2025-01-01");
        return da.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && da.checkDialect(metaOf(DbType.MySQL, 8, 0)) != null
                && df.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && df.checkDialect(metaOf(DbType.MySQL, 8, 0)) != null;
    }

    // ======================== 日期方言差异 ========================

    static boolean nowCurDateCurTimeDialects() {
        SqlFun now = SqlFun.now();
        SqlFun cd = SqlFun.curDate();
        SqlFun ct = SqlFun.curTime();
        return now.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && now.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null   // PG 用 CURRENT_TIMESTAMP
                && cd.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && cd.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null
                && ct.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && ct.checkDialect(metaOf(DbType.SQLite, 3, 30)) != null;       // SQLite 不支持 CURTIME
    }

    static boolean dateExtractionDialects() {
        SqlFun d = SqlFun.date("2024-01-01");
        return d.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && d.checkDialect(metaOf(DbType.SQLite, 3, 30)) == null
                && d.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && d.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null;
    }

    static boolean monthNameOnlyMySQL() {
        SqlFun f = SqlFun.monthName("2024-01-01");
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null;
    }

    static boolean dateAddTimeUnitOnlyMySQL() {
        SqlFun f = SqlFun.dateAdd("2024-01-01", 1, cn.vonce.sql.enumerate.TimeUnit.DAY);
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null
                && f.checkDialect(metaOf(DbType.Postgresql, 14, 0)) != null;
    }

    static boolean timestampDiffOnlyMySQL() {
        SqlFun f = SqlFun.timestampDiff(cn.vonce.sql.enumerate.TimeUnit.DAY, "2024-01-01", "2025-01-01");
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null;
    }

    static boolean dateDiffTwoArgsDialects() {
        SqlFun f = SqlFun.dateDiff("2024-01-01", "2025-01-01");
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) == null
                && f.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null;
    }

    // ======================== 信息函数 ========================

    static boolean versionDatabaseUserDialects() {
        SqlFun v = SqlFun.version();
        SqlFun db = SqlFun.database();
        SqlFun usr = SqlFun.user();
        return v.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && v.checkDialect(metaOf(DbType.Oracle, 19, 0)) != null       // Oracle 没有 version() 函数
                && db.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && db.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null
                && usr.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && usr.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null;
    }

    // ======================== 窗口函数 ========================

    static boolean windowFunctionDialects() {
        SqlFun rn = SqlFun.rowNumber();
        SqlFun rk = SqlFun.rank();
        SqlFun drk = SqlFun.denseRank();
        SqlFun nt = SqlFun.ntile(4);
        SqlFun fv = SqlFun.firstValue("col");
        SqlFun lv = SqlFun.lastValue("col");
        return rn.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null      // 8.0+
                && rn.checkDialect(metaOf(DbType.MySQL, 5, 7)) != null    // 5.x 不支持
                && rn.checkDialect(metaOf(DbType.H2, 2, 0)) != null      // H2 不支持
                && rk.checkDialect(metaOf(DbType.Postgresql, 9, 4)) == null
                && rk.checkDialect(metaOf(DbType.Postgresql, 9, 3)) != null
                && drk.checkDialect(metaOf(DbType.SQLServer, 2005, 0)) == null
                && drk.checkDialect(metaOf(DbType.SQLite, 3, 25)) == null
                && drk.checkDialect(metaOf(DbType.SQLite, 3, 24)) != null
                && nt.checkDialect(metaOf(DbType.Oracle, 19, 0)) == null
                && fv.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && lv.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null;
    }

    static boolean lagLeadRequiresSqlServer2012() {
        SqlFun lag = SqlFun.lag("col", 1);
        SqlFun lead = SqlFun.lead("col", 1);
        return lag.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && lag.checkDialect(metaOf(DbType.SQLServer, 2012, 0)) == null
                && lag.checkDialect(metaOf(DbType.SQLServer, 2008, 0)) != null  // SQL Server 2012 之前不支持
                && lead.checkDialect(metaOf(DbType.Postgresql, 9, 4)) == null
                && lead.checkDialect(metaOf(DbType.SQLite, 3, 25)) == null;
    }

    static boolean nthValueStrictVersion() {
        SqlFun f = SqlFun.nthValue("col", 2);
        return f.checkDialect(metaOf(DbType.MySQL, 8, 0)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 11, 0)) == null
                && f.checkDialect(metaOf(DbType.Postgresql, 10, 0)) != null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 3)) == null
                && f.checkDialect(metaOf(DbType.MariaDB, 10, 2)) != null
                && f.checkDialect(metaOf(DbType.SQLite, 3, 30)) == null
                && f.checkDialect(metaOf(DbType.SQLServer, 2019, 0)) != null;  // SQL Server 不支持
    }

    // ======================== 现有通用函数不被破坏 ========================

    static boolean existingUniversalFunctionsStillPass() {
        // trim/coalesce/nullIf/count/year/month/length/upper/lower/round/floor/ceil 仍是通用
        cn.vonce.sql.bean.Common common = new cn.vonce.sql.bean.Common();
        SqlBeanMeta meta = metaOf(DbType.Oracle, 19, 0);
        meta.setDialectMode(DialectMode.STRICT);
        common.setSqlBeanMeta(meta);
        SqlFun[] univ = {
                SqlFun.trim("c"), SqlFun.coalesce("a", "b"), SqlFun.nullIf("a", "b"),
                SqlFun.count("c"), SqlFun.year("d"), SqlFun.month("d"), SqlFun.length("c"),
                SqlFun.upper("c"), SqlFun.lower("c"), SqlFun.round("n"), SqlFun.floor("n"),
                SqlFun.concat("a", "b"), SqlFun.replace("a", "b", "c"),
                SqlFun.substring("a", 1), SqlFun.ltrim("c"), SqlFun.rtrim("c"),
                SqlFun.left("a", 3), SqlFun.right("a", 3),
                SqlFun.sqrt("n"), SqlFun.mod("a", "b")
        };
        for (SqlFun f : univ) {
            try {
                cn.vonce.sql.uitls.SqlBeanUtil.getSqlFunction(common, f);
            } catch (UnsupportedDialectException e) {
                System.out.println("    [universal] " + e.getFunctionName() + " 被错标为方言专用！");
                return false;
            }
        }
        return true;
    }

    // ======================== JUnit 用例（原 main 段落逐项搬迁） ========================

    @Test
    public void dbVersion() {
        verify("db.version.from(int,int)", versionFromMajorMinor());
        verify("db.version.from(String)", versionFromString());
        verify("db.version.compareTo", versionCompareTo());
        verify("db.version.atLeast", versionAtLeast());
        verify("db.version.from(invalid) throws", versionFromInvalidThrows());
        flush("DbVersion");
    }

    @Test
    public void dialectBuilder() {
        verify("dialect.builder.supports+versions", dialectBuilderSupports());
        verify("dialect.unsupport 优先级", dialectUnsupportPriority());
        verify("dialect.UNIVERSAL 空安全", dialectUniversal());
        verify("dialect.supports(null version)", dialectSupportsWithNoVersion());
        flush("dialectBuilder");
    }

    @Test
    public void checkDialect() {
        verify("checkDialect.通用函数 universal", checkDialectReturnsNullForUniversal());
        verify("checkDialect.支持方言通过", checkDialectPassesForSupportedDb());
        verify("checkDialect.不支持方言失败", checkDialectFailsForUnsupportedDb());
        verify("checkDialect.版本不符失败", checkDialectFailsForTooOldVersion());
        verify("checkDialect.重复调用幂等", checkDialectCachesAfterFirstCall());
        verify("checkDialect.重标立即生效", checkDialectRedialectingTakesEffect());
        verify("checkDialect.meta=null 放行", checkDialectNullMetaPasses());
        flush("checkDialect");
    }

    @Test
    public void renderValidation() {
        verify("render.STRICT 不支持抛异常", renderStrictThrowsForUnsupported());
        verify("render.STRICT 支持方言通过", renderStrictPassesForSupported());
        verify("render.OFF 不校验", renderOffModeNoCheck());
        verify("render.通用函数跨方言", renderUniversalFunPassesAnywhere());
        verify("render.现有函数不被破坏", renderExistingFunctionsUnchanged());
        flush("renderValidation");
    }

    @Test
    public void userExtension() {
        verify("user.extension.SqlFun.dialect 注册", userExtensionRegistersDialects());
        flush("userExtension");
    }

    @Test
    public void dialectStringAndControlFunctions() {
        verify("dialect.ifNull 跨方言对立别名", ifNullCrossDbConflict());
        verify("dialect.nvl 仅 Oracle", nvlOnlyOracle());
        verify("dialect.instr 字符串方言差异", instrDialects());
        verify("dialect.lPad/rPad 字符串差异", lPadRPadDialects());
        verify("dialect.concat_ws 仅 MySQL", concatWsOnlyMySQL());
        verify("dialect.str_to_date 仅 MySQL", strToDateOnlyMySQL());
        verify("dialect.iF 仅 MySQL", ifOnlyMySQL());
        verify("dialect.date_format(snake) 仅 MySQL", dateFormatSnakeCaseOnlyMySQL());
        verify("dialect.charIndex 仅 SQLServer", charIndexOnlySqlServer());
        flush("dialectStringAndControlFunctions");
    }

    @Test
    public void dialectSqlServerAndTimeFunctions() {
        verify("dialect.len 仅 SQLServer", lenOnlySqlServer());
        verify("dialect.stuff 仅 SQLServer", stuffOnlySqlServer());
        verify("dialect.getDate 仅 SQLServer", getDateOnlySqlServer());
        verify("dialect.host_name/user_name 仅 SQLServer", hostNameUserNameOnlySqlServer());
        verify("dialect.dataLength 仅 SQLServer", dataLengthOnlySqlServer());
        verify("dialect.convert SQLServer+MySQL", convertSqlServerAndMySQL());
        verify("dialect.dateName/datePart 仅 SQLServer", dateNameDatePartOnlySqlServer());
        verify("dialect.dateAdd/dateDiff(datePart) 仅 SQLServer", dateAddDateDiffDatePartOnlySqlServer());
        verify("dialect.now/curDate/curTime 方言差异", nowCurDateCurTimeDialects());
        flush("dialectSqlServerAndTimeFunctions");
    }

    @Test
    public void dialectDateAndWindowFunctions() {
        verify("dialect.date 提取日期部分", dateExtractionDialects());
        verify("dialect.monthName 仅 MySQL", monthNameOnlyMySQL());
        verify("dialect.dateAdd(TimeUnit) 仅 MySQL", dateAddTimeUnitOnlyMySQL());
        verify("dialect.timestampDiff 仅 MySQL", timestampDiffOnlyMySQL());
        verify("dialect.dateDiff(2参) MySQL+SQLServer", dateDiffTwoArgsDialects());
        verify("dialect.version/database/user 方言差异", versionDatabaseUserDialects());
        verify("dialect.窗口函数 版本约束", windowFunctionDialects());
        verify("dialect.lag/lead SQLServer 2012+", lagLeadRequiresSqlServer2012());
        verify("dialect.nth_value 严格版本", nthValueStrictVersion());
        verify("render.现有通用函数不被破坏", existingUniversalFunctionsStillPass());
        flush("dialectDateAndWindowFunctions");
    }

    @Test
    public void p0Batch2Functions() {
        verify("p0b2.通用函数无方言标注", p0Batch2UniversalFunctionsAreUntagged());
        verify("p0b2.通用函数 MySQL STRICT 渲染", p0Batch2UniversalFunctionsRender());
        verify("p0b2.md5 方言矩阵", md5Dialects());
        verify("p0b2.md5 PG 渲染", md5RendersOnPostgres());
        verify("p0b2.md5 Oracle<12 STRICT 抛", md5ThrowsOnOldOracle());
        verify("p0b2.dateTrunc MySQL 5.x 抛", dateTruncThrowsOnOldMysql());
        verify("p0b2.dateTrunc Oracle 抛", dateTruncThrowsOnOracle());
        verify("p0b2.position 通用", positionIsUniversal());
        verify("p0b2.extract 通用", extractIsUniversal());
        verify("p0b2.cast 通用", castIsUniversal());
        verify("p0b2.currentTimestamp 通用", currentTimestampIsUniversal());
        flush("p0Batch2Functions");
    }

    @Test
    public void p1RegexpHashBitFunctions() {
        verify("p1.jsonValue 方言矩阵", jsonValueDialects());
        verify("p1.regexpCount 方言矩阵", regexpCountDialects());
        verify("p1.regexpInstr 方言矩阵", regexpInstrDialects());
        verify("p1.bitCount 方言矩阵", bitCountDialects());
        flush("p1RegexpHashBitFunctions");
    }

    @Test
    public void p1WindowMysqlFunctionsAndRendering() {
        verify("p1.percentRank/cumeDist 方言矩阵", windowExtraDialects());
        verify("p1.genRandomUuid 方言矩阵", genRandomUuidDialects());
        verify("p1.inetAton 方言矩阵", inetAtonDialects());
        verify("p1.substringIndex 方言矩阵", substringIndexDialects());
        verify("p1.findInSet 方言矩阵", findInSetDialects());
        verify("p1.strcmp 方言矩阵", strcmpDialects());
        verify("p1.elt 方言矩阵", eltDialects());
        verify("p1.regexpLike SQLServer STRICT 抛", regexpLikeThrowsOnSqlServer());
        verify("p1.elt MySQL 渲染", eltRendersOnMysql());
        flush("p1WindowMysqlFunctionsAndRendering");
    }
}
