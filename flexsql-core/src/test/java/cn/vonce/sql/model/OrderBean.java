package cn.vonce.sql.model;

import cn.vonce.sql.annotation.SqlColumn;
import cn.vonce.sql.annotation.SqlId;
import cn.vonce.sql.annotation.SqlJoin;
import cn.vonce.sql.annotation.SqlTable;
import cn.vonce.sql.annotation.SqlTenantId;
import cn.vonce.sql.enumerate.IdType;
import cn.vonce.sql.enumerate.JoinType;

/**
 * JOIN / 子查询 多租户与动态Schema 隔离测试主表实体
 */
@SqlTable(value = "t_order", alias = "o")
public class OrderBean {

    @SqlId(type = IdType.AUTO)
    @SqlColumn("id")
    private Long id;

    @SqlColumn("order_no")
    private String orderNo;

    /** 租户ID列：主表 SELECT/UPDATE/DELETE 自动追加 WHERE 过滤 */
    @SqlTenantId
    @SqlColumn("tenant_id")
    private String tenantId;

    /** 关联租户表（bean 关联，@SqlTenantId 实体），用于验证关联表 ON 子句租户过滤 + 动态 schema 覆盖 */
    @SqlJoin(type = JoinType.INNER_JOIN, tableAlias = "jt", mainKeyword = "tenant_id")
    private TenantBean tenant;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public TenantBean getTenant() {
        return tenant;
    }

    public void setTenant(TenantBean tenant) {
        this.tenant = tenant;
    }
}
