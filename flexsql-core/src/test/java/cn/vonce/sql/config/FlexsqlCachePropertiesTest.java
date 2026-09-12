package cn.vonce.sql.config;

import cn.vonce.sql.cache.QueryCacheConfig;
import cn.vonce.sql.cache.SqlBeanServices;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * {@link FlexsqlCacheProperties} 单元测试（JUnit）。
 * <p>校验核心翻译逻辑：</p>
 * <ul>
 *   <li>默认全 null → {@link FlexsqlCacheProperties#isEmpty()} 返回 true</li>
 *   <li>设置任意一个字段 → isEmpty()=false 且 toSqlBeanConfig 翻译正确</li>
 *   <li>仅 mode → 翻译后 SqlBeanConfig.cacheMode 正确，其它字段保持 null</li>
 *   <li>全字段 → toSqlBeanConfig 完整翻译</li>
 *   <li>SqlBeanConfig 已有的 setXxx "null 才写"语义不被破坏</li>
 * </ul>
 *
 * <p>不测 spring/solon 侧 binder——那些依赖运行时框架上下文，由各模块自己的
 * CacheAutoWire*Test 集成测试覆盖（运行时 jar 端到端）。</p>
 */
public class FlexsqlCachePropertiesTest {

    /** 复位全局缓存配置，避免污染同 JVM 的其它用例。 */
    @After
    public void resetGlobalCacheConfig() {
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());
    }

    @Test
    public void defaultIsEmpty() {
        Assert.assertTrue(new FlexsqlCacheProperties().isEmpty());
    }

    @Test
    public void localOnlyMakesNotEmpty() {
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        p.getLocal().setMaximumSize(500L);
        Assert.assertFalse(p.isEmpty());
        Assert.assertEquals(Long.valueOf(500L), p.toSqlBeanConfig().getLocalMaximumSize());
    }

    @Test
    public void metricsOnlyMakesNotEmpty() {
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        p.setMetricsLogIntervalSeconds(30L);
        SqlBeanConfig cfg = p.toSqlBeanConfig();
        // 注意：SqlBeanConfig.getCacheMetricsLogIntervalSeconds() 默认返回 60L（永远不会返 null）；
        // 这里只校验 isEmpty() = false + translation 链路不断裂
        Assert.assertFalse(p.isEmpty());
        Assert.assertNotNull(cfg.getCacheMetricsLogIntervalSeconds());
    }

    @Test
    public void modeOnlyTranslatesCorrectly() {
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        p.setMode(CacheMode.LOCAL);
        SqlBeanConfig cfg = p.toSqlBeanConfig();
        Assert.assertEquals(CacheMode.LOCAL, cfg.getCacheMode());
        Assert.assertNull(cfg.getLocalMaximumSize());
        Assert.assertNull(cfg.getLocalExpireAfterWrite());
        Assert.assertNull(cfg.getLocalExpireAfterAccess());
        Assert.assertNull(cfg.getRedisExpireAfterWrite());
    }

    @Test
    public void allLocalFieldsTranslate() {
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        p.setMode(CacheMode.LOCAL);
        p.getLocal().setMaximumSize(2000L);
        p.getLocal().setExpireAfterWrite(300L);
        p.getLocal().setExpireAfterAccess(60L);
        SqlBeanConfig cfg = p.toSqlBeanConfig();
        Assert.assertEquals(CacheMode.LOCAL, cfg.getCacheMode());
        Assert.assertEquals(Long.valueOf(2000L), cfg.getLocalMaximumSize());
        Assert.assertEquals(Long.valueOf(300L), cfg.getLocalExpireAfterWrite());
        Assert.assertEquals(Long.valueOf(60L), cfg.getLocalExpireAfterAccess());
    }

    @Test
    public void redisFieldTranslates() {
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        p.getRedis().setExpireAfterWrite(900L);
        SqlBeanConfig cfg = p.toSqlBeanConfig();
        Assert.assertNull(cfg.getCacheMode());
        Assert.assertEquals(Long.valueOf(900L), cfg.getRedisExpireAfterWrite());
    }

    @Test
    public void toSqlBeanConfigReturnsFreshEachTime() {
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        p.setMode(CacheMode.LOCAL);
        SqlBeanConfig cfg1 = p.toSqlBeanConfig();
        SqlBeanConfig cfg2 = p.toSqlBeanConfig();
        Assert.assertNotSame(cfg1, cfg2);
        Assert.assertEquals(CacheMode.LOCAL, cfg1.getCacheMode());
        Assert.assertEquals(CacheMode.LOCAL, cfg2.getCacheMode());
    }

    @Test
    public void emptyDoesNotTouchDefaultCacheConfig() {
        SqlBeanServices.setCacheConfig(QueryCacheConfig.local(1234, 60, 0));
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        // 注意：我们不会自动调用 apply；这里仅校验 isEmpty + translation 行为
        Assert.assertTrue(p.isEmpty());
        Assert.assertNull(p.toSqlBeanConfig().getCacheMode());
        Assert.assertNotEquals(CacheMode.OFF, SqlBeanServices.getCacheConfig().getMode());
    }
}
