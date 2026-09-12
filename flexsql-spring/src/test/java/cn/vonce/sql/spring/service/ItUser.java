package cn.vonce.sql.spring.service;

import cn.vonce.sql.annotation.SqlColumn;
import cn.vonce.sql.annotation.SqlId;
import cn.vonce.sql.annotation.SqlTable;
import cn.vonce.sql.enumerate.IdType;

/**
 * 「插入并回填自增id」集成测试用的实体。
 * <p>注意：必须是顶层类——框架的 {@code SqlConstantProcessor} 只处理顶层类，嵌套类会导致编译期异常。</p>
 */
@SqlTable("it_user")
public class ItUser {

    @SqlId(type = IdType.AUTO)
    @SqlColumn("id")
    private Long id;

    @SqlColumn("name")
    private String name;

    @SqlColumn("age")
    private Integer age;

    public ItUser() {
    }

    public ItUser(String name, Integer age) {
        this.name = name;
        this.age = age;
    }

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

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "ItUser{id=" + id + ", name='" + name + "', age=" + age + '}';
    }
}
