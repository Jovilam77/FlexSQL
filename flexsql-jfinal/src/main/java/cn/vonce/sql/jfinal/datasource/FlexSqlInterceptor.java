package cn.vonce.sql.jfinal.datasource;

import cn.vonce.sql.java.annotation.DbSource;
import cn.vonce.sql.java.annotation.DbSwitch;
import cn.vonce.sql.java.annotation.DbTransactional;
import cn.vonce.sql.java.datasource.ConnectionContextHolder;
import cn.vonce.sql.java.datasource.DataSourceContextHolder;
import cn.vonce.sql.java.datasource.TransactionTimeoutManager;
import cn.vonce.sql.java.datasource.TransactionalContextHolder;
import cn.vonce.sql.java.enumerate.DbRole;
import cn.vonce.sql.uitls.IdBuilder;
import cn.vonce.sql.uitls.StringUtil;
import com.jfinal.aop.Interceptor;
import com.jfinal.aop.Invocation;

import java.lang.reflect.Method;
import java.util.Random;

/**
 * FlexSQL统一拦截器
 * 处理数据源切换和事务管理
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
public class FlexSqlInterceptor implements Interceptor {

    @Override
    public void intercept(Invocation inv) {
        Class<?> clazz = inv.getTarget().getClass();
        Method method = inv.getMethod();
        
        // 处理事务
        handleTransaction(inv, clazz, method);
    }

    /**
     * 处理事务
     */
    private void handleTransaction(Invocation inv, Class<?> clazz, Method method) {
        // 获取类级别注解
        DbTransactional dbTransactional = clazz.getAnnotation(DbTransactional.class);
        // 获取方法级别注解（优先）
        if (method.isAnnotationPresent(DbTransactional.class)) {
            dbTransactional = method.getAnnotation(DbTransactional.class);
        }

        String xid = null;
        boolean hasTransaction = dbTransactional != null;
        
        try {
            // 处理数据源切换（在事务之前）
            handleDataSourceSwitch(inv, clazz, method, hasTransaction);
            
            if (hasTransaction) {
                xid = TransactionalContextHolder.getXid();
                // 已经存在事务则加入事务并执行
                if (StringUtil.isNotBlank(xid)) {
                    inv.invoke();
                    return;
                }
                // 当前没有事务则创建事务
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

                    inv.invoke();
                    // 取消超时任务
                    TransactionTimeoutManager.cancelTimeout(xid);
                    // 移除事务id
                    TransactionalContextHolder.clearXid();
                    // 提交事务
                    ConnectionContextHolder.commitOrRollback(true);
                }
            } else {
                // 没有事务，直接执行
                inv.invoke();
            }
        } catch (Throwable e) {
            if (hasTransaction) {
                // 取消超时任务
                TransactionTimeoutManager.cancelTimeout(xid);
                // 移除事务id
                TransactionalContextHolder.clearXid();
                
                boolean needRollback = true;
                if (dbTransactional != null) {
                    Class<? extends Throwable>[] rollbackFor = dbTransactional.rollbackFor();
                    Class<? extends Throwable>[] noRollbackFor = dbTransactional.noRollbackFor();
                    
                    if (rollbackFor.length > 0) {
                        needRollback = false;
                        for (Class<? extends Throwable> thr : rollbackFor) {
                            if (thr.isAssignableFrom(e.getClass())) {
                                needRollback = true;
                                break;
                            }
                        }
                    }
                    if (noRollbackFor.length > 0) {
                        for (Class<? extends Throwable> thr : noRollbackFor) {
                            if (thr.isAssignableFrom(e.getClass())) {
                                needRollback = false;
                                break;
                            }
                        }
                    }
                }
                ConnectionContextHolder.commitOrRollback(!needRollback);
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        } finally {
            // 清理数据源上下文
            DataSourceContextHolder.clearDataSource();
        }
    }

    /**
     * 处理数据源切换
     */
    private void handleDataSourceSwitch(Invocation inv, Class<?> clazz, Method method, boolean hasTransaction) {
        if (hasTransaction) {
            // 事务中不切换数据源
            return;
        }
        
        String xid = TransactionalContextHolder.getXid();
        if (clazz.isAnnotationPresent(DbSource.class) && StringUtil.isBlank(xid)) {
            String methodName = method.getName();
            Class<?>[] parameterTypes = method.getParameterTypes();
            String dataSource = null;
            DbSource dbSource = clazz.getAnnotation(DbSource.class);
            
            try {
                DbSwitch dbSwitch = method.getAnnotation(DbSwitch.class);
                if (dbSwitch != null && dbSwitch.value() == DbRole.SLAVE && dbSource.slave().length > 0) {
                    if (dbSource.slave().length == 1) {
                        dataSource = dbSource.slave()[0];
                    } else {
                        dataSource = dbSource.slave()[new Random().nextInt(dbSource.slave().length)];
                    }
                } else {
                    dataSource = dbSource.master();
                }
            } catch (Exception e) {
                // 使用默认数据源
                dataSource = dbSource.master();
            }
            DataSourceContextHolder.setDataSource(dataSource);
        }
    }

}