package cn.vonce.sql.config;

import cn.vonce.sql.cache.QueryCacheConfig;
import cn.vonce.sql.cache.RedisOps;
import cn.vonce.sql.enumerate.JsonType;
import cn.vonce.sql.processor.DefaultUniqueIdProcessor;
import cn.vonce.sql.processor.UniqueIdProcessor;

import java.io.Serializable;

/**
 * SqlBean配置
 * <p>
 * 用户在 spring/solon 容器里以 {@code @Bean} 形式提供本类实例即可驱动框架：
 * <ul>
 *     <li>{@link #setCacheMode}/{link #setLocalMaximumSize}/{@link #setLocalExpireAfterWrite}/
 *     {@link #setLocalExpireAfterAccess}/{@link #setRedisExpireAfterWrite} 五个 cache 字段会在框架启动期
 *     被翻译为 {@link QueryCacheConfig} 并应用到全局 {@link cn.vonce.sql.cache.SqlBeanServices} 单例。</li>
 *     <li>未配置 cache 字段 → 缓存保持默认 OFF；未注册本类 Bean → 完全零影响。</li>
 *     <li>用户已编程式调用过 {@link cn.vonce.sql.cache.SqlBeanServices#setCacheConfig} 时，
 *     Bean 中的 cache 部分会被跳过（编程式优先，避免静默覆盖）。</li>
 * </ul>
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2018年6月12日下午2:48:12
 */
public class SqlBeanConfig implements Serializable {

    public SqlBeanConfig() {

    }

    private Boolean toUpperCase;
    private UniqueIdProcessor uniqueIdProcessor;
    private Boolean autoCreate;
    private JsonType jsonType;

    // ===== 查询缓存字段（用户可写在 yml/application.properties 然后通过 @Value/@Inject 注入到 Bean） =====
    // 默认全部 null：表示用户没配，框架不会把 OFF 强制写入，避免和默认 OFF 混淆判断。

    /** 缓存模式：OFF/LOCAL/REDIS；默认 null 表示用户未设置。 */
    private CacheMode cacheMode;

    /** LOCAL 模式：最大条目数。 */
    private Long localMaximumSize;

    /** LOCAL 模式：写入后过期秒数；0 或 null 表示不设置。 */
    private Long localExpireAfterWrite;

    /** LOCAL 模式：访问后过期秒数；0 或 null 表示不设置。 */
    private Long localExpireAfterAccess;

    /** REDIS 模式：写入后过期秒数；0 或 null 表示使用 RedisOps 默认。 */
    private Long redisExpireAfterWrite;

    public Boolean getToUpperCase() {
        return toUpperCase;
    }

    public void setToUpperCase(Boolean toUpperCase) {
        if (this.toUpperCase == null) {
            this.toUpperCase = toUpperCase;
        }
    }

    public UniqueIdProcessor getUniqueIdProcessor() {
        if (uniqueIdProcessor == null) {
            uniqueIdProcessor = new DefaultUniqueIdProcessor();
        }
        return uniqueIdProcessor;
    }

    public void setUniqueIdProcessor(UniqueIdProcessor uniqueIdProcessor) {
        if (this.uniqueIdProcessor == null) {
            this.uniqueIdProcessor = uniqueIdProcessor;
        }
    }

    public boolean getAutoCreate() {
        if (autoCreate == null) {
            autoCreate = true;
        }
        return autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        if (this.autoCreate == null) {
            this.autoCreate = true;
        }
    }

    public JsonType getJsonType() {
        return jsonType;
    }

    public void setJsonType(JsonType jsonType) {
        if (this.jsonType == null) {
            this.jsonType = jsonType;
        }
    }

    public CacheMode getCacheMode() {
        return cacheMode;
    }

    public void setCacheMode(CacheMode cacheMode) {
        if (this.cacheMode == null) {
            this.cacheMode = cacheMode;
        }
    }

    public Long getLocalMaximumSize() {
        return localMaximumSize;
    }

    public void setLocalMaximumSize(Long localMaximumSize) {
        if (this.localMaximumSize == null) {
            this.localMaximumSize = localMaximumSize;
        }
    }

    public Long getLocalExpireAfterWrite() {
        return localExpireAfterWrite;
    }

    public void setLocalExpireAfterWrite(Long localExpireAfterWrite) {
        if (this.localExpireAfterWrite == null) {
            this.localExpireAfterWrite = localExpireAfterWrite;
        }
    }

    public Long getLocalExpireAfterAccess() {
        return localExpireAfterAccess;
    }

    public void setLocalExpireAfterAccess(Long localExpireAfterAccess) {
        if (this.localExpireAfterAccess == null) {
            this.localExpireAfterAccess = localExpireAfterAccess;
        }
    }

    public Long getRedisExpireAfterWrite() {
        return redisExpireAfterWrite;
    }

    public void setRedisExpireAfterWrite(Long redisExpireAfterWrite) {
        if (this.redisExpireAfterWrite == null) {
            this.redisExpireAfterWrite = redisExpireAfterWrite;
        }
    }

    /**
     * 缓存指标周期日志输出间隔（秒）。默认 60。设为 0 或负数表示关闭。
     * <p>仅对 spring/solon 模块内置的 slf4j reporter 生效；用户接入 Micrometer/Actuator
     * 等监控体系时，可以忽略此字段，自己注册 reporter 即可。</p>
     */
    private Long cacheMetricsLogIntervalSeconds = 60L;

    public Long getCacheMetricsLogIntervalSeconds() {
        if (cacheMetricsLogIntervalSeconds == null) {
            cacheMetricsLogIntervalSeconds = 60L;
        }
        return cacheMetricsLogIntervalSeconds;
    }

    public void setCacheMetricsLogIntervalSeconds(Long cacheMetricsLogIntervalSeconds) {
        if (this.cacheMetricsLogIntervalSeconds == null) {
            this.cacheMetricsLogIntervalSeconds = cacheMetricsLogIntervalSeconds;
        }
    }

    /**
     * 把 SqlBeanConfig 的 cache 字段翻译为 {@link QueryCacheConfig}。
     * 仅在用户设置了 {@link #cacheMode} 时返回非 null；否则视为「用户未配置 cache」，
     * 调用方不应改动全局 {@link cn.vonce.sql.cache.SqlBeanServices} 默认状态。
     * <p>
     * {@link CacheMode#REDIS} 模式下若未提供 {@link RedisOps}，返回
     * {@link QueryCacheConfig#off()}（让调用方降级 + WARN，绝不阻断启动）。
     */
    public QueryCacheConfig buildQueryCacheConfig(RedisOps redisOps) {
        if (cacheMode == null) {
            return null;
        }
        switch (cacheMode) {
            case OFF:
                return QueryCacheConfig.off();
            case LOCAL:
                int maxSize = localMaximumSize == null ? 1000 : localMaximumSize.intValue();
                long writeTtl = localExpireAfterWrite == null ? 0 : localExpireAfterWrite;
                long accessTtl = localExpireAfterAccess == null ? 0 : localExpireAfterAccess;
                return QueryCacheConfig.local(maxSize, writeTtl, accessTtl);
            case REDIS:
                if (redisOps == null) {
                    // 缺实现时降级 OFF；调用方负责打 WARN，不抛异常避免阻断启动
                    return QueryCacheConfig.off();
                }
                long redisTtl = redisExpireAfterWrite == null ? 0 : redisExpireAfterWrite;
                return QueryCacheConfig.redis(redisOps, redisTtl);
            default:
                return null;
        }
    }

}