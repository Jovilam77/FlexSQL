package cn.vonce.sql.model;

import cn.vonce.sql.annotation.SqlColumn;
import cn.vonce.sql.annotation.SqlDefaultValue;
import cn.vonce.sql.annotation.SqlId;
import cn.vonce.sql.annotation.SqlTable;
import cn.vonce.sql.enumerate.FillWith;
import cn.vonce.sql.enumerate.IdType;

import java.util.Date;

/**
 * 审计 / 多租户自动注入测试实体
 */
@SqlTable("t_audit")
public class AuditBean {

    @SqlId(type = IdType.AUTO)
    @SqlColumn("id")
    private Long id;

    @SqlColumn("name")
    private String name;

    /** 创建人：新增时注入当前操作人，且只读（更新时不修改） */
    @SqlDefaultValue(with = FillWith.INSERT, user = true, readonly = true)
    @SqlColumn("create_by")
    private String createBy;

    /** 创建时间：新增时填充，且只读（更新时不修改） */
    @SqlDefaultValue(with = FillWith.INSERT, readonly = true)
    @SqlColumn("create_time")
    private Date createTime;

    /** 更新人：每次更新注入当前操作人 */
    @SqlDefaultValue(with = FillWith.UPDATE_EVERYTIME, user = true)
    @SqlColumn("update_by")
    private String updateBy;

    /** 更新时间：每次更新刷新 */
    @SqlDefaultValue(with = FillWith.UPDATE_EVERYTIME)
    @SqlColumn("update_time")
    private Date updateTime;

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

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
