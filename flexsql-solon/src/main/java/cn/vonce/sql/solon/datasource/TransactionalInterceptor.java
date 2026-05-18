package cn.vonce.sql.solon.datasource;

import cn.vonce.sql.java.annotation.DbTransactional;
import cn.vonce.sql.java.datasource.ConnectionContextHolder;
import cn.vonce.sql.java.datasource.TransactionTimeoutManager;
import cn.vonce.sql.java.datasource.TransactionalContextHolder;
import cn.vonce.sql.uitls.IdBuilder;
import cn.vonce.sql.uitls.StringUtil;
import org.noear.solon.core.aspect.Interceptor;
import org.noear.solon.core.aspect.Invocation;

/**
 * 事务拦截器
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2022/12/14 11:34
 */
public class TransactionalInterceptor implements Interceptor {

    @Override
    public Object doIntercept(Invocation inv) throws Throwable {
        // 获取类级别注解
        DbTransactional dbTransactional = inv.getMethodAnnotation(DbTransactional.class);
        // 获取方法级别注解（优先）
        if (inv.method().getMethod().isAnnotationPresent(DbTransactional.class)) {
            dbTransactional = inv.method().getMethod().getAnnotation(DbTransactional.class);
        }
        
        // 如果没有事务注解，直接执行
        if (dbTransactional == null) {
            return inv.invoke();
        }
        
        Object result;
        String xid = null;
        try {
            xid = TransactionalContextHolder.getXid();
            //已经存在事务则加入事务并执行
            if (StringUtil.isNotBlank(xid)) {
                result = inv.invoke();
                return result;
            }
            //当前没有事务则创建事务
            else {
                if (dbTransactional.readOnly()) {
                    ConnectionContextHolder.setReadOnly(true);
                }
                xid = IdBuilder.uuid();
                TransactionalContextHolder.setXid(xid);
                
                // 调度事务超时任务
                int timeout = dbTransactional.timeout();
                if (timeout > 0) {
                    TransactionTimeoutManager.scheduleTimeout(xid, timeout);
                }
                
                result = inv.invoke();
                //取消超时任务
                TransactionTimeoutManager.cancelTimeout(xid);
                //移除事务id
                TransactionalContextHolder.clearXid();
                //提交或回滚事务
                ConnectionContextHolder.commitOrRollback(true);
            }
        } catch (Throwable e) {
            //取消超时任务
            TransactionTimeoutManager.cancelTimeout(xid);
            //移除事务id
            TransactionalContextHolder.clearXid();
            Class<? extends Throwable>[] rollbackFor = dbTransactional.rollbackFor();
            Class<? extends Throwable>[] noRollbackFor = dbTransactional.noRollbackFor();
            boolean needRollback = false;
            if (rollbackFor.length > 0) {
                //遇到哪些异常回滚
                for (Class<? extends Throwable> thr : rollbackFor) {
                    if (thr.isAssignableFrom(e.getClass())) {
                        needRollback = true;
                        break;
                    }
                }
            }
            if (noRollbackFor.length > 0) {
                //遇到哪些异常不回滚
                for (Class<? extends Throwable> thr : noRollbackFor) {
                    if (thr.isAssignableFrom(e.getClass())) {
                        needRollback = false;
                        break;
                    }
                }
            }
            if (rollbackFor.length == 0 && noRollbackFor.length == 0) {
                //回滚事务
                needRollback = true;
            }
            ConnectionContextHolder.commitOrRollback(!needRollback);
            throw e;
        }
        return result;
    }

}
