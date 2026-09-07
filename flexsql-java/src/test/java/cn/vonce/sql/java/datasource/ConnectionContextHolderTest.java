package cn.vonce.sql.java.datasource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * ConnectionContextHolder 修复测试
 * 验证修复的空指针检查和清理方法
 */
public class ConnectionContextHolderTest {

    private static final Logger logger = Logger.getLogger(ConnectionContextHolderTest.class.getName());

    @Before
    public void setUp() {
        // 确保测试前清理
        ConnectionContextHolder.clearAll();
        DataSourceContextHolder.clearDataSource();
    }

    @After
    public void tearDown() {
        // 测试后清理
        ConnectionContextHolder.clearAll();
        DataSourceContextHolder.clearDataSource();
    }

    /**
     * 测试 setReadOnly(String, boolean) 方法的空指针安全
     */
    @Test
    public void testSetReadOnlyWithNullConnection() {
        logger.info("=== Testing setReadOnly with null connection ===");
        
        // 当数据源不存在时，调用 setReadOnly 不应该抛出 NullPointerException
        try {
            ConnectionContextHolder.setReadOnly("non_existent_ds", true);
            // 如果没有抛异常，测试通过
            assertTrue("setReadOnly should handle null connection gracefully", true);
            logger.info("setReadOnly with null connection test passed");
        } catch (NullPointerException e) {
            fail("setReadOnly should not throw NullPointerException for non-existent data source");
        }
    }

    /**
     * 测试 clearAll() 方法
     */
    @Test
    public void testClearAll() {
        logger.info("=== Testing clearAll method ===");
        
        // 测试 clearAll 不会抛出异常
        try {
            ConnectionContextHolder.clearAll();
            assertTrue("clearAll should execute without exception", true);
            logger.info("clearAll test passed");
        } catch (Exception e) {
            fail("clearAll should not throw exception: " + e.getMessage());
        }
    }

    /**
     * 测试多次清理不会出错
     */
    @Test
    public void testMultipleClearAll() {
        logger.info("=== Testing multiple clearAll calls ===");
        
        try {
            ConnectionContextHolder.clearAll();
            ConnectionContextHolder.clearAll();
            ConnectionContextHolder.clearAll();
            assertTrue("Multiple clearAll calls should not throw exception", true);
            logger.info("Multiple clearAll test passed");
        } catch (Exception e) {
            fail("Multiple clearAll calls should not throw exception: " + e.getMessage());
        }
    }

    /**
     * 测试 DataSourceContextHolder 的清理方法
     */
    @Test
    public void testDataSourceContextHolderClear() {
        logger.info("=== Testing DataSourceContextHolder clear ===");
        
        DataSourceContextHolder.setDataSource("test_ds");
        assertEquals("test_ds", DataSourceContextHolder.getDataSource());
        
        DataSourceContextHolder.clearDataSource();
        assertNull("DataSource should be cleared", DataSourceContextHolder.getDataSource());
        logger.info("DataSourceContextHolder clear test passed");
    }

    /**
     * 测试 TransactionalContextHolder 的清理方法
     */
    @Test
    public void testTransactionalContextHolderClear() {
        logger.info("=== Testing TransactionalContextHolder clear ===");
        
        TransactionalContextHolder.setXid("test_xid");
        assertEquals("test_xid", TransactionalContextHolder.getXid());
        
        TransactionalContextHolder.clearXid();
        assertNull("Xid should be cleared", TransactionalContextHolder.getXid());
        logger.info("TransactionalContextHolder clear test passed");
    }
}