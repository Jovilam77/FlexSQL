package cn.vonce.sql.java.config;

import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.uitls.ReflectUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;

/**
 * 自动配置多数据源 抽象类
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/9/24 16:53
 */
public abstract class BaseAutoConfigMultiDataSource {

    private static final Logger logger = Logger.getLogger(BaseAutoConfigMultiDataSource.class.getName());

    protected final static Map<String, String> fieldMap = new HashMap<>(8);
    protected final static String DRUID_DATA_SOURCE_CLASS = "com.alibaba.druid.pool.DruidDataSource";

    static {
        fieldMap.put("jdbcUrl", "url");
        fieldMap.put("url", "jdbcUrl");
        fieldMap.put("driverClassName", "driverClass");
        fieldMap.put("driverClass", "driverClassName");
        fieldMap.put("minIdle", "minimumIdle");
        fieldMap.put("minimumIdle", "minIdle");
    }

    /**
     * 获取数据库连接类型
     *
     * @param type
     * @return
     */
    public Class<?> getTypeClass(String type) {
        Class<?> typeClass = null;
        try {
            if (StringUtil.isEmpty(type)) {
                typeClass = Class.forName(DRUID_DATA_SOURCE_CLASS);
            } else {
                typeClass = Class.forName(type);
            }
        } catch (ClassNotFoundException e) {
            throw new SqlBeanException("未找到数据源类型：" + e.getMessage());
        }
        return typeClass;
    }

    public abstract Map<String, Object> getPropertyMap();

    public abstract String getProperty(String key);

    public abstract String getDataSourceType();

    public abstract String getDataSourcePrefix();

    public void config(Set<String> dataSourceNameSet, String defaultDataSource, RegisterAutoConfigMultiDataSourceBean register) {
        if (dataSourceNameSet == null || dataSourceNameSet.isEmpty()) {
            return;
        }
        DataSource defaultTargetDataSource = null;
        Map<String, DataSource> dataSourceMap = new HashMap<>(8);
        Class<?> typeClass = getTypeClass(this.getProperty(this.getDataSourceType()));
        String dataSourcePrefix = this.getDataSourcePrefix();
        for (String dataSourceName : dataSourceNameSet) {
            DataSource dataSource = null;
            try {
                dataSource = (DataSource) typeClass.newInstance();
            } catch (IllegalAccessException e) {
                logger.warning("Failed to instantiate data source: " + e.getMessage());
            } catch (InstantiationException e) {
                logger.warning("Failed to instantiate data source: " + e.getMessage());
            }
            for (Map.Entry<String, Object> property : this.getPropertyMap().entrySet()) {
                String prefix = dataSourcePrefix + "." + dataSourceName;
                if (property.getKey().startsWith(prefix) && property.getKey().length() > prefix.length()) {
                    String fieldName = property.getKey().substring(prefix.length() + 1);
                    if (StringUtil.isBlank(fieldName)) {
                        continue;
                    }
                    Field field = ReflectUtil.getField(dataSource, fieldName);
                    if (field == null) {
                        field = ReflectUtil.getField(dataSource, StringUtil.underlineToHump(fieldName));
                    }
                    if (field == null && fieldMap.containsKey(fieldName)) {
                        field = ReflectUtil.getField(dataSource, fieldMap.get(fieldName));
                    }
                    if (field == null && fieldMap.containsKey(fieldName)) {
                        field = ReflectUtil.getField(dataSource, fieldMap.get(StringUtil.underlineToHump(fieldName)));
                    }
                    if (field != null) {
                        try {
                            field.setAccessible(true);
                            field.set(dataSource, SqlBeanUtil.getValueConvert(field.getType(), property.getValue()));
                        } catch (IllegalAccessException e) {
                            logger.warning("Failed to set field value '" + field.getName() + "': " + e.getMessage());
                        }
                    }
                }
            }
            if (dataSourceName.equals(defaultDataSource)) {
                defaultTargetDataSource = dataSource;
            }
            dataSourceMap.put(dataSourceName, dataSource);
        }
        register.registerBean(defaultTargetDataSource, dataSourceMap);
    }

}
