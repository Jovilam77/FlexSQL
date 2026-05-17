package cn.vonce.sql.solon.datasource;

import cn.vonce.sql.java.annotation.DbSource;
import cn.vonce.sql.java.annotation.DbSwitch;
import cn.vonce.sql.java.datasource.DataSourceContextHolder;
import cn.vonce.sql.java.datasource.TransactionalContextHolder;
import cn.vonce.sql.java.enumerate.DbRole;
import cn.vonce.sql.uitls.StringUtil;
import org.noear.solon.core.aspect.Interceptor;
import org.noear.solon.core.aspect.Invocation;
import java.lang.reflect.Method;
import java.util.Random;
import java.util.logging.Logger;

/**
 * 数据源切换切点
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2020/10/29 21:24
 */
public class DataSourceInterceptor implements Interceptor {

    private static final Logger logger = Logger.getLogger(DataSourceInterceptor.class.getName());

    @Override
    public Object doIntercept(Invocation inv) throws Throwable {
        Class<?> clazz = inv.target().getClass();
        String xid = TransactionalContextHolder.getXid();
        if (clazz.isAnnotationPresent(DbSource.class) && StringUtil.isBlank(xid)) {
            String methodName = inv.method().getMethod().getName();
            Class[] parameterTypes = inv.method().getMethod().getParameterTypes();
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
        Object result = inv.invoke();
        DataSourceContextHolder.clearDataSource();
        return result;
    }
}

