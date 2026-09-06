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

/**
 * 查询缓存装饰器（基于 JDK 动态代理，后端无关）。
 * <p>用一层代理包裹任意 {@link SqlBeanService} 实现，无需为每个后端写重复代码、也不侵入执行层：
 * <ul>
 *   <li>查询方法（select、count 等）：以「SQL + 租户 + 动态 schema + 分页 + 返回类型」为键查缓存；
 *       命中返回深拷贝副本，未命中执行原方法并回填（同样存副本）。</li>
 *   <li>写方法（insert、update、delete）：执行原方法后按「表 + 租户」失效相关缓存项。</li>
 *   <li>其余方法（含分页 {@code paging}、ConditionHandle 等难以稳定构建 key 的入口）直接透传，不参与缓存。</li>
 * </ul>
 * 注意：缓存键强制包含租户与动态 schema，避免多租户串号。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public final class CacheableSqlBeanService {

    private CacheableSqlBeanService() {
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

    public static SqlBeanMeta resolveMeta(SqlBeanService<?, ?> delegate) {
        try {
            Method m = delegate.getClass().getMethod("getSqlBeanMeta");
            return (SqlBeanMeta) m.invoke(delegate);
        } catch (Exception e) {
            throw new IllegalStateException("无法获取 SqlBeanMeta，缓存装饰器无法构建缓存键", e);
        }
    }

    private static final class Handler implements InvocationHandler {
        private final SqlBeanService<?, ?> delegate;
        private final QueryCache cache;
        private final SqlBeanMeta meta;
        private final Class<?> beanClass;

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
                evictForWrite(args);
                return result;
            }
            QueryCacheKey key = buildKey(name, args);
            if (key != null) {
                Object cached = cache.get(key);
                if (cached != null) {
                    return BeanCopier.copyValue(cached);
                }
                Object result = method.invoke(delegate, args);
                cache.put(key, BeanCopier.copyValue(result), tableOf(args), TenantContextHolder.getTenantId());
                return result;
            }
            return method.invoke(delegate, args);
        }

        private boolean isWrite(String name) {
            return name.startsWith("insert") || name.startsWith("update") || name.startsWith("delete");
        }

        private void evictForWrite(Object[] args) {
            String table = tableOf(args);
            if (table != null) {
                cache.evictByTable(table, TenantContextHolder.getTenantId());
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
            return new QueryCacheKey(beanClass, returnType, sql, String.valueOf(tenant), schema, "");
        }
    }
}
