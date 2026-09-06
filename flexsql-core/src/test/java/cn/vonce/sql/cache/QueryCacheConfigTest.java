package cn.vonce.sql.cache;

import cn.vonce.sql.config.CacheMode;
import cn.vonce.sql.config.SqlBeanConfig;
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

    /** 桩实现：用于验证 custom 模式与可插拔本地缓存工厂。 */
    static class StubQueryCache implements QueryCache {
        @Override
        public Object get(QueryCacheKey key) {
            return null;
        }

        @Override
        public void put(QueryCacheKey key, Object value, String table, Object tenantId) {
        }

        @Override
        public void evictByTable(String table, String schema, Object tenantId, String dataSource) {
        }

        @Override
        public void clear() {
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

        // ---- custom：外部实现注入 ----
        StubQueryCache stub = new StubQueryCache();
        QueryCacheConfig custom = QueryCacheConfig.custom(stub);
        check("custom.mode", custom.getMode() == CacheMode.CUSTOM);
        check("custom.sameInstance", custom.getCache() == stub);
        check("custom.nullBecomesOff", QueryCacheConfig.custom(null).getMode() == CacheMode.OFF);

        // ---- 可插拔本地缓存工厂（Caffeine 等可选模块通过它替换 LOCAL 实现）----
        QueryCacheFactory factory = (maxSize, writeTtl, accessTtl) -> stub;
        QueryCacheConfig.setLocalCacheFactory(factory);
        check("factory.registered", QueryCacheConfig.getLocalCacheFactory() == factory);
        QueryCacheConfig localByFactory = QueryCacheConfig.local(100, 600, 0);
        check("factory.usedByLocal", localByFactory.getCache() == stub);
        check("factory.keepsLocalMode", localByFactory.getMode() == CacheMode.LOCAL);
        QueryCacheConfig.setLocalCacheFactory(null);
        check("factory.unregistered", QueryCacheConfig.getLocalCacheFactory() == null);
        check("factory.fallbackToSimple", QueryCacheConfig.local(100, 600, 0).getCache() instanceof SimpleQueryCache);

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

        // ===== applyFromSqlBeanConfig：SqlBeanConfig → 全局 QueryCacheConfig =====
        SqlBeanConfig cfgLocal = new SqlBeanConfig();
        cfgLocal.setCacheMode(CacheMode.LOCAL);
        cfgLocal.setLocalMaximumSize(500L);
        cfgLocal.setLocalExpireAfterWrite(300L);
        // 没调编程式 setCacheConfig，先复位 OFF
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
        boolean applied1 = SqlBeanServices.applyFromSqlBeanConfig(cfgLocal, null);
        check("apply.localApplied", applied1);
        check("apply.localMode", SqlBeanServices.getCacheConfig().getMode() == CacheMode.LOCAL);
        check("apply.localMaxSize", ((SimpleQueryCache) SqlBeanServices.getCacheConfig().getCache()).getClass() == SimpleQueryCache.class);

        // 用户没设 cacheMode → 视为未配置，不写 OFF 也不动其他
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
        SqlBeanConfig cfgEmpty = new SqlBeanConfig();
        cfgEmpty.setToUpperCase(true); // 任意非 cache 字段
        boolean applied2 = SqlBeanServices.applyFromSqlBeanConfig(cfgEmpty, null);
        check("apply.emptyNotApplied", !applied2);
        check("apply.emptyKeepsOff", SqlBeanServices.getCacheConfig().getMode() == CacheMode.OFF);

        // 编程式优先：已 setCacheConfig(LOCAL) 时 Bean 即使是 REDIS 也不覆盖
        SqlBeanServices.setCacheConfig(QueryCacheConfig.local(100, 60, 0));
        SqlBeanConfig cfgRedis = new SqlBeanConfig();
        cfgRedis.setCacheMode(CacheMode.REDIS);
        boolean applied3 = SqlBeanServices.applyFromSqlBeanConfig(cfgRedis, new MemRedisOps());
        check("apply.programmaticWinsNoCover", !applied3);
        check("apply.programmaticKeepsLocal", SqlBeanServices.getCacheConfig().getMode() == CacheMode.LOCAL);

        // REDIS 模式缺 RedisOps → 应用但降级 OFF
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
        SqlBeanConfig cfgRedisNoImpl = new SqlBeanConfig();
        cfgRedisNoImpl.setCacheMode(CacheMode.REDIS);
        boolean applied4 = SqlBeanServices.applyFromSqlBeanConfig(cfgRedisNoImpl, null);
        check("apply.redisWithoutImplStillApplied", applied4);
        check("apply.redisDowngradeOff", SqlBeanServices.getCacheConfig().getMode() == CacheMode.OFF);

        // null config 直接 no-op
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
        boolean applied5 = SqlBeanServices.applyFromSqlBeanConfig(null, null);
        check("apply.nullConfigNoOp", !applied5);

        // 复位为 OFF
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());

        System.out.println("\nQueryCacheConfigTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
