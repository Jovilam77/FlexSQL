package cn.vonce.sql.test;

import cn.vonce.sql.annotation.SqlColumn;
import cn.vonce.sql.annotation.SqlId;
import cn.vonce.sql.annotation.SqlLogically;
import cn.vonce.sql.annotation.SqlTable;
import cn.vonce.sql.bean.Select;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.enumerate.LogicallyStrategy;
import cn.vonce.sql.helper.SqlHelper;
import org.junit.Before;
import org.junit.Test;

import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * 逻辑删除策略测试
 * 验证 FILTER 和 NOT_FILTER 两种策略的行为
 */
public class LogicallyStrategyTest {

    private static final Logger logger = Logger.getLogger(LogicallyStrategyTest.class.getName());
    
    private SqlBeanConfig sqlBeanConfig;

    @Before
    public void setUp() {
        sqlBeanConfig = new SqlBeanConfig();
        sqlBeanConfig.setToUpperCase(false);
    }

    /**
     * 测试默认策略（FILTER）：应该自动拼接 deleted=0 条件
     */
    @Test
    public void testDefaultFilterStrategy() {
        logger.info("=== Testing default FILTER strategy ===");
        
        SqlBeanMeta sqlBeanMeta = new SqlBeanMeta();
        sqlBeanMeta.setDbType(DbType.MySQL);
        sqlBeanMeta.setSqlBeanConfig(sqlBeanConfig);
        
        Select select = new Select();
        select.setBeanClass(FilterUser.class);
        select.setSqlBeanMeta(sqlBeanMeta);
        
        String sql = SqlHelper.buildSelectSql(select);
        logger.info("Generated SQL: " + sql);
        
        System.out.println("Generated SQL: " + sql);
        assertTrue("SQL should contain deleted=0 condition. Generated: " + sql, sql.contains("`deleted` = 0"));
        logger.info("Default FILTER strategy test passed");
    }

    /**
     * 测试 NOT_FILTER 策略：不应该拼接逻辑删除条件
     */
    @Test
    public void testNotFilterStrategy() {
        logger.info("=== Testing NOT_FILTER strategy ===");
        
        SqlBeanMeta sqlBeanMeta = new SqlBeanMeta();
        sqlBeanMeta.setDbType(DbType.MySQL);
        sqlBeanMeta.setSqlBeanConfig(sqlBeanConfig);
        
        Select select = new Select();
        select.setBeanClass(NotFilterUser.class);
        select.setSqlBeanMeta(sqlBeanMeta);
        
        String sql = SqlHelper.buildSelectSql(select);
        logger.info("Generated SQL: " + sql);
        
        assertFalse("SQL should NOT contain deleted=0 condition. Generated: " + sql, sql.contains("`deleted` = 0"));
        logger.info("NOT_FILTER strategy test passed");
    }

    /**
     * 测试带条件查询时的逻辑删除策略
     */
    @Test
    public void testFilterStrategyWithCondition() {
        logger.info("=== Testing FILTER strategy with custom condition ===");
        
        SqlBeanMeta sqlBeanMeta = new SqlBeanMeta();
        sqlBeanMeta.setDbType(DbType.MySQL);
        sqlBeanMeta.setSqlBeanConfig(sqlBeanConfig);
        
        Select select = new Select();
        select.setBeanClass(FilterUser.class);
        select.setSqlBeanMeta(sqlBeanMeta);
        select.where().eq("name", "test");
        
        String sql = SqlHelper.buildSelectSql(select);
        logger.info("Generated SQL: " + sql);
        
        assertTrue("SQL should contain deleted=0 condition", sql.contains("`deleted` = 0"));
        assertTrue("SQL should contain custom condition", sql.contains("`name` = 'test'"));
        logger.info("FILTER strategy with condition test passed");
    }

    /**
     * 测试 NOT_FILTER 策略带条件查询
     */
    @Test
    public void testNotFilterStrategyWithCondition() {
        logger.info("=== Testing NOT_FILTER strategy with custom condition ===");
        
        SqlBeanMeta sqlBeanMeta = new SqlBeanMeta();
        sqlBeanMeta.setDbType(DbType.MySQL);
        sqlBeanMeta.setSqlBeanConfig(sqlBeanConfig);
        
        Select select = new Select();
        select.setBeanClass(NotFilterUser.class);
        select.setSqlBeanMeta(sqlBeanMeta);
        select.where().eq("name", "test");
        
        String sql = SqlHelper.buildSelectSql(select);
        logger.info("Generated SQL: " + sql);
        
        assertFalse("SQL should NOT contain deleted=0 condition. Generated: " + sql, sql.contains("`deleted` = 0"));
        assertTrue("SQL should contain custom condition", sql.contains("`name` = 'test'"));
        logger.info("NOT_FILTER strategy with condition test passed");
    }

    /**
     * 使用默认 FILTER 策略的用户实体
     */
    @SqlTable("t_filter_user")
    public static class FilterUser {
        
        @SqlId
        @SqlColumn("id")
        private String id;
        
        @SqlColumn("name")
        private String name;
        
        @SqlLogically  // 默认策略 FILTER
        @SqlColumn("deleted")
        private Integer deleted;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getDeleted() {
            return deleted;
        }

        public void setDeleted(Integer deleted) {
            this.deleted = deleted;
        }
    }

    /**
     * 使用 NOT_FILTER 策略的用户实体
     */
    @SqlTable("t_not_filter_user")
    public static class NotFilterUser {
        
        @SqlId
        @SqlColumn("id")
        private String id;
        
        @SqlColumn("name")
        private String name;
        
        @SqlLogically(strategy = LogicallyStrategy.NOT_FILTER)  // 不自动过滤
        @SqlColumn("deleted")
        private Integer deleted;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getDeleted() {
            return deleted;
        }

        public void setDeleted(Integer deleted) {
            this.deleted = deleted;
        }
    }
}