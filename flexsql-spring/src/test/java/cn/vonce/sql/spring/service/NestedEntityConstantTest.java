package cn.vonce.sql.spring.service;

import cn.vonce.sql.annotation.SqlColumn;
import cn.vonce.sql.annotation.SqlId;
import cn.vonce.sql.annotation.SqlTable;
import cn.vonce.sql.enumerate.IdType;
import cn.vonce.sql.uitls.SqlBeanUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * 回归测试：注解处理器 {@code SqlConstantProcessor} 对嵌套实体类的支持。
 *
 * <p><b>历史缺陷</b>：处理器直接对 {@code element.getEnclosingElement()} 强转 {@code PackageElement}，
 * 嵌套类的 enclosing 是外层类而非包，强转抛 {@code ClassCastException}，再以
 * {@code Diagnostic.Kind.ERROR} 上报，导致整个模块编译失败——即「带 {@code @SqlTable} 的实体必须是顶层类」。</p>
 *
 * <p><b>修复后</b>：包名逐层向上查找，常量类固定生成在「实体类所在包 {@code .sql}」下，命名规则为
 * 顶层类 {@code User$}、嵌套类 {@code Outer_Inner$}（嵌套层级用下划线拍平），
 * 运行时 {@link SqlBeanUtil#getConstantClass(Class)} 使用同一套规则查找。</p>
 */
public class NestedEntityConstantTest {

    /** 父类实体（不生成常量类，仅用于验证嵌套实体的父类字段也能被收集） */
    public static class BaseEntity {

        @SqlId(type = IdType.AUTO)
        @SqlColumn("id")
        private Long id;
    }

    /** 静态内部类实体，同时继承嵌套父类 */
    @SqlTable("nested_person")
    public static class Person extends BaseEntity {

        @SqlColumn
        private String name;

        @SqlColumn("person_age")
        private Integer age;
    }

    /** 更深一层嵌套，验证多级层级拍平 */
    public static class Wrapper {

        @SqlTable("nested_city")
        public static class City {

            @SqlColumn
            private String cityName;
        }
    }

    @Test
    public void testNestedEntityConstantClassGenerated() throws Exception {
        Class<?> constantClass = SqlBeanUtil.getConstantClass(Person.class);
        Assert.assertNotNull("嵌套实体的常量类应被生成并可按新命名规则查找到", constantClass);
        Assert.assertEquals("cn.vonce.sql.spring.service.sql.NestedEntityConstantTest_Person$",
                constantClass.getName());
        Assert.assertEquals("nested_person", constantClass.getField("_tableName").get(null));
        Assert.assertEquals("nested_person", constantClass.getField("_tableAlias").get(null));
        Assert.assertEquals("nested_person.*", constantClass.getField("_all").get(null));
    }

    @Test
    public void testNestedEntityConstantContainsOwnAndSuperFields() throws Exception {
        Class<?> constantClass = SqlBeanUtil.getConstantClass(Person.class);
        Assert.assertNotNull(constantClass);
        // 自身字段：驼峰转下划线
        Assert.assertEquals("name", constantClass.getField("name").get(null));
        // 自身字段：@SqlColumn 显式命名
        Assert.assertEquals("person_age", constantClass.getField("person_age").get(null));
        // 父类字段：嵌套父类的字段同样应被收集
        Assert.assertEquals("id", constantClass.getField("id").get(null));
        // Column 常量（字段名 + $）也应生成
        Assert.assertNotNull(constantClass.getField("person_age$").get(null));
    }

    @Test
    public void testDeeplyNestedEntityConstantClassGenerated() throws Exception {
        Class<?> constantClass = SqlBeanUtil.getConstantClass(Wrapper.City.class);
        Assert.assertNotNull("多层嵌套实体的常量类应被生成", constantClass);
        Assert.assertEquals("cn.vonce.sql.spring.service.sql.NestedEntityConstantTest_Wrapper_City$",
                constantClass.getName());
        Assert.assertEquals("nested_city", constantClass.getField("_tableName").get(null));
        // 默认开启驼峰转下划线
        Assert.assertEquals("city_name", constantClass.getField("city_name").get(null));
    }

    /** 顶层实体的命名规则必须保持不变，避免破坏既有用户代码 */
    @Test
    public void testTopLevelEntityConstantClassNamingUnchanged() throws Exception {
        Class<?> constantClass = SqlBeanUtil.getConstantClass(ItUser.class);
        Assert.assertNotNull(constantClass);
        Assert.assertEquals("cn.vonce.sql.spring.service.sql.ItUser$", constantClass.getName());
        Assert.assertEquals("it_user", constantClass.getField("_tableName").get(null));
    }

}
