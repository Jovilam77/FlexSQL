package cn.vonce.sql.java.config;

import cn.vonce.sql.annotation.SqlTable;
import cn.vonce.sql.bean.Table;
import cn.vonce.sql.bean.TableInfo;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.service.AdvancedDbManageService;
import cn.vonce.sql.service.DbManageService;
import cn.vonce.sql.service.SqlBeanService;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 自动创建表监听 基础类
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2020/8/3 10:51
 */
public abstract class BaseAutoCreateTableListener {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    public abstract <T> T getBean(String name);

    public abstract <T> List<T> getBeansForType(Class<T> baseType);

    public abstract List<String> getBeanNamesForType(Class<?> baseType);

    public void processSqlBeanServices() {
        List<SqlBeanService> beanServiceList = this.getBeansForType(SqlBeanService.class);
        if (beanServiceList == null || beanServiceList.isEmpty()) {
            return;
        }
        
        // 去除重复的，使用 beanClass 作为 key
        Map<Class<?>, SqlBeanService> beanServiceMap = new ConcurrentHashMap<>();
        for (SqlBeanService sqlBeanService : beanServiceList) {
            Class<?> beanClass = sqlBeanService.getBeanClass();
            if (beanClass != null) {
                beanServiceMap.put(beanClass, sqlBeanService);
            }
        }
        
        if (beanServiceMap.isEmpty()) {
            return;
        }
        
        // 按 schema 分组，一个 schema 对应多个 SqlBeanService
        Map<String, List<SqlBeanService>> schemaMap = new ConcurrentHashMap<>();
        for (SqlBeanService sqlBeanService : beanServiceMap.values()) {
            Class<?> clazz = sqlBeanService.getBeanClass();
            if (clazz == null) {
                continue;
            }
            
            SqlTable sqlTable = SqlBeanUtil.getSqlTable(clazz);
            if (sqlTable == null) {
                continue;
            }
            
            // 检查是否实现了 AdvancedDbManageService
            if (!(sqlBeanService instanceof AdvancedDbManageService)) {
                logger.warning("SqlBeanService does not implement AdvancedDbManageService: " + clazz.getName());
                continue;
            }
            
            schemaMap.computeIfAbsent(sqlTable.schema(), k -> new ArrayList<>()).add(sqlBeanService);
        }
        
        // 处理每个 schema
        for (Map.Entry<String, List<SqlBeanService>> entry : schemaMap.entrySet()) {
            String schema = entry.getKey();
            List<SqlBeanService> services = entry.getValue();
            
            if (services.isEmpty()) {
                continue;
            }
            
            SqlBeanService firstService = services.get(0);
            DbType dbType = firstService.getSqlBeanMeta().getDbType();
            
            // 检查 schema 是否存在，不存在则创建（不支持 sqlite 和 oracle）
            if (StringUtil.isNotBlank(schema)) {
                if (dbType == DbType.SQLite || dbType == DbType.Oracle) {
                    continue;
                }
                try {
                    AdvancedDbManageService advancedService = (AdvancedDbManageService) firstService;
                    List<String> databases = advancedService.getSchemas(schema);
                    if (databases == null || databases.isEmpty()) {
                        advancedService.createSchema(schema);
                        logger.info(String.format("-----Schema:[%s]不存在,已为你自动创建-----", schema));
                    }
                } catch (Exception e) {
                    logger.warning(String.format("创建 Schema 出错：" + e.getMessage()));
                    continue;
                }
            }
            
            // 获取当前 schema 的表列表并构建索引
            List<TableInfo> tableList;
            Set<String> tableNameSet = new HashSet<>();
            Map<String, TableInfo> tableInfoMap = new HashMap<>();
            
            try {
                DbManageService dbManageService = (DbManageService) firstService;
                tableList = dbManageService.getTableList();
                // 构建表名索引，支持不区分大小写的查找
                for (TableInfo tableInfo : tableList) {
                    String tableName = tableInfo.getName().toLowerCase();
                    tableNameSet.add(tableName);
                    tableInfoMap.put(tableName, tableInfo);
                }
            } catch (Exception e) {
                logger.warning(String.format("获取表列表出错：" + e.getMessage()));
                continue;
            }
            
            // 处理当前 schema 的所有服务
            for (SqlBeanService sqlBeanService : services) {
                Class<?> clazz = sqlBeanService.getBeanClass();
                if (clazz == null) {
                    continue;
                }
                
                SqlTable sqlTable = SqlBeanUtil.getSqlTable(clazz);
                Table table = SqlBeanUtil.getTable(clazz);
                
                // 存在 @SqlTable 注解且不是视图才处理
                if (sqlTable == null || sqlTable.isView()) {
                    continue;
                }
                
                String tableNameLower = table.getName().toLowerCase();
                boolean isExist = tableNameSet.contains(tableNameLower);
                
                AdvancedDbManageService advancedService = (AdvancedDbManageService) sqlBeanService;
                
                if (isExist) {
                    TableInfo tableInfo = tableInfoMap.get(tableNameLower);
                    if (tableInfo != null) {
                        String remarks = sqlTable.remarks();
                        // 如果没有设置表注释，则从类上获取
                        if (StringUtil.isEmpty(remarks)) {
                            remarks = SqlBeanUtil.getBeanRemarks(SqlBeanUtil.getConstantClass(clazz));
                        }
                        
                        // 表注释不一致，更新注释
                        if (sqlTable.autoAlter() && !remarks.equals(tableInfo.getRemarks())) {
                            try {
                                advancedService.alterRemarks(remarks);
                            } catch (Exception e) {
                                logger.warning(String.format("更新表注释出错：" + e.getMessage()));
                            }
                        }
                        
                        // 更新表结构
                        if (sqlTable.autoAlter()) {
                            try {
                                advancedService.alter(table, advancedService.getColumnInfoList(table.getName()));
                            } catch (Exception e) {
                                logger.warning(String.format("更新表结构出错：" + e.getMessage()));
                            }
                        }
                    }
                } else {
                    // 创建表
                    if (sqlTable.autoCreate()) {
                        try {
                            advancedService.createTable();
                            String fullTableName = StringUtil.isNotEmpty(table.getSchema()) 
                                ? table.getSchema() + "." + table.getName() 
                                : table.getName();
                            logger.info(String.format("-----Table:[%s]不存在,已为你自动创建-----", fullTableName));
                        } catch (Exception e) {
                            logger.warning(String.format("创建表结构出错：" + e.getMessage()));
                        }
                    }
                }
            }
        }
    }

}
