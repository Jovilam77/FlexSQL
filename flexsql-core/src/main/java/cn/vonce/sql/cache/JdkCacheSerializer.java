package cn.vonce.sql.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * 基于 JDK 原生序列化的 {@link CacheSerializer}。
 * <p>注意：被缓存的实体（及其嵌套对象）必须实现 {@link java.io.Serializable}，
 * 否则写入 Redis 时会抛 {@link java.io.NotSerializableException}。
 * 若实体不便实现 Serializable，可注入 JSON 序列化器替代。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public class JdkCacheSerializer implements CacheSerializer {

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return null;
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("JDK 序列化缓存值失败，请确认实体实现 Serializable 或改用 JSON 序列化器", e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (Exception e) {
            throw new IllegalStateException("JDK 反序列化缓存值失败", e);
        }
    }
}
