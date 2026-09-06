package cn.vonce.sql.cache;

/**
 * 查询缓存模式（全局、互斥二选一）。
 * <p>取代原先 per-service 的缓存开关。默认 {@link #OFF}。</p>
 */
public enum CacheMode {

    /** 关闭查询缓存（默认）。caching() 原样返回 service，不影响既有行为。 */
    OFF,

    /**
     * 本地缓存（内置 SimpleQueryCache，零依赖 LRU+TTL）。
     * <b>仅限单机部署</b>：本地缓存是进程内的，多节点各自一份；若用于分布式部署，
     * 写节点失效无法触达其它节点，会产生跨节点脏读。
     */
    LOCAL,

    /**
     * 分布式缓存（基于中心 Redis，见 {@link RedisQueryCache}）。
     * 所有节点共享同一份 Redis 缓存，任意节点写后失效对其它节点即时生效，跨节点一致。
     * 适用于多节点部署。
     */
    REDIS
}
