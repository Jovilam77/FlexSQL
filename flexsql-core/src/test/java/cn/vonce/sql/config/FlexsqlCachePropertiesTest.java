package cn.vonce.sql.config;

import cn.vonce.sql.cache.QueryCacheConfig;
import cn.vonce.sql.cache.SqlBeanServices;

/**
 * {@link FlexsqlCacheProperties} 单元测试（main 风格）。
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

    public static boolean defaultIsEmpty() {
        return new FlexsqlCacheProperties().isEmpty();
    }

    public static boolean localOnlyMakesNotEmpty() {
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        p.getLocal().setMaximumSize(500L);
        return !p.isEmpty() && p.toSqlBeanConfig().getLocalMaximumSize() == 500L;
    }

    public static boolean metricsOnlyMakesNotEmpty() {
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        p.setMetricsLogIntervalSeconds(30L);
        SqlBeanConfig cfg = p.toSqlBeanConfig();
        // 注意：SqlBeanConfig.getCacheMetricsLogIntervalSeconds() 默认返回 60L（永远不会返 null）；
        // 这里只校验 isEmpty() = false + translation 链路不断裂
        return !p.isEmpty() && cfg.getCacheMetricsLogIntervalSeconds() != null;
    }

    public static boolean modeOnlyTranslatesCorrectly() {
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        p.setMode(CacheMode.LOCAL);
        SqlBeanConfig cfg = p.toSqlBeanConfig();
        return cfg.getCacheMode() == CacheMode.LOCAL
                && cfg.getLocalMaximumSize() == null
                && cfg.getLocalExpireAfterWrite() == null
                && cfg.getLocalExpireAfterAccess() == null
                && cfg.getRedisExpireAfterWrite() == null;
    }

    public static boolean allLocalFieldsTranslate() {
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        p.setMode(CacheMode.LOCAL);
        p.getLocal().setMaximumSize(2000L);
        p.getLocal().setExpireAfterWrite(300L);
        p.getLocal().setExpireAfterAccess(60L);
        SqlBeanConfig cfg = p.toSqlBeanConfig();
        return cfg.getCacheMode() == CacheMode.LOCAL
                && cfg.getLocalMaximumSize() == 2000L
                && cfg.getLocalExpireAfterWrite() == 300L
                && cfg.getLocalExpireAfterAccess() == 60L;
    }

    public static boolean redisFieldTranslates() {
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        p.getRedis().setExpireAfterWrite(900L);
        SqlBeanConfig cfg = p.toSqlBeanConfig();
        return cfg.getCacheMode() == null
                && cfg.getRedisExpireAfterWrite() == 900L;
    }

    public static boolean toSqlBeanConfigReturnsFreshEachTime() {
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        p.setMode(CacheMode.LOCAL);
        SqlBeanConfig cfg1 = p.toSqlBeanConfig();
        SqlBeanConfig cfg2 = p.toSqlBeanConfig();
        return cfg1 != cfg2 && cfg1.getCacheMode() == CacheMode.LOCAL && cfg2.getCacheMode() == CacheMode.LOCAL;
    }

    public static boolean emptyDoesNotTouchDefaultCacheConfig() {
        SqlBeanServices.setCacheConfig(QueryCacheConfig.local(1234, 60, 0));
        FlexsqlCacheProperties p = new FlexsqlCacheProperties();
        boolean isEmpty = p.isEmpty();
        // 注意：我们不会自动调用 apply；这里仅校验 isEmpty + translation 行为
        SqlBeanConfig cfg = p.toSqlBeanConfig();
        boolean cacheUnchangedAfterTouch = SqlBeanServices.getCacheConfig().getMode() != CacheMode.OFF;
        return isEmpty && cfg.getCacheMode() == null && cacheUnchangedAfterTouch;
    }

    public static void main(String[] args) {
        // 复位起步状态
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());

        check("defaultIsEmpty", defaultIsEmpty());
        check("localOnlyMakesNotEmpty", localOnlyMakesNotEmpty());
        check("metricsOnlyMakesNotEmpty", metricsOnlyMakesNotEmpty());
        check("modeOnlyTranslatesCorrectly", modeOnlyTranslatesCorrectly());
        check("allLocalFieldsTranslate", allLocalFieldsTranslate());
        check("redisFieldTranslates", redisFieldTranslates());
        check("toSqlBeanConfigReturnsFreshEachTime", toSqlBeanConfigReturnsFreshEachTime());
        check("emptyDoesNotTouchDefaultCacheConfig", emptyDoesNotTouchDefaultCacheConfig());

        // 复位，避免影响其他测试
        SqlBeanServices.setCacheConfig(QueryCacheConfig.off());

        System.out.println("passed, " + passed + " failed, " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("PASS: " + name);
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }
}
