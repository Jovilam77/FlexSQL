package cn.vonce.sql.uitls;

import java.lang.reflect.Field;
import java.util.logging.Logger;

/**
 * 反射工具抽象类
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2020/7/24 18:30
 */
public abstract class ReflectUtil {

    private static final Logger logger = Logger.getLogger(ReflectUtil.class.getName());
    private static volatile Reflect reflect;

    public static Reflect instance() {
        if (reflect == null) {
            synchronized (ReflectUtil.class) {
                if (reflect == null) {
                    try {
                        Class.forName("android.content.Context");
                        reflect = ReflectJdkUtil.instance();
                    } catch (ClassNotFoundException e) {
                        reflect = ReflectAsmUtil.instance();
                    }
                }
            }
        }
        return reflect;
    }

    /**
     * 利用反射获取指定对象的指定属性
     *
     * @param obj       目标对象
     * @param fieldName 目标属性
     * @return 目标属性的值
     */
    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object obj, String fieldName) {
        Object result = null;
        Field field = ReflectUtil.getField(obj, fieldName);
        if (field != null) {
            field.setAccessible(true);
            try {
                result = field.get(obj);
            } catch (IllegalArgumentException e) {
                logger.warning("获取字段值失败(参数错误): " + e.getMessage());
            } catch (IllegalAccessException e) {
                logger.warning("获取字段值失败(访问受限): " + e.getMessage());
            }
        }
        return (T) result;
    }

    /**
     * 利用反射获取指定对象里面的指定属性
     *
     * @param obj       目标对象
     * @param fieldName 目标属性
     * @return 目标字段
     */
    public static Field getField(Object obj, String fieldName) {
        Field field = null;
        for (Class<?> clazz = obj.getClass(); clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                field = clazz.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                // 这里不用做处理，子类没有该字段可能对应的父类有，都没有就返回null。
            }
        }
        return field;
    }

    /**
     * 利用反射设置指定对象的指定属性为指定的值
     *
     * @param obj        目标对象
     * @param fieldName  目标属性
     * @param fieldValue 目标值
     */
    public static void setFieldValue(Object obj, String fieldName, String fieldValue) {
        Field field = ReflectUtil.getField(obj, fieldName);
        if (field != null) {
            try {
                field.setAccessible(true);
                field.set(obj, fieldValue);
            } catch (IllegalArgumentException e) {
                logger.warning("设置字段值失败(参数错误): " + e.getMessage());
            } catch (IllegalAccessException e) {
                logger.warning("设置字段值失败(访问受限): " + e.getMessage());
            }
        }
    }

}
