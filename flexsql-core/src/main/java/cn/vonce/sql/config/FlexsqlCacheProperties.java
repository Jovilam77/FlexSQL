package cn.vonce.sql.config;

import java.io.Serializable;

/**
 * FlexSQL yml / properties 属性绑定专用 POJO（<b>不注册成 Bean</b>）。
 * <p>字段命名面向 yml / properties 友好（{@code flexsql.cache.*} kebab-case + 嵌套），
 * 与 {@link SqlBeanConfig} 的驼峰字段命名不同——这是有意为之：本类只承担
 * "把用户从 yml 输入映射到内存对象"的中介角色，最终字段值会通过
 * {@link #toSqlBeanConfig()} 翻译给 {@link SqlBeanConfig}（cache 配置的真正入口）。</p>
 *
 * <p>由 spring 与 solon 模块分别在启动期从 {@code Environment} /
 * {@code cfg().getProperty(...)} 读字段填入本对象；<b>不会注册成 Bean</b>，避免
 * 与用户 {@code @Bean SqlBeanConfig} 冲突。</p>
 *
 * <p><b>用户使用方式</b>：仅需在 application.yml / app.yml 写：</p>
 * <pre>{@code
 * flexsql:
 *   cache:
 *     mode: local
 *     local:
 *       maximum-size: 1000
 *       expire-after-write: 600
 *       expire-after-access: 0    # 0 = 不设置
 *     redis:
 *       expire-after-write: 600
 *     metrics-log-interval-seconds: 60   # 0 = 关闭
 *     key-eviction-by-id: true          # 按主键精确失效（默认 false；只对 selectById 类缓存项生效）
 * }</pre>
 *
 * <p><b>注意</b>：不写任何 {@code flexsql.cache.*} → {@link #isEmpty()} 返回 true →
 * 框架 binder 直接跳过，<b>对不配用户零干扰</b>。</p>
 *
 * @author Jovi
 * @since 1.1
 */
public class FlexsqlCacheProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 顶层 key：{@code flexsql.cache.mode} */
    private CacheMode mode;

    /** 嵌套 key：{@code flexsql.cache.local.*}；用 final 实例保证 binder 可以直接 getLocal() 写入，无需先 setLocal。 */
    private final Local local = new Local();

    /** 嵌套 key：{@code flexsql.cache.redis.*} */
    private final Redis redis = new Redis();

    /** 顶层 key：{@code flexsql.cache.metrics-log-interval-seconds} */
    private Long metricsLogIntervalSeconds;

    /** 顶层 key：{@code flexsql.cache.key-eviction-by-id}；null 表示用户未设置，{@link #isEmpty()} 视为 true。 */
    private Boolean keyEvictionById;

    public CacheMode getMode() {
        return mode;
    }

    public void setMode(CacheMode mode) {
        this.mode = mode;
    }

    public Local getLocal() {
        return local;
    }

    public Redis getRedis() {
        return redis;
    }

    public Long getMetricsLogIntervalSeconds() {
        return metricsLogIntervalSeconds;
    }

    public void setMetricsLogIntervalSeconds(Long metricsLogIntervalSeconds) {
        this.metricsLogIntervalSeconds = metricsLogIntervalSeconds;
    }

    public Boolean getKeyEvictionById() {
        return keyEvictionById;
    }

    public void setKeyEvictionById(Boolean keyEvictionById) {
        this.keyEvictionById = keyEvictionById;
    }

    /**
     * 是否"完全没配"（{@code null} + 所有子对象为空）。框架 binder 用此判断"零干扰"。
     */
    public boolean isEmpty() {
        return mode == null
                && local.isEmpty()
                && redis.isEmpty()
                && metricsLogIntervalSeconds == null
                && keyEvictionById == null;
    }

    /**
     * 翻译成 {@link SqlBeanConfig}。仅复制非 null 字段，保留
     * {@link SqlBeanConfig} 的"已存在则跳过" set 语义（{@code setXxx} 内部 if null 才写）。
     */
    public SqlBeanConfig toSqlBeanConfig() {
        SqlBeanConfig cfg = new SqlBeanConfig();
        if (mode != null) {
            cfg.setCacheMode(mode);
        }
        if (local.maximumSize != null) {
            cfg.setLocalMaximumSize(local.maximumSize);
        }
        if (local.expireAfterWrite != null) {
            cfg.setLocalExpireAfterWrite(local.expireAfterWrite);
        }
        if (local.expireAfterAccess != null) {
            cfg.setLocalExpireAfterAccess(local.expireAfterAccess);
        }
        if (redis.expireAfterWrite != null) {
            cfg.setRedisExpireAfterWrite(redis.expireAfterWrite);
        }
        if (metricsLogIntervalSeconds != null) {
            cfg.setCacheMetricsLogIntervalSeconds(metricsLogIntervalSeconds);
        }
        if (keyEvictionById != null) {
            cfg.setKeyEvictionById(keyEvictionById);
        }
        return cfg;
    }

    /** yml 嵌套：{@code flexsql.cache.local.*} */
    public static class Local implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long maximumSize;
        private Long expireAfterWrite;
        private Long expireAfterAccess;

        public Long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(Long maximumSize) {
            this.maximumSize = maximumSize;
        }

        public Long getExpireAfterWrite() {
            return expireAfterWrite;
        }

        public void setExpireAfterWrite(Long expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
        }

        public Long getExpireAfterAccess() {
            return expireAfterAccess;
        }

        public void setExpireAfterAccess(Long expireAfterAccess) {
            this.expireAfterAccess = expireAfterAccess;
        }

        public boolean isEmpty() {
            return maximumSize == null && expireAfterWrite == null && expireAfterAccess == null;
        }
    }

    /** yml 嵌套：{@code flexsql.cache.redis.*} */
    public static class Redis implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long expireAfterWrite;

        public Long getExpireAfterWrite() {
            return expireAfterWrite;
        }

        public void setExpireAfterWrite(Long expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
        }

        public boolean isEmpty() {
            return expireAfterWrite == null;
        }
    }
}
