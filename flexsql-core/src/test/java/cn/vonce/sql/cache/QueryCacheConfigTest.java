package cn.vonce.sql.cache;

import cn.vonce.sql.config.CacheMode;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.service.SqlBeanService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QueryCacheConfig / SqlBeanServices 全局开关单元测试（JUnit）。
 */
public class QueryCacheConfigTest {

    /** 复位全局状态（缓存模式 + 可插拔本地缓存工厂），避免污染同 JVM 的其它用例。 */
    @After
    public void resetGlobalState() {
        QueryCacheConfig.setLocalCacheFactory(null);
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
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

    @Test
    public void factoryCreatesOffLocalAndRedisConfigs() {
        QueryCacheConfig off = QueryCacheConfig.off();
        Assert.assertEquals(CacheMode.OFF, off.getMode());
        Assert.assertNull("OFF 模式不应带缓存实现", off.getCache());

        QueryCacheConfig local = QueryCacheConfig.local(100, 600, 0);
        Assert.assertEquals(CacheMode.LOCAL, local.getMode());
        Assert.assertTrue(local.getCache() instanceof SimpleQueryCache);
        Assert.assertFalse("本地缓存不应标记为分布式", local.getCache().isDistributed());

        QueryCacheConfig redis = QueryCacheConfig.redis(new MemRedisOps(), 600);
        Assert.assertEquals(CacheMode.REDIS, redis.getMode());
        Assert.assertTrue(redis.getCache() instanceof RedisQueryCache);
        Assert.assertTrue("Redis 缓存应标记为分布式", redis.getCache().isDistributed());
    }

    @Test
    public void customModeUsesExternalImplementation() {
        StubQueryCache stub = new StubQueryCache();
        QueryCacheConfig custom = QueryCacheConfig.custom(stub);
        Assert.assertEquals(CacheMode.CUSTOM, custom.getMode());
        Assert.assertSame("CUSTOM 模式应直接持有外部实现", stub, custom.getCache());
        Assert.assertEquals("custom(null) 应退化为 OFF", CacheMode.OFF, QueryCacheConfig.custom(null).getMode());
    }

    /** 可插拔本地缓存工厂（Caffeine 等可选模块通过它替换 LOCAL 实现） */
    @Test
    public void pluggableLocalCacheFactory() {
        StubQueryCache stub = new StubQueryCache();
        QueryCacheFactory factory = (maxSize, writeTtl, accessTtl) -> stub;
        try {
            QueryCacheConfig.setLocalCacheFactory(factory);
            Assert.assertSame(factory, QueryCacheConfig.getLocalCacheFactory());

            QueryCacheConfig localByFactory = QueryCacheConfig.local(100, 600, 0);
            Assert.assertSame("LOCAL 应改用工厂产出实现", stub, localByFactory.getCache());
            Assert.assertEquals("换实现不应改变模式语义", CacheMode.LOCAL, localByFactory.getMode());
        } finally {
            QueryCacheConfig.setLocalCacheFactory(null);
        }
        Assert.assertNull(QueryCacheConfig.getLocalCacheFactory());
        Assert.assertTrue("卸载工厂后应回退到内置 SimpleQueryCache",
                QueryCacheConfig.local(100, 600, 0).getCache() instanceof SimpleQueryCache);
    }

    /** 全局开关驱动 SqlBeanServices.caching */
    @Test
    public void globalSwitchDrivesSqlBeanServices() {
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
        SqlBeanService<Object, Object> d = dummy();
        Assert.assertSame("OFF 模式应原样返回 delegate", d, SqlBeanServices.caching(d));
        Assert.assertNull(SqlBeanServices.caching(null));
        Assert.assertEquals(CacheMode.OFF, SqlBeanServices.getCacheConfig().getMode());

        SqlBeanServices.setCacheConfig(QueryCacheConfig.local(100, 600, 0));
        Assert.assertEquals(CacheMode.LOCAL, SqlBeanServices.getCacheConfig().getMode());
        // 注：local/redis 模式的端到端包裹（需真实 SqlBeanMeta）留待带 DB 的集成测试。
    }

    /** SqlBeanConfig → 全局 QueryCacheConfig 的翻译与优先级 */
    @Test
    public void applyFromSqlBeanConfig() {
        // 1) 正常应用
        SqlBeanConfig cfgLocal = new SqlBeanConfig();
        cfgLocal.setCacheMode(CacheMode.LOCAL);
        cfgLocal.setLocalMaximumSize(500L);
        cfgLocal.setLocalExpireAfterWrite(300L);
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
        Assert.assertTrue(SqlBeanServices.applyFromSqlBeanConfig(cfgLocal, null));
        Assert.assertEquals(CacheMode.LOCAL, SqlBeanServices.getCacheConfig().getMode());
        Assert.assertTrue(SqlBeanServices.getCacheConfig().getCache() instanceof SimpleQueryCache);

        // 2) 用户没设 cacheMode → 视为未配置，不写 OFF 也不动其他
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
        SqlBeanConfig cfgEmpty = new SqlBeanConfig();
        cfgEmpty.setToUpperCase(true); // 任意非 cache 字段
        Assert.assertFalse(SqlBeanServices.applyFromSqlBeanConfig(cfgEmpty, null));
        Assert.assertEquals(CacheMode.OFF, SqlBeanServices.getCacheConfig().getMode());

        // 3) 编程式优先：已 setCacheConfig(LOCAL) 时 Bean 即使是 REDIS 也不覆盖
        SqlBeanServices.setCacheConfig(QueryCacheConfig.local(100, 60, 0));
        SqlBeanConfig cfgRedis = new SqlBeanConfig();
        cfgRedis.setCacheMode(CacheMode.REDIS);
        Assert.assertFalse(SqlBeanServices.applyFromSqlBeanConfig(cfgRedis, new MemRedisOps()));
        Assert.assertEquals("编程式配置优先，不应被 Bean 覆盖",
                CacheMode.LOCAL, SqlBeanServices.getCacheConfig().getMode());

        // 4) REDIS 模式缺 RedisOps → 应用但降级 OFF
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
        SqlBeanConfig cfgRedisNoImpl = new SqlBeanConfig();
        cfgRedisNoImpl.setCacheMode(CacheMode.REDIS);
        Assert.assertTrue(SqlBeanServices.applyFromSqlBeanConfig(cfgRedisNoImpl, null));
        Assert.assertEquals("缺 RedisOps 应降级 OFF", CacheMode.OFF, SqlBeanServices.getCacheConfig().getMode());

        // 5) null config 直接 no-op
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
        Assert.assertFalse(SqlBeanServices.applyFromSqlBeanConfig(null, null));
    }
}
