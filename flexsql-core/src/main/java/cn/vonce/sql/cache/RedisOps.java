package cn.vonce.sql.cache;

import java.util.Set;

/**
 * Redis 操作抽象（可插拔）。
 * <p>FlexSQL 核心不依赖任何 Redis 客户端，由调用方注入自己的实现
 * （如基于 Spring Data Redis、Lettuce、Jedis 封装），从而「兼容 Redis 缓存」而无需引入硬依赖。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public interface RedisOps {

    /** 读取键值，不存在返回 null */
    byte[] get(String key);

    /** 写入键值，并指定过期时间（毫秒），ttlMillis&lt;=0 表示不过期 */
    void set(String key, byte[] value, long ttlMillis);

    /** 删除键 */
    void delete(String key);

    /** 将 member 加入集合 setKey */
    void sadd(String setKey, String member);

    /** 获取集合全部成员 */
    Set<String> smembers(String setKey);

    /** 从集合移除 member */
    void srem(String setKey, String member);
}
