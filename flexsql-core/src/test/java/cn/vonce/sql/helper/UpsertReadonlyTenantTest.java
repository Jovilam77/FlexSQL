package cn.vonce.sql.helper;

import cn.vonce.sql.bean.Upsert;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.model.AuditBean;
import cn.vonce.sql.model.TenantBean;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

/**
 * UPSERT 冲突更新分支「只读列 / 租户列」过滤回归测试（P1-6）。
 *
 * <p><b>背景</b>：{@code setAll()} 会把实体所有字段加进冲突更新分支，生成为
 * {@code create_by = VALUES(create_by)} / {@code create_time = VALUES(create_time)}，
 * 于是每次 upsert 已存在的行都会把创建人、创建时间覆盖成「本次待插入值」，
 * 还可能把租户列写脏。常规 UPDATE（{@code setSql}）本来就会过滤这些列，
 * UPSERT 的更新分支漏了同一口径。</p>
 *
 * <p>修复后 {@code buildUpsertAssignments} 会跳过 {@code @SqlDefaultValue(readonly = true)}
 * 与 {@code @SqlTenantId} 列，本测试锁死该行为。</p>
 *
 * @author Jovi
 */
public class UpsertReadonlyTenantTest {

    private static SqlBeanMeta meta(DbType dbType) {
        SqlBeanMeta m = new SqlBeanMeta();
        m.setDbType(dbType);
        SqlBeanConfig cfg = new SqlBeanConfig();
        cfg.setToUpperCase(false);
        m.setSqlBeanConfig(cfg);
        return m;
    }

    /** 去掉标识符转义（反引号/双引号）与空白，便于断言赋值片段。 */
    private static String flat(String sql) {
        return sql.replace("`", "").replace("\"", "").replaceAll("\\s+", "");
    }

    /** 取「冲突更新分支」片段（不同方言的标记不同）。 */
    private static String updateBranch(String flatSql) {
        int i = flatSql.indexOf("ONDUPLICATEKEYUPDATE");
        if (i >= 0) {
            return flatSql.substring(i);
        }
        i = flatSql.indexOf("DOUPDATESET");
        if (i >= 0) {
            return flatSql.substring(i);
        }
        i = flatSql.indexOf("WHENMATCHEDTHENUPDATE");
        if (i >= 0) {
            return flatSql.substring(i);
        }
        return flatSql;
    }

    @Test
    public void readonlyAuditColumnsAreNotOverwritten() {
        for (DbType dbType : new DbType[]{DbType.MySQL, DbType.Postgresql, DbType.SQLite}) {
            AuditBean bean = new AuditBean();
            bean.setId(1L);
            bean.setName("n");
            // 业务层误设：只读列不应参与冲突更新
            bean.setCreateBy("hacker");
            bean.setCreateTime(new Date(0));

            Upsert<AuditBean> upsert = new Upsert<>();
            upsert.setSqlBeanMeta(meta(dbType));
            upsert.setBeanClass(AuditBean.class);
            upsert.setBean(bean);
            upsert.onConflict(AuditBean::getId).setAll();

            String sql = SqlHelper.buildUpsertSql(upsert);
            System.out.println("---upsert " + dbType + " setAll---");
            System.out.println(sql);
            Assert.assertNotNull(dbType + " 应生成 UPSERT SQL", sql);

            String branch = updateBranch(flat(sql));
            Assert.assertTrue(dbType + " 普通列 name 应仍在更新分支: " + sql, branch.contains("name="));
            Assert.assertFalse(dbType + " 只读列 create_by 不应被覆盖: " + sql, branch.contains("create_by"));
            Assert.assertFalse(dbType + " 只读列 create_time 不应被覆盖: " + sql, branch.contains("create_time"));
        }
    }

    @Test
    public void tenantColumnIsNotOverwritten() {
        TenantBean bean = new TenantBean();
        bean.setId(2L);
        bean.setName("n");
        bean.setTenantId("t-1");

        Upsert<TenantBean> upsert = new Upsert<>();
        upsert.setSqlBeanMeta(meta(DbType.MySQL));
        upsert.setBeanClass(TenantBean.class);
        upsert.setBean(bean);
        upsert.onConflict(TenantBean::getId).setAll();

        String sql = SqlHelper.buildUpsertSql(upsert);
        System.out.println("---upsert MySQL tenant setAll---");
        System.out.println(sql);
        Assert.assertNotNull(sql);

        String branch = updateBranch(flat(sql));
        Assert.assertTrue("普通列 name 应仍在更新分支: " + sql, branch.contains("name="));
        Assert.assertFalse("租户列 tenant_id 不应被 upsert 更新分支覆盖: " + sql, branch.contains("tenant_id"));
    }
}
