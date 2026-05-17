package cn.vonce.sql.mapper;

import cn.vonce.sql.annotation.SqlJoin;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.define.SqlEnum;
import cn.vonce.sql.uitls.ReflectUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * 结果映射基类
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2023/6/30 15:23
 */
public abstract class BaseMapper<T> {

    /**
     * 获取所有列名
     *
     * @param resultSetDelegate
     * @return
     */
    public abstract List<String> getColumnNameList(ResultSetDelegate<T> resultSetDelegate);

    /**
     * 基础对象映射
     *
     * @param resultSetDelegate
     * @return
     */
    public abstract Object baseHandleResultSet(ResultSetDelegate<T> resultSetDelegate);

    /**
     * map对象映射
     *
     * @param resultSetDelegate
     * @return
     */
    public abstract Object mapHandleResultSet(ResultSetDelegate<T> resultSetDelegate);

    /**
     * bean对象映射处理
     *
     * @param clazz
     * @param resultSetDelegate
     * @param columnNameList
     * @return
     */
    public Object beanHandleResultSet(Class<?> clazz, ResultSetDelegate<T> resultSetDelegate, List<String> columnNameList) {
        Object bean = ReflectUtil.instance().newObject(clazz);
        String tableAlias = SqlBeanUtil.getTable(clazz).getAlias();
        List<Field> fieldList = SqlBeanUtil.getBeanAllField(clazz);
        for (Field field : fieldList) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            SqlJoin sqlJoin = field.getAnnotation(SqlJoin.class);
            String fieldName = field.getName();
            if (sqlJoin != null) {
                boolean isBean = !SqlBeanUtil.isBaseType(field.getType());
                if (isBean) {
                    Class<?> subClazz = field.getType();
                    Object subBean = ReflectUtil.instance().newObject(subClazz);
                    //获取表的别名，先是获取别名，获取不到就会获取表名
                    String subTableAlias = SqlBeanUtil.getTable(subClazz).getAlias();
                    //如果在SqlBeanJoin中设置了表名，那么优先使用该表名，如果有多个联表查询的对象需要连接同一张表的，那么需要保证表名一致
                    if (StringUtil.isNotEmpty(sqlJoin.table())) {
                        subTableAlias = sqlJoin.table();
                    }
                    //如果在SqlBeanJoin中设置了别名，那么优先使用该别名，如果有多个联表查询的对象需要连接同一张表的，那么需要保证别名一致
                    if (StringUtil.isNotEmpty(sqlJoin.tableAlias())) {
                        subTableAlias = sqlJoin.tableAlias();
                    }
                    List<Field> subFields = SqlBeanUtil.getBeanAllField(subClazz);
                    for (Field subField : subFields) {
                        if (Modifier.isStatic(subField.getModifiers())) {
                            continue;
                        }
                        String subFieldName = subField.getName();
                        subFieldName = subTableAlias + SqlConstant.UNDERLINE + subFieldName;
                        setFieldValue(subBean, subField, subFieldName, resultSetDelegate);
                    }
                    ReflectUtil.instance().set(bean.getClass(), bean, fieldName, subBean);
                    continue;
                } else {
                    if (!columnNameList.contains(fieldName)) {
                        String subTableAlias = sqlJoin.table();
                        if (StringUtil.isNotEmpty(sqlJoin.tableAlias())) {
                            subTableAlias = sqlJoin.tableAlias();
                        }
                        fieldName = subTableAlias + SqlConstant.UNDERLINE + fieldName;
                    }
                    if (columnNameList.contains(fieldName)) {
                        setFieldValue(bean, field, fieldName, resultSetDelegate);
                    }
                }
            } else {
                //优先使用 表别名+字段名方式匹配
                String newFieldName = tableAlias + SqlConstant.UNDERLINE + fieldName;
                if (!columnNameList.contains(newFieldName)) {
                    //其次通过驼峰转下划线方式匹配
                    newFieldName = StringUtil.humpToUnderline(fieldName);
                    if (!columnNameList.contains(newFieldName)) {
                        newFieldName = newFieldName.toUpperCase();
                        if (!columnNameList.contains(newFieldName)) {
                            //再其次通过字段名匹配
                            newFieldName = fieldName;
                            if (!columnNameList.contains(newFieldName)) {
                                newFieldName = fieldName.toUpperCase();
                            }
                        }
                    }
                }
                if (columnNameList.contains(newFieldName)) {
                    setFieldValue(bean, field, newFieldName, resultSetDelegate);
                }
            }
        }
        return bean;
    }

    /**
     * 字段赋值
     *
     * @param obj
     * @param field
     * @param columnName
     * @param resultSetDelegate
     */
    public void setFieldValue(Object obj, Field field, String columnName, ResultSetDelegate<T> resultSetDelegate) {
        Object value = getValue(field, columnName, resultSetDelegate);
        if (value == null) {
            value = SqlBeanUtil.getDefaultValue(field.getType());
        }
        if (SqlEnum.class.isAssignableFrom(field.getType())) {
            ReflectUtil.instance().set(obj.getClass(), obj, field.getName(), SqlBeanUtil.matchEnum(field, value));
        } else {
            ReflectUtil.instance().set(obj.getClass(), obj, field.getName(), value);
        }
    }

    /**
     * 获取该字段对应的值
     *
     * @param field
     * @param columnName
     * @param resultSetDelegate
     * @return
     */
    public abstract Object getValue(Field field, String columnName, ResultSetDelegate<T> resultSetDelegate);

    /**
     * 获取该字段对应的值
     *
     * @param jdbcType
     * @param index
     * @param resultSetDelegate
     * @return
     */
    public abstract Object getValue(String jdbcType, int index, ResultSetDelegate<T> resultSetDelegate);

    /**
     * 获取基本类型默认值
     *
     * @param typeName
     * @return
     */
    public Object getDefaultValueByColumnType(String typeName) {
        Object value;
        switch (typeName) {
            case "BIGINT":
            case "INTEGER":
            case "TINYINT":
            case "SMALLINT":
                value = 0;
                break;
            case "FLOAT":
                value = 0f;
                break;
            case "DOUBLE":
            case "NUMERIC":
                value = 0d;
                break;
            case "BIT":
                value = false;
                break;
            case "CHAR":
            case "VARCHAR":
            case "LONGVARCHAR":
                value = null;
                break;
            default:
                value = null;
                break;
        }
        return value;
    }

}
