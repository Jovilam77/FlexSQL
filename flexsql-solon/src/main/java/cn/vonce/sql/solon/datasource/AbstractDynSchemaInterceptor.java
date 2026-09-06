package cn.vonce.sql.solon.datasource;

import cn.vonce.sql.java.annotation.DbDynSchema;
import cn.vonce.sql.provider.DynSchemaContextHolder;
import org.noear.solon.core.aspect.Interceptor;
import org.noear.solon.core.aspect.Invocation;

/**
 * 动态Schema切换切点
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2021/6/23 23:32
 */
public abstract class AbstractDynSchemaInterceptor implements Interceptor {

    public abstract String getSchema();

    @Override
    public Object doIntercept(Invocation inv) throws Throwable {
        Class<?> clazz = inv.target().getClass();
        boolean dynamic = clazz.isAnnotationPresent(DbDynSchema.class);
        if (dynamic) {
            DynSchemaContextHolder.setSchema(getSchema());
        }
        try {
            return inv.invoke();
        } finally {
            // 仅当本方法实际设置了 schema 时才出栈，避免清掉外层已设置的 schema；
            // 放在 finally 中确保方法抛异常时也不会在线程池里泄漏
            if (dynamic) {
                DynSchemaContextHolder.clearSchema();
            }
        }
    }

}

