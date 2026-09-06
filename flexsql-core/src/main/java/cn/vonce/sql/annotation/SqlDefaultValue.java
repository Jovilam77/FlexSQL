package cn.vonce.sql.annotation;

import cn.vonce.sql.enumerate.FillWith;

import java.lang.annotation.*;

/**
 * 标识该注解的字段如果为null自动注入默认值（仅支持基本类型、String、Date、Timestamp、BigDecimal）
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2022/6/24 20:22
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
@Inherited
public @interface SqlDefaultValue {

    /**
     * 填充类型（insert=新增、update=更新，together=新增更新同时）
     *
     * @return
     */
    FillWith with();

    /**
     * 是否注入当前操作人（如 createBy / updateBy）。
     * 为 true 时，填充值取自 SqlBeanUtil.getCurrentUser(fieldType)，
     * 需提前通过 SqlBeanUtil.setCurrentUserSupplier(...) 注册当前用户解析器（如从登录上下文取 userId）。
     * 仅当 with() 指定的时机命中时才注入；未注册解析器时该字段不填充（返回 null，由调用方回落）。
     *
     * @return
     */
    boolean user() default false;

    /**
     * 只读审计字段（如 createTime / createBy）：INSERT 时按 with() 填充一次，
     * UPDATE 的 SET 子句中跳过该字段，防止业务层误改创建信息。
     *
     * @return
     */
    boolean readonly() default false;

}
