package cn.vonce.sql.uitls;

import cn.vonce.sql.bean.Column;
import cn.vonce.sql.define.ColumnFun;
import cn.vonce.sql.exception.SqlBeanException;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Lambda工具类
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2022/12/7 19:18
 */
public class LambdaUtil {

    private static final Logger logger = Logger.getLogger(LambdaUtil.class.getName());

    /**
     * 获取列字段对象
     *
     * @param column
     * @param <T>
     * @param <R>
     * @return
     */
    public static <T, R> Column getColumn(ColumnFun<T, R> column) {
        if (SqlBeanUtil.isAndroidEnv()){
            throw new SqlBeanException("Android环境不支持Lambda表达式指定列字段(XXXClass::getXXX)");
        }
        SerializedLambda lambda = null;
        try {
            Method method = column.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(Boolean.TRUE);
            lambda = (SerializedLambda) method.invoke(column);
        } catch (NoSuchMethodException e) {
            logger.warning("No writeReplace method found for lambda: " + e.getMessage());
        } catch (InvocationTargetException e) {
            logger.warning("Failed to invoke writeReplace on lambda: " + e.getMessage());
        } catch (IllegalAccessException e) {
            logger.warning("Illegal access invoking writeReplace on lambda: " + e.getMessage());
        }
        return SqlBeanUtil.getColumnByLambda(lambda);
    }

}
