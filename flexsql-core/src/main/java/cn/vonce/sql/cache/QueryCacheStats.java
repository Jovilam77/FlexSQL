package cn.vonce.sql.cache;

/**
 * 查询缓存统计快照（不可变）。
 * <p>由 {@link CacheMetrics#snapshot()} / {@link CacheMetrics#snapshotByTable()} 产出。
 * 统计口径（与常见缓存指标略有差异，此处以「判断缓存是否值得开启」为目标）：
 * <ul>
 *   <li><b>命中 hit</b>：查询时<b>首次</b>读取缓存即取到值。</li>
 *   <li><b>未命中 miss</b>：首次读取缓存未取到值（随后要么自己回源，要么等待并发回源结果）。</li>
 *   <li><b>回源 load</b>：真正落到数据库执行的查询次数。并发下 N 个 miss 只会回源 1 次，
 *       因此 {@code miss - load} 的差值即为「并发击穿保护复用」的次数，差值大说明热点 key 集中。</li>
 *   <li><b>回填 put</b>：回源后写入缓存的次数。</li>
 *   <li><b>失效 evict</b>：写操作触发的失效次数（按「表 + schema + 租户 + 数据源」计一次，非条目数）。</li>
 * </ul>
 * 命中率 {@code hitRate = hit / (hit + miss)}；该值长期偏低（如 &lt; 30%）说明缓存收益不足以抵消
 * 深拷贝与内存开销，应当关闭或调整缓存对象。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public final class QueryCacheStats {

    private final long hitCount;
    private final long missCount;
    private final long loadCount;
    private final long putCount;
    private final long evictCount;

    QueryCacheStats(long hitCount, long missCount, long loadCount, long putCount, long evictCount) {
        this.hitCount = hitCount;
        this.missCount = missCount;
        this.loadCount = loadCount;
        this.putCount = putCount;
        this.evictCount = evictCount;
    }

    /** 命中次数（首次读取缓存即取到值） */
    public long getHitCount() {
        return hitCount;
    }

    /** 未命中次数（首次读取缓存未取到值） */
    public long getMissCount() {
        return missCount;
    }

    /** 回源次数（真正执行数据库查询） */
    public long getLoadCount() {
        return loadCount;
    }

    /** 回填次数 */
    public long getPutCount() {
        return putCount;
    }

    /** 失效触发次数（按表计，非条目数） */
    public long getEvictCount() {
        return evictCount;
    }

    /** 总查询次数（命中 + 未命中） */
    public long getTotalCount() {
        return hitCount + missCount;
    }

    /** 命中率，0~1；无查询时返回 0 */
    public double getHitRate() {
        long total = getTotalCount();
        return total == 0 ? 0D : (double) hitCount / (double) total;
    }

    @Override
    public String toString() {
        return "QueryCacheStats{hit=" + hitCount
                + ", miss=" + missCount
                + ", load=" + loadCount
                + ", put=" + putCount
                + ", evict=" + evictCount
                + ", hitRate=" + String.format("%.1f%%", getHitRate() * 100D) + "}";
    }
}
