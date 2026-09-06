package cn.vonce.sql.model;

import cn.vonce.sql.annotation.SqlColumn;
import cn.vonce.sql.annotation.SqlId;
import cn.vonce.sql.annotation.SqlTable;
import cn.vonce.sql.annotation.SqlTenantId;
import cn.vonce.sql.enumerate.IdType;

/**
 * 行级多租户隔离测试实体（共享表 + tenant_id 列）
 */
@SqlTable("t_tenant")
public class TenantBean {

    @SqlId(type = IdType.AUTO)
    @SqlColumn("id")
    private Long id;

    @SqlColumn("name")
    private String name;

    /** 租户ID列：INSERT 自动写入当前租户，SELECT/UPDATE/DELETE 自动追加过滤 */
    @SqlTenantId
    @SqlColumn("tenant_id")
    private String tenantId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
