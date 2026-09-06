package cn.vonce.sql.cache;

/**
 * 缓存值序列化器（可插拔）。
 * <p>本地缓存（Caffeine / Simple）通常直接持有 Java 对象引用，无需序列化；
 * 但分布式缓存（Redis）需要把结果对象序列化为字节数组。默认提供 JDK 序列化实现，
 * 也可替换为 JSON（如 Jackson / Fastjson）以获更好的跨语言性与可读性。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public interface CacheSerializer {

    byte[] serialize(Object obj);

    Object deserialize(byte[] bytes);
}
