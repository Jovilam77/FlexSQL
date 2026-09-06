package cn.vonce.sql.provider;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 动态Schema持有者
 *
 * 采用「栈」结构而非单一值，以支持方法嵌套调用场景：
 * 外层 @DbDynSchema 方法设置租户A，内层 @DbDynSchema 方法设置租户B，
 * 内层返回后外层仍应恢复为租户A；非 @DbDynSchema 方法不应干扰外层已设置的 schema。
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2021/6/19 00:23
 */
public class DynSchemaContextHolder {

    private static final ThreadLocal<Deque<String>> contextHolder = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 设置当前Schema（入栈）
     *
     * @param schema
     */
    public static void setSchema(String schema) {
        contextHolder.get().push(schema);
    }

    /**
     * 获取当前Schema（取栈顶，不弹出）
     *
     * @return
     */
    public static String getSchema() {
        Deque<String> deque = contextHolder.get();
        return deque.isEmpty() ? null : deque.peek();
    }

    /**
     * 清除当前Schema（出栈一级；栈空时移除 ThreadLocal 避免内存泄漏）
     */
    public static void clearSchema() {
        Deque<String> deque = contextHolder.get();
        if (!deque.isEmpty()) {
            deque.pop();
        }
        if (deque.isEmpty()) {
            contextHolder.remove();
        }
    }

}
