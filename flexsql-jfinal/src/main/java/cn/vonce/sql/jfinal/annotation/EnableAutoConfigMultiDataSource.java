package cn.vonce.sql.jfinal.annotation;

import java.lang.annotation.*;

/**
 * 启用自动配置多数据源
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:00
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface EnableAutoConfigMultiDataSource {

    /**
     * 默认数据源名称
     *
     * @return
     */
    String defaultDataSource() default "";

}