package cn.vonce.sql.java.mapper;

import cn.vonce.sql.bean.ColumnInfo;
import cn.vonce.sql.bean.TableInfo;
import cn.vonce.sql.java.dao.MybatisSqlBeanDao;
import cn.vonce.sql.mapper.ResultSetDelegate;
import cn.vonce.sql.mapper.SqlBeanMapper;
import cn.vonce.sql.uitls.ReflectUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.plugin.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.logging.Logger;

/**
 * Mybatis 结果映射拦截器
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2018年5月15日上午9:21:48
 */
@Intercepts(@Signature(method = "handleResultSets", type = ResultSetHandler.class, args = {Statement.class}))
public class MybatisSqlBeanMapperInterceptor extends SqlBeanMapper implements Interceptor {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 获取代理目标对象
        Object target = invocation.getTarget();
        if (target instanceof DefaultResultSetHandler) {
            DefaultResultSetHandler resultSetHandler = (DefaultResultSetHandler) target;
            // 利用反射获取参数对象
            ParameterHandler parameterHandler = ReflectUtil.getFieldValue(resultSetHandler, "parameterHandler");
            MappedStatement mappedStatement = ReflectUtil.getFieldValue(resultSetHandler, "mappedStatement");
            if (mappedStatement.getId().startsWith(MybatisSqlBeanDao.class.getName())) {
                Object parameterObj = parameterHandler.getParameterObject();
                // 获取节点属性的集合
                List<ResultMap> resultMaps = mappedStatement.getResultMaps();
                ResultSetDelegate<ResultSet> resultSetDelegate = new ResultSetDelegate<>(((Statement) invocation.getArgs()[0]).getResultSet());
                return handleResultSet(resultSetDelegate, ((HashMap<String, Object>) parameterObj), resultMaps.get(0).getType());
            }
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // TODO Auto-generated method stub

    }

    /**
     * 对象映射处理
     *
     * @param resultSetDelegate 获取当前结果集
     * @param mapParam          获取当前dao方法参数map
     * @param resultType        获取当前dao方法resultType的类型
     * @return
     */
    private Object handleResultSet(ResultSetDelegate<ResultSet> resultSetDelegate, Map<String, Object> mapParam, Class<?> resultType) {
        if (null != resultSetDelegate.getDelegate()) {
            if (resultType == ColumnInfo.class || resultType == TableInfo.class) {
                return beanHandleResultSet(resultType, resultSetDelegate, super.getColumnNameList(resultSetDelegate));
            }
            if (SqlBeanUtil.isBaseType(resultType)) {
                return baseHandleResultSet(resultSetDelegate, resultType);
            }
            if (SqlBeanUtil.isMap(resultType)) {
                return mapHandleResultSet(resultSetDelegate);
            }
            // 获取实际需要返回的类型
            Class<?> returnType;
            if (mapParam.containsKey("returnType")) {
                returnType = (Class<?>) mapParam.get("returnType");
            } else {
                returnType = (Class<?>) mapParam.get("clazz");
            }
            if (SqlBeanUtil.isBaseType(returnType)) {
                return baseHandleResultSet(resultSetDelegate, returnType);
            }
            if (SqlBeanUtil.isMap(returnType)) {
                return mapHandleResultSet(resultSetDelegate);
            }
            return beanHandleResultSet(returnType, resultSetDelegate, super.getColumnNameList(resultSetDelegate));
        }
        return null;
    }

    /**
     * bean对象映射处理
     *
     * @param resultSetDelegate
     * @param clazz
     * @return
     */
    public List<Object> beanHandleResultSet(Class<?> clazz, ResultSetDelegate<ResultSet> resultSetDelegate, List<String> columnNameList) {
        List<Object> resultList = new ArrayList<>();
        if (null != resultSetDelegate.getDelegate()) {
            try {
                while (resultSetDelegate.getDelegate().next()) {
                    resultList.add(super.beanHandleResultSet(clazz, resultSetDelegate, columnNameList));
                }
            } catch (SQLException e) {
                logger.warning("Failed to process bean result set: " + e.getMessage());
            } finally {
                closeResultSet(resultSetDelegate);
            }
        }
        return resultList;
    }

    /**
     * map对象映射
     *
     * @param resultSetDelegate
     * @return
     */
    public List<Object> mapHandleResultSet(ResultSetDelegate<ResultSet> resultSetDelegate) {
        List<Object> resultList = new ArrayList<>();
        if (null != resultSetDelegate.getDelegate()) {
            try {
                while (resultSetDelegate.getDelegate().next()) {
                    resultList.add(super.mapHandleResultSet(resultSetDelegate));
                }
            } catch (SQLException e) {
                logger.severe(String.format("map对象映射异常SQLException, %s", e.getMessage()));
            } finally {
                // 关闭result set
                closeResultSet(resultSetDelegate);
            }
        }
        return resultList;
    }

    /**
     * 基础对象映射
     *
     * @param resultSetDelegate
     * @return
     */
    public List<Object> baseHandleResultSet(ResultSetDelegate<ResultSet> resultSetDelegate, Class<?> returnType) {
        List<Object> resultList = new ArrayList<>();
        if (null != resultSetDelegate.getDelegate()) {
            try {
                while (resultSetDelegate.getDelegate().next()) {
                    Object value = super.baseHandleResultSet(resultSetDelegate);
                    if (value != null && value.getClass() != returnType) {
                        value = SqlBeanUtil.getValueConvert(returnType, value);
                    }
                    resultList.add(value);
                }
            } catch (SQLException e) {
                logger.severe(String.format("基础对象映射异常SQLException, %s", e.getMessage()));
            } finally {
                // 关闭result set
                closeResultSet(resultSetDelegate);
            }
        }
        return resultList;
    }

    /**
     * 关闭ResultSet
     *
     * @param resultSetDelegate
     */
    private void closeResultSet(ResultSetDelegate<ResultSet> resultSetDelegate) {
        try {
            if (resultSetDelegate.getDelegate() != null) {
                resultSetDelegate.getDelegate().close();
            }
        } catch (SQLException e) {
            logger.severe(String.format("关闭 result set异常, %s", e.getMessage()));
        }
    }

}
