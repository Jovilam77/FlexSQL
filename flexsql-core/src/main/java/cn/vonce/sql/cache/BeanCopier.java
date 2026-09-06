package cn.vonce.sql.cache;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量级深拷贝工具（零依赖，基于反射）。
 * <p>缓存命中后返回「副本」而非缓存里的原始对象引用，避免调用方修改返回 bean 污染缓存。
 * 策略：新建实例并逐字段拷贝；集合/Map 克隆容器（元素仍共享引用）；标量/不可变类型直接复用。
 * 不做递归式全深拷贝（嵌套 bean 字段共享），足以覆盖「调用方改顶层字段/集合」的常见场景。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public final class BeanCopier {

    private BeanCopier() {
    }

    /** 对缓存值做副本：自动识别 List / Map / 单个 bean */
    public static Object copyValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            return copyList((List<?>) value);
        }
        if (value instanceof Map) {
            return copyMap((Map<?, ?>) value);
        }
        if (isCopyableBean(value.getClass())) {
            return copy(value);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static <T> T copy(T bean) {
        if (bean == null) {
            return null;
        }
        Class<?> clazz = bean.getClass();
        if (!isCopyableBean(clazz)) {
            return bean;
        }
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object target = ctor.newInstance();
            Class<?> walk = clazz;
            while (walk != null && walk != Object.class) {
                for (Field f : walk.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) {
                        continue;
                    }
                    f.setAccessible(true);
                    Object v = f.get(bean);
                    f.set(target, cloneIfContainer(v));
                }
                walk = walk.getSuperclass();
            }
            return (T) target;
        } catch (Exception e) {
            // 拷贝失败则原样返回（不阻断业务）
            return bean;
        }
    }

    private static Object cloneIfContainer(Object v) {
        if (v instanceof Collection) {
            return new ArrayList<>((Collection<?>) v);
        }
        if (v instanceof Map) {
            return new HashMap<>((Map<?, ?>) v);
        }
        return v;
    }

    public static List<?> copyList(List<?> list) {
        if (list == null) {
            return null;
        }
        List<Object> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(isCopyableBean(item == null ? null : item.getClass()) ? copy(item) : item);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<?, ?> copyMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        Map<Object, Object> result = new HashMap<>(map.size());
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object val = e.getValue();
            result.put(e.getKey(), isCopyableBean(val == null ? null : val.getClass()) ? copy(val) : val);
        }
        return result;
    }

    private static boolean isCopyableBean(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        if (clazz.isPrimitive() || clazz.isEnum()) {
            return false;
        }
        if (clazz == String.class || Number.class.isAssignableFrom(clazz)
                || clazz == Boolean.class || clazz == Character.class
                || clazz == java.util.Date.class || clazz == java.sql.Date.class
                || clazz == java.sql.Timestamp.class || clazz == java.time.LocalDate.class
                || clazz == java.time.LocalDateTime.class || clazz == java.time.LocalTime.class) {
            return false;
        }
        // 数组与常见 JDK 容器不按 bean 拷贝
        if (clazz.isArray() || Collection.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz)) {
            return false;
        }
        return true;
    }
}
