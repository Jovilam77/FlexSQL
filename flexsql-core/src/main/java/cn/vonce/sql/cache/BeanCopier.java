package cn.vonce.sql.cache;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量级深拷贝工具（零依赖，基于反射）。
 * <p>缓存命中后返回「副本」而非缓存里的原始对象引用，避免调用方修改返回 bean 污染缓存。
 * 策略（v2 起为递归式全深拷贝）：
 * <ul>
 *   <li>标量/不可变类型（String、Number、日期、enum、Class 等）直接复用，不做拷贝；</li>
 *   <li>集合/Map：克隆容器，元素/值递归深拷贝；</li>
 *   <li>普通 bean：新建实例（无参构造）+ 逐字段递归深拷贝；</li>
 *   <li>record（仅 Java 16+ 运行时生效，Java 8 下无 record 故不会触发）：通过 canonical 构造器对各分量递归深拷贝；</li>
 *   <li>循环引用：用 IdentityHashMap 记录「源→副本」映射，重复引用复用同一副本（保留引用同一性，避免无限递归）；
 *       极端自环由深度上限兜底，防止栈溢出。</li>
 * </ul>
 * 拷贝失败（如类型既无无参构造也非 record）则原样返回被拷贝对象，不阻断业务。</p>
 *
 * @author Jovi
 * @version 2.0
 */
public final class BeanCopier {

    private static final int MAX_DEPTH = 64;

    private BeanCopier() {
    }

    /** 对缓存值做副本：自动识别 List / Map / 单个 bean（递归深拷贝）。 */
    public static Object copyValue(Object value) {
        return copyValue(value, new IdentityHashMap<>(), 0);
    }

    private static Object copyValue(Object value, Map<Object, Object> copies, int depth) {
        if (value == null) {
            return null;
        }
        // 深度上限兜底：防止病态自环导致栈溢出（极少见，主要发生在 record 持有自身分量时）
        if (depth > MAX_DEPTH) {
            return value;
        }
        if (value instanceof List) {
            return copyList((List<?>) value, copies, depth + 1);
        }
        if (value instanceof Map) {
            return copyMap((Map<?, ?>) value, copies, depth + 1);
        }
        if (isCopyableBean(value.getClass())) {
            return copy(value, copies, depth + 1);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static <T> T copy(T bean) {
        if (bean == null) {
            return null;
        }
        return (T) copy(bean, new IdentityHashMap<>(), 0);
    }

    private static Object copy(Object bean, Map<Object, Object> copies, int depth) {
        if (bean == null) {
            return null;
        }
        // 已拷贝过则复用副本，既防止无限递归，也保留引用同一性（钻石型结构不会重复拷贝）
        if (copies.containsKey(bean)) {
            return copies.get(bean);
        }
        Class<?> clazz = bean.getClass();
        if (!isCopyableBean(clazz)) {
            return bean;
        }
        try {
            Object target;
            if (isRecord(clazz)) {
                target = copyRecord(bean, clazz, copies, depth + 1);
            } else {
                Constructor<?> ctor = clazz.getDeclaredConstructor();
                ctor.setAccessible(true);
                target = ctor.newInstance();
                // 先登记再拷贝字段，使自引用字段在递归时复用本副本
                copies.put(bean, target);
                Class<?> walk = clazz;
                while (walk != null && walk != Object.class) {
                    for (Field f : walk.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) {
                            continue;
                        }
                        f.setAccessible(true);
                        Object v = f.get(bean);
                        f.set(target, copyValue(v, copies, depth + 1));
                    }
                    walk = walk.getSuperclass();
                }
            }
            return target;
        } catch (Exception e) {
            // 拷贝失败则原样返回（不阻断业务）
            return bean;
        }
    }

    /**
     * 是否为 record。通过反射探测 {@code Class.isRecord()}，在 Java 8 等不支持 record 的运行时
     * 会捕获 NoSuchMethodException 并返回 false（该环境下本就不可能存在 record 实例）。
     */
    private static boolean isRecord(Class<?> clazz) {
        try {
            Method m = Class.class.getMethod("isRecord");
            return Boolean.TRUE.equals(m.invoke(clazz));
        } catch (NoSuchMethodException e) {
            return false; // Java < 16：不存在 record
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 深拷贝 record：反射取得各分量类型与访问器，对分量递归深拷贝后通过 canonical 构造器重建。
     * 全程使用反射引用 RecordComponent，避免对 Java 8 运行时产生硬依赖（record 路径仅在 Java 16+ 被触发）。
     */
    private static Object copyRecord(Object bean, Class<?> clazz, Map<Object, Object> copies, int depth) throws Exception {
        Method getRecordComponents = Class.class.getMethod("getRecordComponents");
        Object[] comps = (Object[]) getRecordComponents.invoke(clazz);
        Class<?>[] types = new Class<?>[comps.length];
        Object[] vals = new Object[comps.length];
        for (int i = 0; i < comps.length; i++) {
            Class<?> rcType = comps[i].getClass();
            Method getType = rcType.getMethod("getType");
            types[i] = (Class<?>) getType.invoke(comps[i]);
            Method getAccessor = rcType.getMethod("getAccessor");
            Method accessor = (Method) getAccessor.invoke(comps[i]);
            accessor.setAccessible(true);
            // 各分量递归深拷贝（分量可能是可变集合/bean）
            vals[i] = copyValue(accessor.invoke(bean), copies, depth);
        }
        Constructor<?> ctor = clazz.getDeclaredConstructor(types);
        ctor.setAccessible(true);
        return ctor.newInstance(vals);
    }

    /** 兼容旧调用：等价于 {@link #copyList(List, Map, int)} 以新副本映射。 */
    public static List<?> copyList(List<?> list) {
        return copyList(list, new IdentityHashMap<>(), 0);
    }

    /** 兼容旧调用：等价于 {@link #copyMap(Map, Map, int)} 以新副本映射。 */
    public static Map<?, ?> copyMap(Map<?, ?> map) {
        return copyMap(map, new IdentityHashMap<>(), 0);
    }

    private static List<?> copyList(List<?> list, Map<Object, Object> copies, int depth) {
        if (list == null) {
            return null;
        }
        List<Object> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(copyValue(item, copies, depth));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> copyMap(Map<?, ?> map, Map<Object, Object> copies, int depth) {
        if (map == null) {
            return null;
        }
        Map<Object, Object> result = new HashMap<>(map.size());
        for (Map.Entry<?, ?> e : map.entrySet()) {
            result.put(e.getKey(), copyValue(e.getValue(), copies, depth));
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
