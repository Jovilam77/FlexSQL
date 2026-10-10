package cn.vonce.sql.helper;

import cn.vonce.sql.bean.Select;
import cn.vonce.sql.bean.Update;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.helper.Cond;
import cn.vonce.sql.helper.Wrapper;
import cn.vonce.sql.model.LogicallyBean;
import cn.vonce.sql.provider.TenantContextHolder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 回归测试：传字符串 where 时，框架自动注入的逻辑删除 / 乐观锁条件被整体跳过（刻意保留的「口子」）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li><b>Select 逻辑删除</b>：{@code where(String)} 跳过 {@code (deleted = 0)} 自动过滤，
 *       便于主动查询已被逻辑删除的数据；而 {@code where(Condition)} / {@code where(Wrapper)}
 *       仍保留自动过滤。</li>
 *   <li><b>Update 乐观锁</b>：{@code where(String)} 跳过乐观锁条件；{@code where(Condition)} 仍保留。</li>
 * </ul>
 *
 * <p>租户隔离过滤始终生效，与本「口子」无关，不在本用例验证范围内。</p>
 *
 * @author Jovi
 */
public class WhereStringBackdoorTest {

    @Before
    public void setUp() {
        // 隔离租户上下文，避免干扰逻辑删除 / 乐观锁断言
        TenantContextHolder.clearTenantId();
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

    private static Select select() {
        Select s = new Select();
        s.setSqlBeanMeta(meta(DbType.MySQL));
        s.table(LogicallyBean.class);
        s.column("id");
        return s;
    }

    /** 去除标识符转义与空白，便于断言连续片段。 */
    private static String flat(String sql) {
        return sql.replace("`", "").replace("\"", "").replaceAll("\\s+", "");
    }

    // ==================== Select 逻辑删除过滤 ====================

    @Test
    public void rawStringWhereSkipsLogicDeleteFilter() {
        Select s = select();
        s.where("1 = 1");
        String f = flat(SqlHelper.buildSelectSql(s));
        System.out.println("--- Select raw string where (backdoor) ---");
        System.out.println(f);
        // 口子：传字符串 where 时不注入逻辑删除过滤，可借此查询已删除数据
        Assert.assertFalse("传字符串 where 时不应注入 (deleted = 0)，这是查询已删除数据的口子: " + f,
                f.contains("deleted=0"));
    }

    @Test
    public void conditionWhereKeepsLogicDeleteFilter() {
        Select s = select();
        s.where().eq("name", "x");
        String f = flat(SqlHelper.buildSelectSql(s));
        System.out.println("--- Select where(Condition) (auto filter) ---");
        System.out.println(f);
        Assert.assertTrue("where(Condition) 仍必须注入 (deleted = 0): " + f, f.contains("deleted=0"));
    }

    @Test
    public void wrapperWhereKeepsLogicDeleteFilter() {
        Select s = select();
        s.where(Wrapper.where(Cond.eq("name", "x")));
        String f = flat(SqlHelper.buildSelectSql(s));
        System.out.println("--- Select where(Wrapper) (auto filter) ---");
        System.out.println(f);
        Assert.assertTrue("where(Wrapper) 仍必须注入 (deleted = 0): " + f, f.contains("deleted=0"));
    }

    // ==================== Update 乐观锁 ====================

    private static Update<LogicallyBean> baseUpdate() {
        Update<LogicallyBean> u = new Update<>();
        u.setSqlBeanMeta(meta(DbType.MySQL));
        u.setTable(LogicallyBean.class);
        // 乐观锁条件需要实体实例才能读取 version 字段，故注入一个 bean（version 留空 → 渲染为 IS NULL）
        u.bean(new LogicallyBean());
        u.optimisticLock(true);
        u.set("name", "y");
        return u;
    }

    /** 仅取 WHERE 之后的片段做断言，避免与 SET 子句中自动递增的 version 列混淆。 */
    private static String wherePart(String flatSql) {
        int idx = flatSql.indexOf("WHERE");
        return idx >= 0 ? flatSql.substring(idx + "WHERE".length()) : flatSql;
    }

    @Test
    public void rawStringWhereSkipsOptimisticLock() {
        Update<LogicallyBean> u = baseUpdate();
        u.where("id = 1");
        String f = flat(SqlHelper.buildUpdateSql(u));
        System.out.println("--- Update raw string where (backdoor) ---");
        System.out.println(f);
        // 口子：传字符串 where 时，WHERE 中不注入乐观锁条件（version 列不应出现在 WHERE 中）
        Assert.assertFalse("传字符串 where 时 WHERE 不应注入乐观锁条件，这是口子: " + f,
                wherePart(f).contains("version"));
    }

    @Test
    public void conditionWhereKeepsOptimisticLock() {
        Update<LogicallyBean> u = baseUpdate();
        u.where().eq("id", 1L);
        String f = flat(SqlHelper.buildUpdateSql(u));
        System.out.println("--- Update where(Condition) (auto lock) ---");
        System.out.println(f);
        Assert.assertTrue("where(Condition) 仍必须在 WHERE 中注入乐观锁条件: " + f,
                wherePart(f).contains("version"));
    }
}
