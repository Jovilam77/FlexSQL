package cn.vonce.sql.jfinal.config;

import cn.vonce.sql.java.annotation.DbTransactional;
import cn.vonce.sql.java.config.BaseAutoConfigMultiDataSource;
import cn.vonce.sql.jfinal.annotation.EnableAutoConfigMultiDataSource;
import cn.vonce.sql.jfinal.datasource.DynamicDataSource;
import cn.vonce.sql.uitls.StringUtil;
import com.jfinal.aop.AopManager;
import com.jfinal.kit.PropKit;

import javax.sql.DataSource;
import java.util.*;

/**
 * 自动配置多数据源
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 17:00
 */
public class AutoConfigMultiDataSource extends BaseAutoConfigMultiDataSource {

    private Map<String, Object> propertyMap;

    @Override
    public Map<String, Object> getPropertyMap() {
        return this.propertyMap;
    }

    @Override
    public String getProperty(String key) {
        return PropKit.get(key);
    }

    @Override
    public String getDataSourceType() {
        return "jfinal.datasource.type";
    }

    @Override
    public String getDataSourcePrefix() {
        return "jfinal.datasource.flexsql";
    }

    /**
     * 配置多数据源（在JFinalConfig中调用）
     *
     * @param enableAutoConfigMultiDataSource 注解
     */
    public void config(EnableAutoConfigMultiDataSource enableAutoConfigMultiDataSource) {
        String defaultDataSource = null;
        Set<String> dataSourceNameSet = new LinkedHashSet<>();
        String dataSourcePrefix = this.getDataSourcePrefix();
        this.propertyMap = new HashMap<>();

        // 从PropKit中读取所有配置，添加空检查
        com.jfinal.kit.Prop prop = PropKit.getProp();
        if (prop == null) {
            // 如果没有配置文件，直接返回
            return;
        }

        Properties properties = prop.getProperties();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            if (entry.getKey() instanceof String) {
                String key = (String) entry.getKey();
                if (key.startsWith(dataSourcePrefix)) {
                    String dsName = key.substring(key.indexOf(dataSourcePrefix) + dataSourcePrefix.length() + 1);
                    if (dsName.contains(".")) {
                        dsName = dsName.substring(0, dsName.indexOf("."));
                    }
                    dataSourceNameSet.add(dsName);
                    if (defaultDataSource == null) {
                        defaultDataSource = dsName;
                    }
                    this.propertyMap.put((String) entry.getKey(), entry.getValue());
                }
            }
        }

        String anonDefaultDataSource = enableAutoConfigMultiDataSource.defaultDataSource();
        if (anonDefaultDataSource != null && StringUtil.isNotBlank(anonDefaultDataSource)) {
            defaultDataSource = anonDefaultDataSource;
        }

        super.config(dataSourceNameSet, defaultDataSource, (defaultTargetDataSource, dataSourceMap) -> {
            DynamicDataSource dynamicDataSource = new DynamicDataSource();
            dynamicDataSource.setDefaultTargetDataSource(defaultTargetDataSource);
            dynamicDataSource.setTargetDataSources(dataSourceMap);

            // 将动态数据源注册到Aop容器
            AopManager.me().addSingletonObject(dynamicDataSource);

            // 初始化Mybatis配置
            AutoConfigJFinal.init(dynamicDataSource);
        });
    }

}