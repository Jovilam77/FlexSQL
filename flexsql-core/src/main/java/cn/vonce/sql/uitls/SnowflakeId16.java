package cn.vonce.sql.uitls;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 廖雪峰的 53 bits unique id:
 * 53bitID由32bit秒级时间戳+16bit自增+5bit机器标识组成，累积32台机器，每秒可以生成6.5万个序列号
 * <p>
 * |--------|--------|--------|--------|--------|--------|--------|--------|
 * |00000000|00011111|11111111|11111111|11111111|11111111|11111111|11111111|
 * |--------|---xxxxx|xxxxxxxx|xxxxxxxx|xxxxxxxx|xxx-----|--------|--------|
 * |--------|--------|--------|--------|--------|---xxxxx|xxxxxxxx|xxx-----|
 * |--------|--------|--------|--------|--------|--------|--------|---xxxxx|
 * <p>
 * Maximum ID = 11111_11111111_11111111_11111111_11111111_11111111_11111111
 * <p>
 * Maximum TS = 11111_11111111_11111111_11111111_111
 * <p>
 * Maximum NT = ----- -------- -------- -------- ---11111_11111111_111 = 65535
 * <p>
 * Maximum SH = ----- -------- -------- -------- -------- -------- ---11111 = 31
 * <p>
 * It can generate 64k unique id per IP and up to 2106-02-07T06:28:15Z.
 */
public final class SnowflakeId16 implements Serializable {

//    private static final Logger logger = LoggerFactory.getLogger(SnowflakeId16.class);

    private static final Pattern PATTERN_LONG_ID = Pattern.compile("^([0-9]{15})([0-9a-f]{32})([0-9a-f]{3})$");

    private static final Pattern PATTERN_HOSTNAME = Pattern.compile("^.*\\D+([0-9]+)$");

    //暂时不支持jdk8
    //private static final long OFFSET = LocalDate.of(2000, 1, 1).atStartOfDay(ZoneId.of("Z")).toEpochSecond();
    private static final long OFFSET = 946684800;

    private static final long MAX_NEXT = 0b1111111111111111111L;

    private static final long SHARD_ID = getServerIdAsLong();

    private static final AtomicLong offset = new AtomicLong(0);
    private static final AtomicLong lastEpoch = new AtomicLong(0);

    public static long nextId() {
        return nextId(System.currentTimeMillis() / 1000);
    }

    private static long nextId(long epochSecond) {
        long currentEpoch = epochSecond;
        long last;
        
        while (true) {
            last = lastEpoch.get();
            
            if (currentEpoch < last) {
                // Clock is backwards, wait until clock catches up
                currentEpoch = System.currentTimeMillis() / 1000;
                continue;
            }
            
            if (currentEpoch == last) {
                // Same epoch, try to get next sequence
                long next = offset.incrementAndGet();
                if (next <= MAX_NEXT) {
                    return generateId(currentEpoch, next, SHARD_ID);
                }
                // Sequence exhausted, need to wait for next epoch
            }
            
            // Try to advance to next epoch
            if (lastEpoch.compareAndSet(last, currentEpoch)) {
                offset.set(1);
                return generateId(currentEpoch, 1, SHARD_ID);
            }
            // CAS failed, another thread advanced the epoch, retry
            currentEpoch = System.currentTimeMillis() / 1000;
        }
    }

    private static long generateId(long epochSecond, long next, long shardId) {
        return ((epochSecond - OFFSET) << 21) | (next << 5) | shardId;
    }

    private static long getServerIdAsLong() {
        // 首先尝试从主机名解析机器ID（兼容安卓异步调用）
        try {
            RunnableFuture<String> runnableFuture = new FutureTask<>(new Callable<String>() {
                @Override
                public String call() throws UnknownHostException {
                    return InetAddress.getLocalHost().getHostName();
                }
            });
            new Thread(runnableFuture).start();
            String hostname = runnableFuture.get();
            Matcher matcher = PATTERN_HOSTNAME.matcher(hostname);
            if (matcher.matches()) {
                long n = Long.parseLong(matcher.group(1));
                if (n >= 0 && n < 8) {
                    return n;
                }
            }
        } catch (ExecutionException e) {
            // 继续尝试其他方式
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 继续尝试其他方式
        }
        
        // 尝试使用IP地址哈希
        try {
            RunnableFuture<Long> ipFuture = new FutureTask<>(new Callable<Long>() {
                @Override
                public Long call() throws UnknownHostException {
                    InetAddress localHost = InetAddress.getLocalHost();
                    byte[] address = localHost.getAddress();
                    
                    // 使用IP地址哈希计算机器ID
                    int hash = 0;
                    for (byte b : address) {
                        hash = 31 * hash + (b & 0xFF);
                    }
                    
                    // 确保结果在0-31范围内
                    return Math.abs((long) hash) % 32;
                }
            });
            new Thread(ipFuture).start();
            return ipFuture.get();
            
        } catch (ExecutionException e) {
            // 继续尝试默认值
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 继续尝试默认值
        }
        
        // 如果以上方法都失败，使用一个合理的默认值
        // 基于系统属性或其他标识符生成一个相对唯一的值
        String pid = System.getProperty("pid", "0");
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null) {
            hostname = "unknown";
        }
        int hash = (pid + hostname).hashCode();
        return Math.abs((long) hash) % 32;
    }

    public static long stringIdToLongId(String stringId) {
        // a stringId id is composed as timestamp (15) + uuid (32) + serverId (000~fff).
        Matcher matcher = PATTERN_LONG_ID.matcher(stringId);
        if (matcher.matches()) {
            long epoch = Long.parseLong(matcher.group(1)) / 1000;
            String uuid = matcher.group(2);
            byte[] sha1 = HashUtil.sha1AsBytes(uuid);
            long next = ((sha1[0] << 24) | (sha1[1] << 16) | (sha1[2] << 8) | sha1[3]) & MAX_NEXT;
            long serverId = Long.parseLong(matcher.group(3), 16);
            return generateId(epoch, next, serverId);
        }
        throw new IllegalArgumentException("Invalid id: " + stringId);
    }
}