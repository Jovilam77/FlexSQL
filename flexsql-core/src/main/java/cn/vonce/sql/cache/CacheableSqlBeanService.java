package cn.vonce.sql.cache;

import cn.vonce.sql.bean.Delete;
import cn.vonce.sql.bean.Insert;
import cn.vonce.sql.bean.Paging;
import cn.vonce.sql.bean.Select;
import cn.vonce.sql.bean.Table;
import cn.vonce.sql.bean.Update;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.define.ConditionHandle;
import cn.vonce.sql.helper.Wrapper;
import cn.vonce.sql.provider.DynSchemaContextHolder;
import cn.vonce.sql.provider.SqlBeanProvider;
import cn.vonce.sql.provider.TenantContextHolder;
import cn.vonce.sql.service.SqlBeanService;
import cn.vonce.sql.uitls.SqlBeanUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 查询缓存装饰器（基于 JDK 动态代理，后端无关）。
 * <p>用一层代理包裹任意 {@link SqlBeanService} 实现，无需为每个后端写重复代码、也不侵入执行层：
 * <ul>
 *   <li>查询方法（select、count 等）：以「SQL + 租户 + 动态 schema + 数据源 + 分页 + 返回类型」为键查缓存；
 *       命中（含空结果）返回深拷贝副本，未命中执行原方法并回填（同样存副本，空结果亦缓存以防穿透）。</li>
 *   <li>写方法（insert、update、delete、copy、backup）：执行原方法后按「表 + 动态 schema + 租户 + 数据源」失效相关缓存项；
 *       copy/backup 的失效目标是其 {@code targetTableName}（而非源表）。</li>
 *   <li>其余方法（含分页 {@code paging}、ConditionHandle 等难以稳定构建 key 的入口）直接透传，不参与缓存。</li>
 * </ul>
 * 注意：缓存键强制包含租户、动态 schema 与数据源，避免多租户 / 动态 schema / 多数据源串号。
 * <p><b>一致性说明（cache-aside）</b>：写操作默认在事务<b>提交后</b>触发失效（由各框架注册 {@code CacheTransactionSynchronization}，
 * 如 Spring 的 TransactionSynchronizationManager、Solon 的 TranUtils）。这可避免「提交前失效 → 并发读回填旧值 → 提交后缓存仍是旧值」
 * 的脏数据窗口；若未注册事务钩子（或无事务上下文），则退化为写后立即失效。
 * 分布式场景（Redis）存在经典的 cache-aside 竞态（节点 A 回源尚未写回时节点 B 写入并失效，A 可能把旧值写回），
 * 由 TTL + 写时失效兜底，属于最终一致，业务不应依赖毫秒级强一致。</p>
 *
 * @author Jovi
 * @version 1.1
 */
public final class CacheableSqlBeanService {

    private CacheableSqlBeanService() {
    }

    /**
     * 当前数据源名解析器（可插拔）。core 不依赖任何具体后端，由各框架在启动时注册：
     * 返回当前线程绑定的数据源名（多数据源 / @DbSwitch 场景），返回 null 表示默认 / 单一数据源。
     * 缓存键据此区分不同数据源，避免跨数据源串数据。
     */
    public interface DataSourceNameResolver {
        String currentDataSource();
    }

    /**
     * 事务同步钩子（可插拔）。core 不依赖 Spring / Solon 事务 API，由各框架注册；未注册时写操作立即失效。
     */
    public interface CacheTransactionSynchronization {
        /** 当前是否处于事务中 */
        boolean isActive();

        /** 在事务成功提交后执行（用于延迟缓存失效，避免提交前失效导致并发读回填旧值） */
        void executeAfterCommit(Runnable action);
    }

    private static volatile DataSourceNameResolver dataSourceResolver = () -> null;
    private static volatile CacheTransactionSynchronization transactionSynchronization = null;

    public static void setDataSourceResolver(DataSourceNameResolver resolver) {
        dataSourceResolver = resolver == null ? () -> null : resolver;
    }

    public static void setTransactionSynchronization(CacheTransactionSynchronization sync) {
        transactionSynchronization = sync;
    }

    @SuppressWarnings("unchecked")
    public static <T, ID> SqlBeanService<T, ID> wrap(SqlBeanService<T, ID> delegate, QueryCache cache) {
        if (delegate == null || cache == null) {
            return delegate;
        }
        Handler handler = new Handler(delegate, cache);
        // 代理 delegate 实现的所有接口（不仅是 SqlBeanService），以保留 AdvancedDbManageService 等
        // DDL/表结构方法；未匹配到缓存逻辑的方法会由 Handler 透传到真实 delegate。
        Class<?>[] declared = delegate.getClass().getInterfaces();
        boolean hasSqlBeanService = false;
        for (Class<?> i : declared) {
            if (SqlBeanService.class.equals(i)) {
                hasSqlBeanService = true;
                break;
            }
        }
        Class<?>[] interfaces = declared;
        if (!hasSqlBeanService) {
            interfaces = new Class<?>[declared.length + 1];
            System.arraycopy(declared, 0, interfaces, 0, declared.length);
            interfaces[declared.length] = SqlBeanService.class;
        }
        return (SqlBeanService<T, ID>) Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                interfaces,
                handler);
    }

    /**
     * 判断一个对象是否已经是本装饰器生成的缓存代理，避免重复包裹。
     */
    public static boolean isCacheProxy(Object bean) {
        return bean != null && Proxy.isProxyClass(bean.getClass())
                && Proxy.getInvocationHandler(bean) instanceof Handler;
    }

    public static SqlBeanMeta resolveMeta(SqlBeanService<?, ?> delegate) {
        try {
            Method m = delegate.getClass().getMethod("getSqlBeanMeta");
            return (SqlBeanMeta) m.invoke(delegate);
        } catch (Exception e) {
            throw new IllegalStateException("无法获取 SqlBeanMeta，缓存装饰器无法构建缓存键", e);
        }
    }

    /** 空结果哨兵：缓存 null 结果以防穿透（与「未命中」区分）。需可序列化以兼容 Redis 的 JDK 序列化。 */
    private static final class NullSentinel implements java.io.Serializable {
        static final NullSentinel INSTANCE = new NullSentinel();
        private static final long serialVersionUID = 1L;
    }

    private static Object toCacheValue(Object value) {
        return value == null ? NullSentinel.INSTANCE : BeanCopier.copyValue(value);
    }

    private static Object unwrap(Object cached) {
        if (cached instanceof NullSentinel) {
            return null;
        }
        return BeanCopier.copyValue(cached);
    }

    private static final class Handler implements InvocationHandler {
        private final SqlBeanService<?, ?> delegate;
        private final QueryCache cache;
        private final SqlBeanMeta meta;
        private final Class<?> beanClass;
        private final ConcurrentHashMap<QueryCacheKey, Object> loadLocks = new ConcurrentHashMap<>();

        Handler(SqlBeanService<?, ?> delegate, QueryCache cache) {
            this.delegate = delegate;
            this.cache = cache;
            this.beanClass = delegate.getBeanClass();
            this.meta = CacheableSqlBeanService.resolveMeta(delegate);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (isWrite(name)) {
                Object result = method.invoke(delegate, args);
                evictForWrite(args, name);
                return result;
            }
            QueryCacheKey key = buildKey(name, args);
            if (key != null) {
                Object cached = cache.get(key);
                if (cached != null) {
                    return unwrap(cached);
                }
                // 缓存击穿保护：同一 key 同一时刻仅一个线程回源，其余线程复用重建结果（Double-Checked Locking）
                Object lock = loadLocks.computeIfAbsent(key, k -> new Object());
                synchronized (lock) {
                    Object re = cache.get(key);
                    if (re != null) {
                        return unwrap(re);
                    }
                    Object result = method.invoke(delegate, args);
                    cache.put(key, toCacheValue(result), tableOf(args), TenantContextHolder.getTenantId());
                    return result;
                }
            }
            return method.invoke(delegate, args);
        }

        private boolean isWrite(String name) {
            if (name.startsWith("insert") || name.startsWith("update") || name.startsWith("delete")) {
                return true;
            }
            // copy/backup 会写目标表，必须触发失效（避免目标表被缓存读到旧值）
            return "copy".equals(name) || "backup".equals(name);
        }

        private void evictForWrite(Object[] args, String name) {
            Object tenant = TenantContextHolder.getTenantId();
            String dataSource = dataSourceResolver.currentDataSource();
            if ("copy".equals(name) || "backup".equals(name)) {
                // 目标表名是最后一个 String 参数；若存在两个 String，则前一个是 targetSchema
                List<String> strs = new ArrayList<>();
                if (args != null) {
                    for (Object a : args) {
                        if (a instanceof String) {
                            strs.add((String) a);
                        }
                    }
                }
                if (strs.isEmpty()) {
                    // 无参 backup() 目标表名在内部自动生成（<table>_时间戳），无法确定，跳过失效；
                    // 源表未被写入，无需失效。
                    return;
                }
                String table = strs.get(strs.size() - 1);
                String schema = strs.size() >= 2 ? strs.get(strs.size() - 2) : DynSchemaContextHolder.getSchema();
                deferEviction(table, schema, tenant, dataSource);
                return;
            }
            String table = tableOf(args);
            if (table != null) {
                deferEviction(table, DynSchemaContextHolder.getSchema(), tenant, dataSource);
            }
        }

        /**
         * 触发按表失效。若当前处于事务中，则将失效延迟到事务成功提交后执行（避免提交前失效导致并发读回填旧值）；
         * 否则（无事务钩子 / 不在事务中）立即失效。
         */
        private void deferEviction(String table, String schema, Object tenantId, String dataSource) {
            Runnable evict = () -> cache.evictByTable(table, schema, tenantId, dataSource);
            CacheTransactionSynchronization tx = CacheableSqlBeanService.transactionSynchronization;
            if (tx != null && tx.isActive()) {
                tx.executeAfterCommit(evict);
            } else {
                evict.run();
            }
        }

        private String tableOf(Object[] args) {
            if (args != null && args.length > 0) {
                Object first = args[0];
                if (first instanceof Insert || first instanceof Update || first instanceof Delete) {
                    Class<?> bc = ((cn.vonce.sql.bean.Common) first).getBeanClass();
                    if (bc != null) {
                        return SqlBeanUtil.getTable(bc).getName();
                    }
                }
            }
            Table table = SqlBeanUtil.getTable(beanClass);
            return table == null ? null : table.getName();
        }

        private QueryCacheKey buildKey(String name, Object[] args) {
            if (args == null) {
                args = new Object[0];
            }
            Object tenant = TenantContextHolder.getTenantId();
            String schema = DynSchemaContextHolder.getSchema();
            String dataSource = dataSourceResolver.currentDataSource();
            Class<?> returnType = null;
            String where = null;
            Object[] sqlArgs = null;
            Select select = null;
            Paging paging = null;
            boolean hasWrapper = false;
            boolean hasCondition = false;
            for (Object a : args) {
                if (a instanceof Wrapper) {
                    hasWrapper = true;
                } else if (a instanceof ConditionHandle) {
                    hasCondition = true;
                } else if (a instanceof Paging) {
                    paging = (Paging) a;
                }
            }
            // ConditionHandle 无法稳定构建 key，跳过缓存
            if (hasCondition) {
                return null;
            }
            // Wrapper/Paging 组合无法嵌入分页，跳过缓存
            if (paging != null && hasWrapper) {
                return null;
            }

            String sql;
            switch (name) {
                case "selectById":
                    returnType = args.length == 2 ? (Class<?>) args[0] : null;
                    sql = SqlBeanProvider.selectByIdSql(meta, beanClass, returnType, args.length == 2 ? args[1] : args[0]);
                    break;
                case "selectByIds":
                    returnType = args.length == 2 ? (Class<?>) args[0] : null;
                    sql = SqlBeanProvider.selectByIdsSql(meta, beanClass, returnType,
                            (Object[]) (args.length == 2 ? args[1] : args[0]));
                    break;
                case "selectOne":
                    if (args.length == 2) {
                        returnType = (Class<?>) args[0];
                        select = (Select) args[1];
                    } else {
                        select = (Select) args[0];
                    }
                    sql = SqlBeanProvider.selectSql(meta, beanClass, returnType, select);
                    break;
                case "selectMap":
                    select = (Select) args[0];
                    sql = SqlBeanProvider.selectSql(meta, beanClass, null, select);
                    break;
                case "selectOneBy":
                    if (hasWrapper) {
                        if (args.length == 2) {
                            returnType = (Class<?>) args[0];
                        }
                        select = new Select();
                        select.where((Wrapper) (args.length == 2 ? args[1] : args[0]));
                        sql = SqlBeanProvider.selectSql(meta, beanClass, returnType, select);
                    } else {
                        if (args.length == 3) {
                            returnType = (Class<?>) args[0];
                            where = (String) args[1];
                            sqlArgs = (Object[]) args[2];
                        } else {
                            where = (String) args[0];
                            sqlArgs = (Object[]) args[1];
                        }
                        sql = SqlBeanProvider.selectBySql(meta, beanClass, returnType, null, where, sqlArgs);
                    }
                    break;
                case "selectBy":
                    if (hasWrapper) {
                        if (args.length == 2) {
                            returnType = (Class<?>) args[0];
                        }
                        select = new Select();
                        select.where((Wrapper) (args.length == 2 ? args[1] : args[0]));
                        sql = SqlBeanProvider.selectSql(meta, beanClass, returnType, select);
                    } else {
                        // (String,args) / (Class,String,args) / (Paging,String,args) / (Class,Paging,String,args)
                        if (args.length == 2) {
                            where = (String) args[0];
                            sqlArgs = (Object[]) args[1];
                        } else if (args.length == 3) {
                            if (args[0] instanceof Class) {
                                returnType = (Class<?>) args[0];
                                where = (String) args[1];
                                sqlArgs = (Object[]) args[2];
                            } else {
                                paging = (Paging) args[0];
                                where = (String) args[1];
                                sqlArgs = (Object[]) args[2];
                            }
                        } else if (args.length == 4) {
                            returnType = (Class<?>) args[0];
                            paging = (Paging) args[1];
                            where = (String) args[2];
                            sqlArgs = (Object[]) args[3];
                        } else {
                            return null;
                        }
                        sql = SqlBeanProvider.selectBySql(meta, beanClass, returnType, paging, where, sqlArgs);
                    }
                    break;
                case "countBy":
                    if (hasWrapper) {
                        select = new Select();
                        select.where((Wrapper) args[0]);
                        sql = SqlBeanProvider.selectSql(meta, beanClass, null, select);
                    } else {
                        where = (String) args[0];
                        sqlArgs = (Object[]) args[1];
                        sql = SqlBeanProvider.countBySql(meta, beanClass, where, sqlArgs);
                    }
                    break;
                case "count":
                    if (args.length == 0) {
                        sql = SqlBeanProvider.selectAllSql(meta, beanClass, null, null);
                    } else if (args.length == 1 && args[0] instanceof Select) {
                        select = (Select) args[0];
                        sql = SqlBeanProvider.countSql(meta, beanClass, null, select);
                    } else if (args.length == 2) {
                        returnType = (Class<?>) args[0];
                        select = (Select) args[1];
                        sql = SqlBeanProvider.countSql(meta, beanClass, returnType, select);
                    } else {
                        return null;
                    }
                    break;
                case "select":
                    if (args.length == 0) {
                        sql = SqlBeanProvider.selectAllSql(meta, beanClass, null, null);
                    } else if (args.length == 1 && args[0] instanceof Class) {
                        returnType = (Class<?>) args[0];
                        sql = SqlBeanProvider.selectAllSql(meta, beanClass, returnType, null);
                    } else if (args.length == 1 && args[0] instanceof Paging) {
                        sql = SqlBeanProvider.selectAllSql(meta, beanClass, null, (Paging) args[0]);
                    } else if (args.length == 2 && args[0] instanceof Class) {
                        returnType = (Class<?>) args[0];
                        sql = SqlBeanProvider.selectAllSql(meta, beanClass, returnType, (Paging) args[1]);
                    } else if (args.length == 1 && args[0] instanceof Select) {
                        select = (Select) args[0];
                        sql = SqlBeanProvider.selectSql(meta, beanClass, null, select);
                    } else if (args.length == 2 && args[0] instanceof Class && args[1] instanceof Select) {
                        returnType = (Class<?>) args[0];
                        select = (Select) args[1];
                        sql = SqlBeanProvider.selectSql(meta, beanClass, returnType, select);
                    } else {
                        return null;
                    }
                    break;
                case "selectMapList":
                    select = (Select) args[0];
                    sql = SqlBeanProvider.selectSql(meta, beanClass, null, select);
                    break;
                default:
                    // paging(...) 等不参与缓存
                    return null;
            }
            return new QueryCacheKey(beanClass, returnType, sql, String.valueOf(tenant), schema, "", dataSource);
        }
    }
}
