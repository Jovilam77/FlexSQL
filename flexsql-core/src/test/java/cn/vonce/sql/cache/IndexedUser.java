package cn.vonce.sql.cache;

import cn.vonce.sql.annotation.SqlColumn;
import cn.vonce.sql.annotation.SqlId;
import cn.vonce.sql.annotation.SqlTable;

/**
 * 测试用 Bean（含 {@link SqlId} 主键）。
 * 内嵌于 IndexedQueryCacheTest 同包以便单元测试直接使用，
 * 不属于公开 API。
 */
@SqlTable("t_user_indexed")
class IndexedUser {
    @SqlId
    private Long id;
    @SqlColumn("name")
    private String name;

    public IndexedUser() {}
    public IndexedUser(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

/** 测试用：不带 @SqlId 的 bean（应跳过索引登记）。 */
class NonIdBean {
    private Long id;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}
