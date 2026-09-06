package cn.vonce.sql.config;

import cn.vonce.sql.cache.QueryCache;
import cn.vonce.sql.enumerate.JsonType;
import cn.vonce.sql.processor.DefaultUniqueIdProcessor;
import cn.vonce.sql.processor.UniqueIdProcessor;

import java.io.Serializable;

/**
 * SqlBean配置
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
            this.autoCreate = autoCreate;
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

    // ===== 查询缓存配置（默认关闭，开启后使用内置 SimpleQueryCache 本地缓存，可注入自定义 QueryCache / RedisQueryCache）=====

    private Boolean queryCacheEnabled;
    private QueryCache queryCache;
    private Long queryCacheMaxSize = 1000L;
    private Long queryCacheExpireAfterWriteSeconds = 600L;
    private Long queryCacheExpireAfterAccessSeconds = 0L;

    public boolean getQueryCacheEnabled() {
        return queryCacheEnabled != null && queryCacheEnabled;
    }

    public void setQueryCacheEnabled(boolean queryCacheEnabled) {
        this.queryCacheEnabled = queryCacheEnabled;
    }

    public QueryCache getQueryCache() {
        return queryCache;
    }

    public void setQueryCache(QueryCache queryCache) {
        this.queryCache = queryCache;
    }

    public long getQueryCacheMaxSize() {
        if (queryCacheMaxSize == null) {
            queryCacheMaxSize = 1000L;
        }
        return queryCacheMaxSize;
    }

    public void setQueryCacheMaxSize(long queryCacheMaxSize) {
        this.queryCacheMaxSize = queryCacheMaxSize;
    }

    public long getQueryCacheExpireAfterWriteSeconds() {
        if (queryCacheExpireAfterWriteSeconds == null) {
            queryCacheExpireAfterWriteSeconds = 600L;
        }
        return queryCacheExpireAfterWriteSeconds;
    }

    public void setQueryCacheExpireAfterWriteSeconds(long queryCacheExpireAfterWriteSeconds) {
        this.queryCacheExpireAfterWriteSeconds = queryCacheExpireAfterWriteSeconds;
    }

    public long getQueryCacheExpireAfterAccessSeconds() {
        if (queryCacheExpireAfterAccessSeconds == null) {
            queryCacheExpireAfterAccessSeconds = 0L;
        }
        return queryCacheExpireAfterAccessSeconds;
    }

    public void setQueryCacheExpireAfterAccessSeconds(long queryCacheExpireAfterAccessSeconds) {
        this.queryCacheExpireAfterAccessSeconds = queryCacheExpireAfterAccessSeconds;
    }

}
