package cn.vonce.sql.java.annotation;

import java.lang.annotation.*;

/**
 * 事务注解
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2022/12/14 11:21
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DbTransactional {

    /**
     * 是否只读
     *
     * @return
     */
    boolean readOnly() default false;

    /**
     * 事务超时时间（秒）
     * -1 表示不设置超时
     *
     * @return
     */
    int timeout() default -1;

    /**
     * 什么异常回滚
     *
     * @return
     */
    Class<? extends Throwable>[] rollbackFor() default {};

    /**
     * 什么异常不回滚
     *
     * @return
     */
    Class<? extends Throwable>[] noRollbackFor() default {};

}
