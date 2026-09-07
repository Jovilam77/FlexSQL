package cn.vonce.sql.java.datasource;

import cn.vonce.sql.java.annotation.DbTransactional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * 事务超时管理器测试
 */
public class TransactionTimeoutManagerTest {

    private static final Logger logger = Logger.getLogger(TransactionTimeoutManagerTest.class.getName());

    @Before
    public void setUp() {
        TransactionalContextHolder.clearXid();
    }

    @After
    public void tearDown() {
        TransactionalContextHolder.clearXid();
    }

    /**
     * 测试调度超时任务
     */
    @Test
    public void testScheduleTimeout() {
        logger.info("=== Testing scheduleTimeout ===");
        
        String xid = "test_xid_001";
        TransactionalContextHolder.setXid(xid);
        
        // 调度一个短超时任务（1秒）
        TransactionTimeoutManager.scheduleTimeout(xid, 1);
        
        assertTrue("Timeout task should exist", TransactionTimeoutManager.hasTimeoutTask(xid));
        
        // 取消任务
        TransactionTimeoutManager.cancelTimeout(xid);
        
        assertFalse("Timeout task should be cancelled", TransactionTimeoutManager.hasTimeoutTask(xid));
        
        logger.info("scheduleTimeout test passed");
    }

    /**
     * 测试取消不存在的超时任务
     */
    @Test
    public void testCancelNonExistentTimeout() {
        logger.info("=== Testing cancelNonExistentTimeout ===");
        
        // 取消不存在的任务不应该抛出异常
        try {
            TransactionTimeoutManager.cancelTimeout("non_existent_xid");
            assertTrue("Cancelling non-existent task should not throw exception", true);
        } catch (Exception e) {
            fail("Cancelling non-existent task should not throw exception: " + e.getMessage());
        }
        
        logger.info("cancelNonExistentTimeout test passed");
    }

    /**
     * 测试调度超时任务但不设置事务上下文
     */
    @Test
    public void testScheduleWithoutTransactionContext() {
        logger.info("=== Testing scheduleWithoutTransactionContext ===");
        
        String xid = "test_xid_002";
        // 不设置 TransactionalContextHolder
        
        // 调度超时任务
        TransactionTimeoutManager.scheduleTimeout(xid, 1);
        
        // 任务应该被调度
        assertTrue("Timeout task should exist", TransactionTimeoutManager.hasTimeoutTask(xid));
        
        // 取消任务
        TransactionTimeoutManager.cancelTimeout(xid);
        
        logger.info("scheduleWithoutTransactionContext test passed");
    }

    /**
     * 测试调度无效超时时间（<=0）
     */
    @Test
    public void testScheduleWithInvalidTimeout() {
        logger.info("=== Testing scheduleWithInvalidTimeout ===");
        
        String xid = "test_xid_003";
        
        // 测试超时时间为0
        TransactionTimeoutManager.scheduleTimeout(xid, 0);
        assertFalse("Task should not be scheduled for timeout=0", TransactionTimeoutManager.hasTimeoutTask(xid));
        
        // 测试超时时间为负数
        TransactionTimeoutManager.scheduleTimeout(xid, -1);
        assertFalse("Task should not be scheduled for timeout<0", TransactionTimeoutManager.hasTimeoutTask(xid));
        
        // 测试空xid
        TransactionTimeoutManager.scheduleTimeout(null, 5);
        assertFalse("Task should not be scheduled for null xid", TransactionTimeoutManager.hasTimeoutTask(xid));
        
        logger.info("scheduleWithInvalidTimeout test passed");
    }

    /**
     * 测试 DbTransactional 注解的 timeout 属性
     */
    @Test
    public void testDbTransactionalTimeout() {
        logger.info("=== Testing DbTransactional timeout ===");
        
        // 测试默认超时值
        DbTransactional defaultTransactional = DefaultTransactional.class.getAnnotation(DbTransactional.class);
        assertEquals("Default timeout should be -1", -1, defaultTransactional.timeout());
        
        // 测试自定义超时值
        DbTransactional customTransactional = CustomTimeoutTransactional.class.getAnnotation(DbTransactional.class);
        assertEquals("Custom timeout should be 5", 5, customTransactional.timeout());
        
        // 测试只读事务的超时值
        DbTransactional readOnlyTransactional = ReadOnlyTransactional.class.getAnnotation(DbTransactional.class);
        assertTrue("Should be read only", readOnlyTransactional.readOnly());
        assertEquals("Timeout should be 10", 10, readOnlyTransactional.timeout());
        
        logger.info("DbTransactional timeout test passed");
    }

    @DbTransactional
    private static class DefaultTransactional {}

    @DbTransactional(timeout = 5)
    private static class CustomTimeoutTransactional {}

    @DbTransactional(readOnly = true, timeout = 10)
    private static class ReadOnlyTransactional {}
}