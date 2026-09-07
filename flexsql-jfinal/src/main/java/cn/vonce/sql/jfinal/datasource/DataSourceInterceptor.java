package cn.vonce.sql.jfinal.datasource;

import cn.vonce.sql.java.annotation.DbSource;
import cn.vonce.sql.java.annotation.DbSwitch;
import cn.vonce.sql.java.datasource.DataSourceContextHolder;
import cn.vonce.sql.java.datasource.TransactionalContextHolder;
import cn.vonce.sql.java.enumerate.DbRole;
import cn.vonce.sql.uitls.StringUtil;
import com.jfinal.aop.Interceptor;
import com.jfinal.aop.Invocation;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.logging.Logger;

/**
 * 数据源切换切点
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 21:24
 */
public class DataSourceInterceptor implements Interceptor {

    private static final Logger logger = Logger.getLogger(DataSourceInterceptor.class.getName());

    @Override
    public void intercept(Invocation inv) {
        Class<?> clazz = inv.getTarget().getClass();
        String xid = TransactionalContextHolder.getXid();
        if (clazz.isAnnotationPresent(DbSource.class) && StringUtil.isBlank(xid)) {
            String methodName = inv.getMethod().getName();
            Class<?>[] parameterTypes = inv.getMethod().getParameterTypes();
            String dataSource = null;
            DbSource dbSource = clazz.getAnnotation(DbSource.class);
            try {
                Method method = clazz.getMethod(methodName, parameterTypes);
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
                logger.warning("Failed to resolve data source: " + e.getMessage());
            }
            DataSourceContextHolder.setDataSource(dataSource);
        }
        try {
            inv.invoke();
        } finally {
            DataSourceContextHolder.clearDataSource();
        }
    }

}