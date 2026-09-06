package cn.vonce.sql.provider;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 当前租户ID持有者（行级多租户隔离）
 * <p>
 * 采用「栈」结构而非单一值，以支持方法嵌套调用场景（与 {@link DynSchemaContextHolder} 一致）：
 * 外层方法设置租户A，内层方法设置租户B，内层返回后外层仍应恢复为租户A；
 * 非租户方法不应干扰外层已设置的租户ID。
 * <p>
 * 租户ID类型由业务决定（通常为 String / Long），故以 Object 持有。
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2026/9/6
 */
public class TenantContextHolder {

    private static final ThreadLocal<Deque<Object>> contextHolder = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 设置当前租户ID（入栈）
     *
     * @param tenantId 租户ID（如 "tenant_a" / 1001L）
     */
    public static void setTenantId(Object tenantId) {
        contextHolder.get().push(tenantId);
    }

    /**
     * 获取当前租户ID（取栈顶，不弹出）
     *
     * @return 当前租户ID；未设置时返回 null
     */
    public static Object getTenantId() {
        Deque<Object> deque = contextHolder.get();
        return deque.isEmpty() ? null : deque.peek();
    }

    /**
     * 清除当前租户ID（出栈一级；栈空时移除 ThreadLocal 避免内存泄漏）
     */
    public static void clearTenantId() {
        Deque<Object> deque = contextHolder.get();
        if (!deque.isEmpty()) {
            deque.pop();
        }
        if (deque.isEmpty()) {
            contextHolder.remove();
        }
    }

}
