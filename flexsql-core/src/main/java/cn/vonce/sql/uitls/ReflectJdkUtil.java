package cn.vonce.sql.uitls;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Logger;

/**
 * 反射工具类 JDK
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2020/7/24 18:30
 */
public class ReflectJdkUtil implements Reflect {

    private static final Logger logger = Logger.getLogger(ReflectJdkUtil.class.getName());

    private final Map<String, Method> methodMap = new WeakHashMap<>();
    private final Map<Class<?>, Constructor<?>> constructorMap = new WeakHashMap<>();
    private static volatile ReflectJdkUtil reflectJdkUtil;

    private ReflectJdkUtil() {

    }

    public static ReflectJdkUtil instance() {
        if (reflectJdkUtil == null) {
            synchronized (ReflectJdkUtil.class) {
                if (reflectJdkUtil == null) {
                    reflectJdkUtil = new ReflectJdkUtil();
                }
            }
        }
        return reflectJdkUtil;
    }

    @Override
    public Object newObject(Class<?> clazz) {
        Constructor<?> constructor = constructorMap.get(clazz);
        if (constructor == null) {
            try {
                constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                constructorMap.put(clazz, constructor);
            } catch (NoSuchMethodException e) {
                logger.warning("No default constructor found for " + clazz.getName() + ": " + e.getMessage());
                throw new RuntimeException("No default constructor found for " + clazz.getName(), e);
            }
        }
        try {
            return constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to instantiate " + clazz.getName(), e);
        }
    }

    @Override
    public Object get(Class<?> clazz, Object instance, String name) {
        if (clazz == null || name == null || name.trim().length() == 0) {
            return null;
        }
        name = "get" + name.substring(0, 1).toUpperCase() + name.substring(1);
        return invoke(clazz, instance, name);
    }

    @Override
    public void set(Class<?> clazz, Object instance, String name, Object value) {
        if (clazz == null || name == null || name.trim().length() == 0) {
            return;
        }
        name = "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
        invoke(clazz, instance, name, value);
    }

    @Override
    public Object invoke(Class<?> clazz, Object instance, String name) {
        try {
            String methodFullName = clazz.getName() + "." + name;
            Method method = methodMap.get(methodFullName);
            if (method == null) {
                method = clazz.getMethod(name);
                methodMap.put(methodFullName, method);
            }
            return method.invoke(instance);
        } catch (IllegalAccessException e) {
            logger.warning("Illegal access invoking " + name + " on " + clazz.getName() + ": " + e.getMessage());
        } catch (InvocationTargetException e) {
            logger.warning("Invocation target exception invoking " + name + " on " + clazz.getName() + ": " + e.getMessage());
        } catch (NoSuchMethodException e) {
            logger.warning("No such method " + name + " on " + clazz.getName() + ": " + e.getMessage());
        }
        return null;
    }

    public Object invoke(Class<?> clazz, Object instance, String name, Object value) {
        try {
            String methodFullName = clazz.getName() + "." + name;
            Method method = methodMap.get(methodFullName);
            if (method == null) {
                method = getMethod(clazz.getMethods(), name, 1);
                methodMap.put(methodFullName, method);
            }
            return method.invoke(instance, value);
        } catch (IllegalAccessException e) {
            logger.warning("Illegal access invoking " + name + " on " + clazz.getName() + ": " + e.getMessage());
        } catch (InvocationTargetException e) {
            logger.warning("Invocation target exception invoking " + name + " on " + clazz.getName() + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public Object invoke(Class<?> clazz, Object instance, String name, Class<?>[] parameterTypes, Object[] values) {
        try {
            String methodFullName = clazz.getName() + "." + name;
            Method method = methodMap.get(methodFullName);
            if (method == null) {
                method = clazz.getMethod(name, parameterTypes);
                methodMap.put(methodFullName, method);
            }
            return method.invoke(instance, values);
        } catch (IllegalAccessException e) {
            logger.warning("Illegal access invoking " + name + " on " + clazz.getName() + ": " + e.getMessage());
        } catch (InvocationTargetException e) {
            logger.warning("Invocation target exception invoking " + name + " on " + clazz.getName() + ": " + e.getMessage());
        } catch (NoSuchMethodException e) {
            logger.warning("No such method " + name + " on " + clazz.getName() + ": " + e.getMessage());
        }
        return null;
    }

    private Method getMethod(Method[] methods, String name, int paramCount) {
        for (Method method : methods) {
            if (method.getName().equals(name) && method.getParameterTypes().length == paramCount) {
                return method;
            }
        }
        return null;
    }

}
