package cn.vonce.sql.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 零依赖的本地查询缓存默认实现（不依赖 Caffeine）。
 * <p>使用带访问顺序的 {@link LinkedHashMap} 实现容量上限（LRU 淘汰）+ TTL 过期定时清理。
 * 当前作为 FlexSQL 查询缓存的内置默认后端；若后续运行时存在 Caffeine，可新增对应实现并在工厂中优先选用。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public class SimpleQueryCache implements QueryCache {

    private final long maximumSize;
    private final long ttlMillis;
    private final Map<QueryCacheKey, Entry> store;
    private final Map<String, Set<QueryCacheKey>> index = new ConcurrentHashMap<>();
    private final Map<QueryCacheKey, String> compositeOf = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sweeper;

    @SuppressWarnings("serial")
    public SimpleQueryCache(long maximumSize, long expireAfterWriteSeconds) {
        this.maximumSize = Math.max(1, maximumSize);
        this.ttlMillis = expireAfterWriteSeconds * 1000L;
        this.store = Collections.synchronizedMap(new LinkedHashMap<QueryCacheKey, Entry>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<QueryCacheKey, Entry> eldest) {
                return size() > SimpleQueryCache.this.maximumSize;
            }
        });
        this.sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "flexsql-query-cache-sweeper");
            t.setDaemon(true);
            return t;
        });
        if (ttlMillis > 0) {
            this.sweeper.scheduleAtFixedRate(this::sweep, 30, 30, TimeUnit.SECONDS);
        }
    }

    private static final class Entry {
        final Object value;
        final long expireAt;

        Entry(Object value, long ttlMillis) {
            this.value = value;
            this.expireAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : Long.MAX_VALUE;
        }

        boolean expired() {
            return System.currentTimeMillis() >= expireAt;
        }
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        for (Map.Entry<QueryCacheKey, Entry> e : new ArrayList<>(store.entrySet())) {
            if (e.getValue().expireAt <= now) {
                remove(e.getKey());
            }
        }
    }

    @Override
    public Object get(QueryCacheKey key) {
        Entry e = store.get(key);
        if (e == null) {
            return null;
        }
        if (e.expired()) {
            remove(key);
            return null;
        }
        return e.value;
    }

    @Override
    public void put(QueryCacheKey key, Object value, String table, Object tenantId) {
        String composite = table + "@" + (tenantId == null ? "" : tenantId);
        compositeOf.put(key, composite);
        index.computeIfAbsent(composite, k -> ConcurrentHashMap.newKeySet()).add(key);
        store.put(key, new Entry(value, ttlMillis));
    }

    @Override
    public void evictByTable(String table, Object tenantId) {
        if (table == null) {
            return;
        }
        String composite = table + "@" + (tenantId == null ? "" : tenantId);
        Set<QueryCacheKey> set = index.get(composite);
        if (set != null) {
            for (QueryCacheKey key : new ArrayList<>(set)) {
                remove(key);
            }
        }
    }

    private void remove(QueryCacheKey key) {
        store.remove(key);
        String composite = compositeOf.remove(key);
        if (composite != null) {
            Set<QueryCacheKey> set = index.get(composite);
            if (set != null) {
                set.remove(key);
                if (set.isEmpty()) {
                    index.remove(composite, set);
                }
            }
        }
    }

    @Override
    public void clear() {
        store.clear();
        index.clear();
        compositeOf.clear();
    }
}
