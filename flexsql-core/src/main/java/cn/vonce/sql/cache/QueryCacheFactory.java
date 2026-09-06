package cn.vonce.sql.cache;

/**
 * 本地查询缓存实现工厂（可插拔 SPI，核心零依赖）。
 * <p>核心内置且默认的本地实现是 {@link SimpleQueryCache}（零依赖 LRU + TTL）。
 * 若希望把 {@link CacheMode#LOCAL} 换成其它本地缓存库（如 Caffeine），
 * 由可选模块在启动时注册本工厂，之后 {@link QueryCacheConfig#local(long, long, long)}
 * 便会产出该实现的实例，业务代码无需改动。</p>
 * <p>这样核心模块始终不依赖任何具体缓存库，可选实现放在独立模块中（如 flexsql-caffeine）。</p>
 *
 * @author Jovi
 * @version 1.0
 * @see QueryCacheConfig#setLocalCacheFactory(QueryCacheFactory)
 */
public interface QueryCacheFactory {

    /**
     * 创建本地缓存实例
     *
     * @param maximumSize               容量上限（条目数），&lt;=0 时实现应自行取默认值
     * @param expireAfterWriteSeconds   写入后过期秒数，0 表示不过期
     * @param expireAfterAccessSeconds  访问后过期秒数，0 表示不按访问过期
     * @return 本地缓存实例
     */
    QueryCache create(long maximumSize, long expireAfterWriteSeconds, long expireAfterAccessSeconds);

}
