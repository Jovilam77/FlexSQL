package cn.vonce.sql.cache.it;

import cn.vonce.sql.annotation.SqlColumn;
import cn.vonce.sql.annotation.SqlId;
import cn.vonce.sql.annotation.SqlTable;

/**
 * 集成测试用的极简实体：仅含 id + name 两个字段，足以让 SqlBeanProvider 生成 SELECT 语句（缓存键构建所需）。
 */
@SqlTable("user")
public class TestUser {

    @SqlId
    private Integer id;

    @SqlColumn("name")
    private String name;

    public TestUser() {
    }

    public TestUser(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
