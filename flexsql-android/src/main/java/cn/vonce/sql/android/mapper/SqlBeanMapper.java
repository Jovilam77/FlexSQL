package cn.vonce.sql.android.mapper;


import android.database.Cursor;
import cn.vonce.sql.annotation.SqlJSON;
import cn.vonce.sql.bean.ColumnInfo;
import cn.vonce.sql.bean.TableInfo;
import cn.vonce.sql.json.JSONConvert;
import cn.vonce.sql.mapper.BaseMapper;
import cn.vonce.sql.mapper.ResultSetDelegate;
import cn.vonce.sql.uitls.DateUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * SqlBean 结果映射
 *
 * @author Jovi
 */
public class SqlBeanMapper<T> extends BaseMapper<Cursor> implements RowMapper<T> {

    private static final Logger logger = Logger.getLogger(SqlBeanMapper.class.getName());

    public Class<?> clazz;
    public Class<?> returnType;

    public SqlBeanMapper(Class<?> clazz, Class<?> returnType) {
        this.clazz = clazz;
        this.returnType = returnType;
    }

    @Override
    public List<String> getColumnNameList(ResultSetDelegate<Cursor> resultSetDelegate) {
        Cursor cursor = resultSetDelegate.getDelegate();
        return Arrays.asList(cursor.getColumnNames());
    }

    @Override
    public T mapRow(ResultSetDelegate<Cursor> resultSetDelegate, int index) {
        Object object = null;
        if (resultSetDelegate.getDelegate().moveToNext()) {
            if (returnType.getName().equals(ColumnInfo.class.getName()) || returnType.getName().equals(TableInfo.class.getName())) {
                return (T) beanHandleResultSet(returnType, resultSetDelegate, getColumnNameList(resultSetDelegate));
            }
            if (SqlBeanUtil.isBaseType(returnType)) {
                return (T) baseHandleResultSet(resultSetDelegate);
            }
            if (SqlBeanUtil.isMap(returnType)) {
                return (T) mapHandleResultSet(resultSetDelegate);
            }
            return (T) beanHandleResultSet(returnType, resultSetDelegate, getColumnNameList(resultSetDelegate));
        }
        return (T) object;
    }

    @Override
    public Object baseHandleResultSet(ResultSetDelegate<Cursor> resultSetDelegate) {
        Cursor cursor = resultSetDelegate.getDelegate();
        Object value;
        value = getValue(cursor.getType(0), 0, cursor);
        if (value != null && !value.getClass().getName().equals(returnType.getName())) {
            value = SqlBeanUtil.getValueConvert(returnType, value);
        }
        if (value == null || value.equals("null")) {
            value = getDefaultValueByColumnType(cursor.getType(0));
        }
        return value;
    }

    @Override
    public Object mapHandleResultSet(ResultSetDelegate<Cursor> resultSetDelegate) {
        Cursor cursor = resultSetDelegate.getDelegate();
        Map<String, Object> map = new HashMap<>();
        for (int i = 1; i < cursor.getColumnCount(); i++) {
            Object value = getValue(cursor.getType(i), i, cursor);
            if (value == null || value.equals("null")) {
                value = getDefaultValueByColumnType(cursor.getType(i));
            }
            map.put(cursor.getColumnName(i), value);
        }
        return map;
    }

    @Override
    public Object getValue(Field field, String columnName, ResultSetDelegate<Cursor> resultSetDelegate) {
        Cursor cursor = resultSetDelegate.getDelegate();
        Object value = null;
        int index = cursor.getColumnIndex(columnName);
        if (index == -1) {
            return null;
        }
        try {
            SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
            if (sqlJSON != null && sqlJSON.convert() != JSONConvert.class) {
                String json = cursor.getString(index);
                return SqlBeanUtil.convertJSON(sqlJSON.convert().newInstance(), json, field);
            }
            switch (field.getType().getName()) {
                case "byte":
                case "java.lang.Byte":
                    value = Byte.parseByte(cursor.getShort(index) + "");
                    break;
                case "short":
                case "java.lang.Short":
                    value = cursor.getShort(index);
                    break;
                case "int":
                case "java.lang.Integer":
                    value = cursor.getInt(index);
                    break;
                case "float":
                case "java.lang.Float":
                    value = cursor.getFloat(index);
                    break;
                case "double":
                case "java.lang.Double":
                    value = cursor.getDouble(index);
                    break;
                case "long":
                case "java.lang.Long":
                    value = cursor.getLong(index);
                    break;
                case "boolean":
                case "java.lang.Boolean":
                    short bool = cursor.getShort(index);
                    if (bool > 0) {
                        value = true;
                    } else {
                        value = false;
                    }
                    break;
                case "char":
                case "java.lang.Character":
                    value = cursor.getString(index);
                    if (StringUtil.isNotEmpty(value)) {
                        value = value.toString().charAt(0);
                    }
                    break;
                case "java.lang.String":
                    value = cursor.getString(index);
                    break;
                case "java.sql.Date":
                case "java.sql.Time":
                case "java.sql.Timestamp":
                case "java.util.Date":
                    //先取得long类型，转String之后如果长度是10或13那么则为时间戳
                    long timestamp = cursor.getLong(index);
                    String stringTimestamp = timestamp + "";
                    if (stringTimestamp.length() == 10 || stringTimestamp.length() == 13) {
                        value = new java.util.Date(timestamp);
                    } else {
                        value = DateUtil.stringToDate(cursor.getString(index));
                    }
                    break;
                case "java.math.BigDecimal":
                    value = new BigDecimal(cursor.getDouble(index));
                    break;
                default:
                    value = cursor.getBlob(index);
                    break;
            }
        } catch (Exception e) {
            logger.warning("Failed to get value from field '" + field.getName() + "': " + e.getMessage());
        }
        return value;
    }

    @Override
    public Object getValue(String jdbcType, int index, ResultSetDelegate<Cursor> resultSetDelegate) {
        return null;
    }

    /**
     * 获取该字段对应的值
     *
     * @param jdbcType
     * @param index
     * @param cursor
     * @return
     */
    public Object getValue(int jdbcType, int index, Cursor cursor) {
        Object value = null;
        switch (jdbcType) {
            case Cursor.FIELD_TYPE_INTEGER:
                value = cursor.getLong(index);
                break;
            case Cursor.FIELD_TYPE_FLOAT:
                value = cursor.getDouble(index);
                break;
            case Cursor.FIELD_TYPE_STRING:
                value = cursor.getString(index);
                break;
            case Cursor.FIELD_TYPE_BLOB:
                value = cursor.getBlob(index);
                break;
        }
        return value;
    }

    /**
     * 获取基本类型默认值
     *
     * @param jdbcType
     * @return
     */
    public Object getDefaultValueByColumnType(int jdbcType) {
        Object value = null;
        switch (jdbcType) {
            case Cursor.FIELD_TYPE_INTEGER:
                value = 0;
                break;
            case Cursor.FIELD_TYPE_FLOAT:
                value = 0f;
                break;
            case Cursor.FIELD_TYPE_STRING:
            case Cursor.FIELD_TYPE_BLOB:
                value = null;
                break;
        }
        return value;
    }

}
