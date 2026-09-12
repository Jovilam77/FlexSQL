package cn.vonce.sql.cache;

import cn.vonce.sql.config.CacheMode;
import cn.vonce.sql.config.SqlBeanConfig;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 9/5–9/12 code review P2 项回归测试（缓存侧）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li><b>P2-4</b> Redis 反序列化异常不降级 —— 脏键 / 实体结构变更会让读请求直接抛错，
 *       且异常路径不 evict，只能等 TTL。修复后按未命中处理并删除坏键；写路径序列化失败亦降级为「跳过缓存」。</li>
 *   <li><b>P2-6</b> {@code cacheMode: CUSTOM} 静默忽略 —— 旧实现落到 {@code default: return null}，
 *       既不改全局配置也没有任何告警。修复后必须显式告警。</li>
 * </ul>
 *
 * @author Jovi
 */
public class P2CacheTest {

    private static QueryCacheKey key() {
        return new QueryCacheKey(P2CacheTest.class, null, "select", "SELECT 1", "t", null, "", null);
    }

    /** 序列化/反序列化都必定失败的序列化器（模拟实体结构变更 / 不兼容）。 */
    private static final CacheSerializer BROKEN = new CacheSerializer() {
        @Override
        public byte[] serialize(Object obj) {
            throw new IllegalStateException("boom-serialize");
        }

        @Override
        public Object deserialize(byte[] bytes) {
            throw new IllegalStateException("boom-deserialize");
        }
    };

    // ==================== P2-4 Redis 降级 ====================

    @Test
    public void deserializeFailureDegradesToMissAndEvictsBadKey() {
        FakeRedisOps ops = new FakeRedisOps();
        QueryCacheKey k = key();
        // 预置一个「坏值」（脏键）
        ops.store.put(k.toStoreKey(), new byte[]{1, 2, 3});

        RedisQueryCache cache = new RedisQueryCache(ops, BROKEN, 60);

        Object result = cache.get(k);
        Assert.assertNull("反序列化失败必须降级为未命中（让调用方回源 DB），而不是向上抛异常", result);
        Assert.assertTrue("坏键必须被删除，否则每次读都会抛错、只能等 TTL: " + ops.deleted,
                ops.deleted.contains(k.toStoreKey()));
        Assert.assertFalse("坏键已删除: " + ops.store, ops.store.containsKey(k.toStoreKey()));
    }

    @Test
    public void missingKeyReturnsNullWithoutTouchingSerializer() {
        FakeRedisOps ops = new FakeRedisOps();
        RedisQueryCache cache = new RedisQueryCache(ops, BROKEN, 60);
        Assert.assertNull(cache.get(key()));
        Assert.assertTrue("未命中不应产生删除动作: " + ops.deleted, ops.deleted.isEmpty());
    }

    @Test
    public void serializeFailureSkipsCacheWriteInsteadOfFailingQuery() {
        FakeRedisOps ops = new FakeRedisOps();
        RedisQueryCache cache = new RedisQueryCache(ops, BROKEN, 60);

        cache.put(key(), "value", "t_tenant", "t-1");

        Assert.assertTrue("序列化失败应跳过写入，而不是让查询整体失败: " + ops.store, ops.store.isEmpty());
        Assert.assertTrue("不应写入失效索引: " + ops.sets, ops.sets.isEmpty());
    }

    // ==================== P2-6 CUSTOM 模式告警 ====================

    @Test
    public void customModeWarnsInsteadOfSilentlyIgnoring() {
        SqlBeanConfig cfg = new SqlBeanConfig();
        cfg.setCacheMode(CacheMode.CUSTOM);

        PrintStream oldErr = System.err;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(buf, true, "UTF-8"));
            QueryCacheConfig built = cfg.buildQueryCacheConfig(null);
            Assert.assertNull("CUSTOM 无法仅靠 Bean 完成，应返回 null（不改全局配置）", built);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            System.setErr(oldErr);
        }

        String err = buf.toString();
        Assert.assertTrue("CUSTOM 必须显式告警（旧实现静默忽略，用户会以为已开启缓存）: " + err,
                err.contains("CUSTOM"));
    }

    @Test
    public void offAndLocalModesAreUnaffected() {
        SqlBeanConfig off = new SqlBeanConfig();
        off.setCacheMode(CacheMode.OFF);
        Assert.assertEquals(CacheMode.OFF, off.buildQueryCacheConfig(null).getMode());

        SqlBeanConfig local = new SqlBeanConfig();
        local.setCacheMode(CacheMode.LOCAL);
        local.setLocalMaximumSize(10L);
        Assert.assertEquals(CacheMode.LOCAL, local.buildQueryCacheConfig(null).getMode());

        // 没设置 cacheMode → 视为未配置
        Assert.assertNull(new SqlBeanConfig().buildQueryCacheConfig(null));
    }

    /** 最小可用的 RedisOps 假实现（内存 Map）。 */
    private static final class FakeRedisOps implements RedisOps {
        final Map<String, byte[]> store = new HashMap<>();
        final Set<String> deleted = new HashSet<>();
        final Set<String> sets = new HashSet<>();

        @Override
        public byte[] get(String key) {
            return store.get(key);
        }

        @Override
        public void set(String key, byte[] value, long ttlMillis) {
            store.put(key, value);
        }

        @Override
        public void delete(String key) {
            store.remove(key);
            deleted.add(key);
        }

        @Override
        public void sadd(String setKey, String member) {
            sets.add(setKey);
        }

        @Override
        public Set<String> smembers(String setKey) {
            return new HashSet<>();
        }

        @Override
        public void srem(String setKey, String member) {
        }
    }
}
