package cn.vonce.sql.mapper;


import cn.vonce.sql.annotation.SqlJSON;
import cn.vonce.sql.json.JSONConvert;
import cn.vonce.sql.uitls.DateUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;

/**
 * SqlBean 结果映射
 */
public class SqlBeanMapper extends BaseMapper<ResultSet> {

    private static final Logger logger = Logger.getLogger(SqlBeanMapper.class.getName());

    /**
     * 获取所有列名
     *
     * @param resultSetDelegate
     * @return
     * @throws SQLException
     */
    @Override
    public List<String> getColumnNameList(ResultSetDelegate<ResultSet> resultSetDelegate) {
        ResultSet resultSet = resultSetDelegate.getDelegate();
        List<String> columnNameList = new ArrayList<>();
        try {
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            int columns = resultSetMetaData.getColumnCount();
            for (int i = 1; i <= columns; i++) {
                columnNameList.add(resultSetMetaData.getColumnLabel(i));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return columnNameList;
    }

    @Override
    public Object baseHandleResultSet(ResultSetDelegate<ResultSet> resultSetDelegate) {
        ResultSet resultSet = resultSetDelegate.getDelegate();
        Object value = null;
        try {
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            value = getValue(resultSetMetaData.getColumnTypeName(1), 1, resultSetDelegate);
            if (value == null) {
                value = getDefaultValueByColumnType(resultSetMetaData.getColumnTypeName(1));
            }
        } catch (SQLException e) {
            logger.warning("Failed to get base value: " + e.getMessage());
        }
        return value;
    }

    @Override
    public Object mapHandleResultSet(ResultSetDelegate<ResultSet> resultSetDelegate) {
        ResultSet resultSet = resultSetDelegate.getDelegate();
        Map<String, Object> map = null;
        try {
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            int columns = resultSetMetaData.getColumnCount();
            map = new HashMap<>();
            for (int i = 1; i <= columns; i++) {
                Object value = getValue(resultSetMetaData.getColumnTypeName(i), i, resultSetDelegate);
                if (value == null) {
                    value = getDefaultValueByColumnType(resultSetMetaData.getColumnTypeName(i));
                }
                map.put(resultSetMetaData.getColumnLabel(i), value);
            }
        } catch (SQLException e) {
            logger.warning("Failed to map result set: " + e.getMessage());
        }
        return map;
    }

    @Override
    public Object getValue(Field field, String columnName, ResultSetDelegate<ResultSet> resultSetDelegate) {
        ResultSet resultSet = resultSetDelegate.getDelegate();
        Object value = null;
        try {
            SqlJSON sqlJSON = field.getAnnotation(SqlJSON.class);
            if (sqlJSON != null && sqlJSON.convert() != JSONConvert.class) {
                if (sqlJSON != null && sqlJSON.convert() != JSONConvert.class) {
                    String json = resultSet.getString(columnName);
                    return SqlBeanUtil.convertJSON(sqlJSON.convert().newInstance(), json, field);
                }
            }
            switch (field.getType().getName()) {
                case "byte":
                    value = resultSet.getByte(columnName);
                    break;
                case "java.lang.Byte":
                    value = resultSet.getObject(columnName);
                    if (value != null) {
                        value = resultSet.getByte(columnName);
                    }
                    break;
                case "short":
                    value = resultSet.getShort(columnName);
                    break;
                case "java.lang.Short":
                    value = resultSet.getObject(columnName);
                    if (value != null) {
                        value = resultSet.getShort(columnName);
                    }
                    break;
                case "int":
                    value = resultSet.getInt(columnName);
                    break;
                case "java.lang.Integer":
                    value = resultSet.getObject(columnName);
                    if (value != null) {
                        value = resultSet.getInt(columnName);
                    }
                    break;
                case "float":
                    value = resultSet.getFloat(columnName);
                    break;
                case "java.lang.Float":
                    value = resultSet.getObject(columnName);
                    if (value != null) {
                        value = resultSet.getFloat(columnName);
                    }
                    break;
                case "double":
                    value = resultSet.getDouble(columnName);
                    break;
                case "java.lang.Double":
                    value = resultSet.getObject(columnName);
                    if (value != null) {
                        value = resultSet.getDouble(columnName);
                    }
                    break;
                case "long":
                    value = resultSet.getLong(columnName);
                    break;
                case "java.lang.Long":
                    value = resultSet.getObject(columnName);
                    if (value != null) {
                        value = resultSet.getLong(columnName);
                    }
                    break;
                case "boolean":
                    value = resultSet.getBoolean(columnName);
                    break;
                case "java.lang.Boolean":
                    value = resultSet.getObject(columnName);
                    if (value != null) {
                        value = resultSet.getBoolean(columnName);
                    }
                    break;
                case "char":
                case "java.lang.Character":
                    value = resultSet.getString(columnName);
                    if (StringUtil.isNotEmpty(value)) {
                        value = value.toString().charAt(0);
                    }
                    break;
                case "java.lang.String":
                    value = resultSet.getString(columnName);
                    break;
                case "java.sql.Date":
                    value = resultSet.getDate(columnName);
                    break;
                case "java.sql.Time":
                    value = resultSet.getTime(columnName);
                    break;
                case "java.util.Date":
                case "java.sql.Timestamp":
                    value = resultSet.getTimestamp(columnName);
                    break;
                case "java.time.LocalDate":
                    value = DateUtil.dateToLocalDate(resultSet.getTimestamp(columnName));
                    break;
                case "java.time.LocalTime":
                    value = DateUtil.dateToLocalTime(resultSet.getTimestamp(columnName));
                    break;
                case "java.time.LocalDateTime":
                    value = DateUtil.dateToLocalDateTime(resultSet.getTimestamp(columnName));
                    break;
                case "java.math.BigDecimal":
                    value = resultSet.getBigDecimal(columnName);
                    break;
                default:
                    value = resultSet.getObject(columnName);
                    break;
            }
        } catch (SQLException e) {
            logger.warning("Failed to get value for field '" + field.getName() + "': " + e.getMessage());
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return value;
    }

    @Override
    public Object getValue(String jdbcType, int index, ResultSetDelegate<ResultSet> resultSetDelegate) {
        ResultSet resultSet = resultSetDelegate.getDelegate();
        Object value = null;
        try {
            switch (jdbcType) {
                case "TINYINT":
                    value = resultSet.getByte(index);
                    break;
                case "SMALLINT":
                    value = resultSet.getShort(index);
                    break;
                case "INTEGER":
                    value = resultSet.getInt(index);
                    break;
                case "BIGINT":
                    value = resultSet.getLong(index);
                    break;
                case "REAL":
                    value = resultSet.getFloat(index);
                    break;
                case "FLOAT":
                case "DOUBLE":
                    value = resultSet.getDouble(index);
                    break;
                case "BIT":
                case "BOOLEAN":
                    value = resultSet.getBoolean(index);
                    break;
                case "CHAR":
                case "VARCHAR":
                case "LONGVARCHAR":
                    value = resultSet.getString(index);
                    break;
                case "NUMERIC":
                case "DECIMAL":
                    value = resultSet.getBigDecimal(index);
                    break;
                default:
                    value = resultSet.getObject(index);
                    break;
            }
        } catch (SQLException e) {
            logger.warning("Failed to get value by jdbcType '" + jdbcType + "': " + e.getMessage());
        }
        return value;
    }

}
