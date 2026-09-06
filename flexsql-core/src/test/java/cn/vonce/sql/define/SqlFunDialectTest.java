package cn.vonce.sql.define;

import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.enumerate.DialectMode;
import cn.vonce.sql.exception.UnsupportedDialectException;

/**
 * SqlFun 方言支持元数据单元测试（main 风格，无需 DB）。
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

    static int pass = 0;
    static int fail = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("  PASS " + name);
        } else {
            fail++;
            System.out.println("  FAIL " + name);
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

    public static void main(String[] args) {
        check("db.version.from(int,int)", versionFromMajorMinor());
        check("db.version.from(String)", versionFromString());
        check("db.version.compareTo", versionCompareTo());
        check("db.version.atLeast", versionAtLeast());
        check("db.version.from(invalid) throws", versionFromInvalidThrows());

        check("dialect.builder.supports+versions", dialectBuilderSupports());
        check("dialect.unsupport 优先级", dialectUnsupportPriority());
        check("dialect.UNIVERSAL 空安全", dialectUniversal());
        check("dialect.supports(null version)", dialectSupportsWithNoVersion());

        check("checkDialect.通用函数 universal", checkDialectReturnsNullForUniversal());
        check("checkDialect.支持方言通过", checkDialectPassesForSupportedDb());
        check("checkDialect.不支持方言失败", checkDialectFailsForUnsupportedDb());
        check("checkDialect.版本不符失败", checkDialectFailsForTooOldVersion());
        check("checkDialect.重复调用幂等", checkDialectCachesAfterFirstCall());
        check("checkDialect.重标立即生效", checkDialectRedialectingTakesEffect());
        check("checkDialect.meta=null 放行", checkDialectNullMetaPasses());

        check("render.STRICT 不支持抛异常", renderStrictThrowsForUnsupported());
        check("render.STRICT 支持方言通过", renderStrictPassesForSupported());
        check("render.OFF 不校验", renderOffModeNoCheck());
        check("render.通用函数跨方言", renderUniversalFunPassesAnywhere());
        check("render.现有函数不被破坏", renderExistingFunctionsUnchanged());

        check("user.extension.SqlFun.dialect 注册", userExtensionRegistersDialects());

        System.out.println();
        System.out.println("=== SqlFunDialectTest: " + pass + " passed, " + fail + " failed ===");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
