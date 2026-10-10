package cn.vonce.sql.model;

import cn.vonce.sql.annotation.SqlColumn;
import cn.vonce.sql.annotation.SqlId;
import cn.vonce.sql.annotation.SqlLogically;
import cn.vonce.sql.annotation.SqlTable;
import cn.vonce.sql.annotation.SqlVersion;
import cn.vonce.sql.enumerate.IdType;

/**
 * 逻辑删除 / 乐观锁「口子」回归测试实体。
 *
 * <p>声明 {@link SqlLogically} 后，普通查询会自动追加 {@code (deleted = 0)} 过滤；
 * 当用户显式传入原生字符串 where 时，该过滤被刻意跳过，便于主动查询已被逻辑删除的数据。
 * 同时声明 {@link SqlVersion} 以验证 Update 乐观锁在字符串 where 下的绕过行为。</p>
 */
@SqlTable("t_logically")
public class LogicallyBean {

    @SqlId(type = IdType.AUTO)
    @SqlColumn("id")
    private Long id;

    @SqlColumn("name")
    private String name;

    @SqlLogically
    @SqlColumn("deleted")
    private Integer deleted;

    @SqlVersion
    @SqlColumn("version")
    private Integer version;

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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
