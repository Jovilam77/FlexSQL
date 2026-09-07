package cn.vonce.sql.java.annotation;

import java.lang.annotation.*;

/**
 * 启用 FlexSQL 原生 JDBC 实现
 * 在服务类上标注此注解，表示使用 FlexSQL 自带的 JDBC 实现，不依赖 MyBatis
 *
 * 使用方式：
 * <pre>
 * {@code @UseFlexSql}
 * public class UserService extends FlexSqlBeanServiceImpl<User, Long> {
 *     // ...
 * }
 * </pre>
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UseFlexSql {
}