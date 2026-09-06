package cn.vonce.sql.annotation;

import java.lang.annotation.*;

/**
 * 标识该字段为租户ID列（行级多租户隔离列）。
 * <p>
 * 配合 {@code TenantContextHolder} 使用：
 * <ul>
 *     <li>INSERT 时自动以当前租户ID写入该列，业务层不可指定租户，防止越权写入；</li>
 *     <li>SELECT / UPDATE / DELETE 的 WHERE 自动追加 {@code tenant_id = 当前租户} 过滤条件，
 *         且始终生效（无论是否显式传入 where），实现行级租户隔离，防止跨租户数据越权访问。</li>
 * </ul>
 * 与 {@code @DbDynSchema}（schema 级隔离）互补：schema 隔离不同命名空间，tenant_id 隔离同一表内的行。
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2026/9/6
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
@Inherited
public @interface SqlTenantId {
}
