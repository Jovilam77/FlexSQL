package cn.vonce.sql.helper;

import cn.vonce.sql.bean.Backup;
import cn.vonce.sql.bean.Copy;
import cn.vonce.sql.bean.Select;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.dialect.SqliteDialect;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.enumerate.JoinType;
import cn.vonce.sql.model.TenantBean;
import cn.vonce.sql.provider.TenantContextHolder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * 9/5–9/12 code review P2 项回归测试（SQL 构建侧）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li><b>P2-1</b> {@code count(true)} + union 丢分支 —— 旧实现 {@code if (!select.isCount() && union...)}
 *       会整段跳过 UNION 分支，导致分页总数只统计主查询。修复后内层渲染原始列、
 *       最外层包一层 {@code COUNT(*)}（形如 {@code SELECT COUNT(*) FROM (a UNION b) T}）。</li>
 *   <li><b>P2-2</b> RIGHT / FULL JOIN 的租户过滤放在 ON —— 关联表是「行保留侧」时，
 *       不匹配的关联表行仍会被保留（另一端补 NULL），其它租户的行会因此泄漏到结果里，
 *       必须并入外层 WHERE 才能真正过滤；INNER / LEFT JOIN 保持放 ON（否则会退化成内连接语义）。</li>
 *   <li><b>P2-3</b> SQLite 行锁静默失效 —— SQLite 无 {@code FOR UPDATE} 语法，
 *       必须告警而不是静默丢弃。</li>
 *   <li><b>P2-7</b> Backup / Copy 不参与租户过滤 —— 两者都会把源表数据搬走，
 *       缺失租户条件会跨租户搬运数据。</li>
 * </ul>
 *
 * @author Jovi
 */
public class P2SqlBuildTest {

    private static final String TENANT = "t-1";

    @Before
    public void setUp() {
        TenantContextHolder.setTenantId(TENANT);
    }

    @After
    public void tearDown() {
        TenantContextHolder.clearTenantId();
    }

    private static SqlBeanMeta meta(DbType dbType) {
        SqlBeanMeta m = new SqlBeanMeta();
        m.setDbType(dbType);
        SqlBeanConfig cfg = new SqlBeanConfig();
        cfg.setToUpperCase(false);
        m.setSqlBeanConfig(cfg);
        return m;
    }

    private static Select select(String table) {
        Select s = new Select();
        s.setSqlBeanMeta(meta(DbType.MySQL));
        s.table(table);
        s.column("id");
        return s;
    }

    /** 去掉标识符转义与多余空白，保留单个空格便于定位 ON / WHERE 位置。 */
    private static String compact(String sql) {
        return sql.replace("`", "").replace("\"", "").replaceAll("\\s+", " ").trim();
    }

    /** 去掉全部空白，便于断言连续片段。 */
    private static String flat(String sql) {
        return sql.replace("`", "").replace("\"", "").replaceAll("\\s+", "");
    }

    // ==================== P2-1 count + union ====================

    @Test
    public void countOverUnionWrapsOuterCountAndKeepsAllBranches() {
        Select main = select("t_a");
        main.union(select("t_b"));
        main.count(true);

        String sql = SqlHelper.buildSelectSql(main);
        System.out.println("---count(true) over union---");
        System.out.println(sql);

        String f = flat(sql);
        // 旧实现：SELECT COUNT(*) FROM t_a（分支被整段丢弃）
        Assert.assertTrue("count+union 应为「外层 COUNT(*) 包裹整个 UNION」: " + sql,
                f.startsWith("SELECTCOUNT(*)FROM(SELECT"));
        Assert.assertTrue("两个分支都必须保留: " + sql, f.contains("t_a") && f.contains("t_b"));
        Assert.assertTrue("必须保留 UNION: " + sql, f.contains("UNION"));
        Assert.assertTrue("外层包裹需有别名 T: " + sql, f.endsWith(")AST"));
        // 内层渲染原始列 → COUNT(*) 只应出现一次（旧实现会退化成两个 COUNT(*) 的 UNION → 返回多行）
        Assert.assertEquals("COUNT(*) 只应出现一次（内层应为原始列）: " + sql,
                f.indexOf("COUNT(*)"), f.lastIndexOf("COUNT(*)"));
    }

    @Test
    public void plainCountIsUnchanged() {
        // 不变量：无 union 的 count 仍保持原形 SELECT COUNT(*) FROM t_a（不额外套一层）
        Select main = select("t_a");
        main.count(true);
        String f = flat(SqlHelper.buildSelectSql(main));
        Assert.assertTrue("应以 SELECT COUNT(*) FROM t_a 开头: " + f, f.startsWith("SELECTCOUNT(*)FROMt_a"));
        Assert.assertFalse("无 union 的 count 不应出现外层包裹: " + f, f.contains(")AST"));
    }

    // ==================== P2-2 RIGHT / FULL JOIN 租户过滤 ====================

    /** 构造一个 main(t_order) + JOIN(t_tenant) 查询；用 on 字符串形式避免依赖条件构建器。 */
    private static Select joined(JoinType joinType) {
        Select main = select("t_order");
        main.join(joinType, "t_tenant", "t_tenant.id = t_order.id");
        main.getJoin().get(0).setJoinClass(TenantBean.class);
        return main;
    }

    @Test
    public void rightJoinTenantConditionGoesToWhere() {
        String sql = compact(SqlHelper.buildSelectSql(joined(JoinType.RIGHT_JOIN)));
        System.out.println("---right join + tenant---");
        System.out.println(sql);

        int onIdx = sql.indexOf(" ON ");
        int whereIdx = sql.indexOf(" WHERE ");
        Assert.assertTrue("应生成 ON: " + sql, onIdx >= 0);
        Assert.assertTrue("RIGHT JOIN 的租户条件必须移到外层 WHERE（放 ON 会泄漏其它租户行）: " + sql,
                whereIdx > onIdx);
        Assert.assertTrue("租户条件应位于 WHERE 之后: " + sql, sql.indexOf("tenant_id") > whereIdx);
    }

    @Test
    public void fullJoinTenantConditionGoesToWhere() {
        String sql = compact(SqlHelper.buildSelectSql(joined(JoinType.FULL_JOIN)));
        System.out.println("---full join + tenant---");
        System.out.println(sql);

        int whereIdx = sql.indexOf(" WHERE ");
        Assert.assertTrue("FULL JOIN 两侧都是行保留侧，租户条件必须移到 WHERE: " + sql, whereIdx >= 0);
        Assert.assertTrue("租户条件应位于 WHERE 之后: " + sql, sql.indexOf("tenant_id") > whereIdx);
    }

    @Test
    public void leftJoinTenantConditionStaysInOn() {
        String sql = compact(SqlHelper.buildSelectSql(joined(JoinType.LEFT_JOIN)));
        System.out.println("---left join + tenant---");
        System.out.println(sql);

        int onIdx = sql.indexOf(" ON ");
        Assert.assertTrue("应生成 ON: " + sql, onIdx >= 0);
        Assert.assertFalse("LEFT JOIN 的关联表是非保留侧，租户条件留在 ON 即可（放 WHERE 会退化成内连接）: " + sql,
                sql.contains(" WHERE "));
        Assert.assertTrue("租户条件应位于 ON 之后: " + sql, sql.indexOf("tenant_id") > onIdx);
    }

    @Test
    public void innerJoinTenantConditionStaysInOn() {
        String sql = compact(SqlHelper.buildSelectSql(joined(JoinType.INNER_JOIN)));
        Assert.assertTrue("INNER JOIN 的租户条件应留在 ON: " + sql, sql.contains(" ON "));
        Assert.assertFalse("INNER JOIN 无需额外 WHERE: " + sql, sql.contains(" WHERE "));
    }

    // ==================== P2-3 SQLite 行锁 ====================

    @Test
    public void sqliteForUpdateIsIgnoredWithWarning() {
        Logger logger = Logger.getLogger(SqliteDialect.class.getName());
        CapturingHandler handler = new CapturingHandler();
        Level oldLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
        try {
            Select s = new Select();
            s.setSqlBeanMeta(meta(DbType.SQLite));
            s.table("t_a");
            s.column("id");
            s.forUpdate();

            String sql = SqlHelper.buildSelectSql(s);
            System.out.println("---sqlite forUpdate---");
            System.out.println(sql);

            Assert.assertFalse("SQLite 不支持 FOR UPDATE，不应生成锁子句: " + sql,
                    sql.toUpperCase().contains("FOR UPDATE"));
            Assert.assertTrue("SQLite 行锁被忽略时必须告警（旧实现静默失效）: " + handler.messages,
                    containsSqliteWarning(handler.messages));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(oldLevel);
        }
    }

    private static boolean containsSqliteWarning(List<String> messages) {
        for (String m : messages) {
            if (m != null && m.contains("SQLite") && m.contains("行锁")) {
                return true;
            }
        }
        return false;
    }

    private static final class CapturingHandler extends Handler {
        final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    // ==================== P2-7 Backup / Copy 租户过滤 ====================

    @Test
    public void backupAppliesTenantFilter() {
        Backup backup = new Backup();
        backup.setSqlBeanMeta(meta(DbType.MySQL));
        backup.setTable(TenantBean.class);
        backup.setTargetTableName("t_tenant_bak");

        String sql = SqlHelper.buildBackup(backup);
        System.out.println("---backup + tenant---");
        System.out.println(sql);

        Assert.assertTrue("Backup 必须注入租户条件（否则会跨租户备份数据）: " + sql,
                compact(sql).contains(" WHERE "));
        Assert.assertTrue("租户列应出现在条件中: " + sql, flat(sql).contains("tenant_id='" + TENANT + "'"));
    }

    @Test
    public void copyAppliesTenantFilter() {
        Copy copy = new Copy();
        copy.setSqlBeanMeta(meta(DbType.MySQL));
        copy.setTable(TenantBean.class);
        copy.setTargetTableName("t_tenant_bak");

        String sql = SqlHelper.buildCopy(copy);
        System.out.println("---copy + tenant---");
        System.out.println(sql);

        Assert.assertTrue("Copy 必须注入租户条件（否则会跨租户搬运数据）: " + sql,
                compact(sql).contains(" WHERE "));
        Assert.assertTrue("租户列应出现在条件中: " + sql, flat(sql).contains("tenant_id='" + TENANT + "'"));
    }

    @Test
    public void backupWithoutTenantContextIsUnchanged() {
        TenantContextHolder.clearTenantId();
        Backup backup = new Backup();
        backup.setSqlBeanMeta(meta(DbType.MySQL));
        backup.setTable(TenantBean.class);
        backup.setTargetTableName("t_tenant_bak");

        String sql = SqlHelper.buildBackup(backup);
        Assert.assertFalse("无租户上下文时不应注入条件: " + sql, compact(sql).contains(" WHERE "));
        Assert.assertFalse("无租户上下文时不应出现 tenant_id: " + sql, compact(sql).contains("tenant_id"));
    }
}
