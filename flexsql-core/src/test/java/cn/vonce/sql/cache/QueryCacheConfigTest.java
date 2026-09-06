package cn.vonce.sql.cache;

import cn.vonce.sql.service.SqlBeanService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;

/**
 * QueryCacheConfig / SqlBeanServices 全局开关单元测试（main 风格，无需 DB）。
 */
public class QueryCacheConfigTest {

    static int pass = 0;
    static int fail = 0;

    static int check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("  PASS " + name);
        } else {
            fail++;
            System.out.println("  FAIL " + name);
        }
        return ok ? 1 : 0;
    }

    @SuppressWarnings("unchecked")
    static <T, ID> SqlBeanService<T, ID> dummy() {
        return (SqlBeanService<T, ID>) Proxy.newProxyInstance(
                SqlBeanService.class.getClassLoader(),
                new Class[]{SqlBeanService.class},
                (InvocationHandler) (proxy, method, args) -> {
                    if ("getBeanClass".equals(method.getName())) {
                        return Object.class;
                    }
                    if ("getSqlBeanMeta".equals(method.getName())) {
                        return null;
                    }
                    return null;
                });
    }

    /** 内存版 RedisOps，用于在无真实 Redis 时验证 redis 模式工厂。 */
    static class MemRedisOps implements RedisOps {
        private final Map<String, byte[]> data = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> sets = new ConcurrentHashMap<>();

        @Override
        public byte[] get(String key) {
            return data.get(key);
        }

        @Override
        public void set(String key, byte[] value, long ttlMillis) {
            data.put(key, value);
        }

        @Override
        public void delete(String key) {
            data.remove(key);
        }

        @Override
        public void sadd(String setKey, String member) {
            sets.computeIfAbsent(setKey, k -> ConcurrentHashMap.newKeySet()).add(member);
        }

        @Override
        public Set<String> smembers(String setKey) {
            return sets.getOrDefault(setKey, Collections.emptySet());
        }

        @Override
        public void srem(String setKey, String member) {
            Set<String> s = sets.get(setKey);
            if (s != null) {
                s.remove(member);
            }
        }
    }

    public static void main(String[] args) {
        // ---- QueryCacheConfig 工厂 ----
        QueryCacheConfig off = QueryCacheConfig.off();
        check("off.mode", off.getMode() == CacheMode.OFF);
        check("off.cacheNull", off.getCache() == null);

        QueryCacheConfig local = QueryCacheConfig.local(100, 600, 0);
        check("local.mode", local.getMode() == CacheMode.LOCAL);
        check("local.isSimple", local.getCache() instanceof SimpleQueryCache);
        check("local.notDistributed", !local.getCache().isDistributed());

        QueryCacheConfig redis = QueryCacheConfig.redis(new MemRedisOps(), 600);
        check("redis.mode", redis.getMode() == CacheMode.REDIS);
        check("redis.isRedis", redis.getCache() instanceof RedisQueryCache);
        check("redis.distributed", redis.getCache().isDistributed());

        // ---- 全局开关驱动 SqlBeanServices.caching ----
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
        SqlBeanService<Object, Object> d = dummy();
        check("off.returnsDelegate", SqlBeanServices.caching(d) == d);
        check("null.delegate", SqlBeanServices.caching(null) == null);
        check("getCacheConfig.off", SqlBeanServices.getCacheConfig().getMode() == CacheMode.OFF);

        SqlBeanServices.setCacheConfig(local);
        check("switchToLocal", SqlBeanServices.getCacheConfig().getMode() == CacheMode.LOCAL);
        // 注：local/redis 模式的端到端包裹（需真实 SqlBeanMeta）留待带 DB 的集成测试。

        SqlBeanServices.setCacheConfig(QueryCacheConfig.off()); // 复位，确保不影响其它测试

        System.out.println("\nQueryCacheConfigTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
