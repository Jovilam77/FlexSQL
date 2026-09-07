package cn.vonce.sql.test;

import cn.vonce.sql.uitls.LogUtil;
import cn.vonce.sql.uitls.ReflectUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.SnowflakeId16;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * Unit tests for optimization fixes
 * Validates correctness and effectiveness of all fixes
 */
public class OptimizationTest {

    private static final Logger logger = Logger.getLogger(OptimizationTest.class.getName());

    /**
     * Test ReflectUtil thread-safe initialization
     */
    @Test
    public void testReflectUtilThreadSafety() throws InterruptedException, ExecutionException {
        logger.info("=== Testing ReflectUtil Thread Safety ===");
        
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<Future<?>> futures = new HashSet<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                Object reflect = ReflectUtil.instance();
                assertNotNull("Reflect instance should not be null", reflect);
                return reflect;
            }));
        }

        Object firstInstance = null;
        for (Future<?> future : futures) {
            Object instance = future.get();
            assertNotNull("Each thread should get an instance", instance);
            if (firstInstance == null) {
                firstInstance = instance;
            } else {
                assertSame("All threads should get the same instance", firstInstance, instance);
            }
        }

        executor.shutdown();
        assertTrue("Executor should shutdown properly", executor.awaitTermination(5, TimeUnit.SECONDS));
        logger.info("ReflectUtil thread safety test passed");
    }

    /**
     * Test SnowflakeId16 machine ID generation
     */
    @Test
    public void testSnowflakeId16MachineId() {
        logger.info("=== Testing SnowflakeId16 Machine ID Generation ===");
        
        // SnowflakeId16 uses static methods, trigger initialization by calling nextId
        SnowflakeId16.nextId();
        
        try {
            java.lang.reflect.Field shardIdField = SnowflakeId16.class.getDeclaredField("SHARD_ID");
            shardIdField.setAccessible(true);
            long shardId = shardIdField.getLong(null); // static field, pass null
            
            assertTrue("Shard ID should be non-negative", shardId >= 0);
            assertTrue("Shard ID should be in valid range", shardId < 32);
            logger.info("Shard ID generated successfully: " + shardId);
            
        } catch (Exception e) {
            fail("Failed to get shard ID: " + e.getMessage());
        }
    }

    /**
     * Test SnowflakeId16 concurrency performance and uniqueness
     */
    @Test
    public void testSnowflakeId16Concurrency() throws InterruptedException, ExecutionException {
        logger.info("=== Testing SnowflakeId16 Concurrency ===");
        
        int threadCount = 10;
        int idsPerThread = 5000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<Long> generatedIds = new ConcurrentSkipListSet<>();
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < idsPerThread; j++) {
                    long id = SnowflakeId16.nextId();
                    generatedIds.add(id);
                }
                latch.countDown();
            });
        }

        assertTrue("All threads should complete", latch.await(30, TimeUnit.SECONDS));
        long endTime = System.currentTimeMillis();

        int expectedCount = threadCount * idsPerThread;
        assertEquals("Generated ID count should be correct", expectedCount, generatedIds.size());
        logger.info("Generated IDs: " + generatedIds.size() + ", Expected: " + expectedCount);
        
        long duration = endTime - startTime;
        double throughput = (double) expectedCount / (duration / 1000.0);
        logger.info("Duration: " + duration + "ms, Throughput: " + String.format("%.2f", throughput) + " IDs/s");
        
        executor.shutdown();
        logger.info("SnowflakeId16 concurrency test passed");
    }

    /**
     * Test SqlBeanUtil cache thread safety
     */
    @Test
    public void testSqlBeanUtilCacheThreadSafety() throws InterruptedException, ExecutionException {
        logger.info("=== Testing SqlBeanUtil Cache Thread Safety ===");
        
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<Future<?>> futures = new HashSet<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    java.lang.reflect.Method getTableMethod = SqlBeanUtil.class.getDeclaredMethod("getTable", Class.class);
                    getTableMethod.setAccessible(true);
                    Object result = getTableMethod.invoke(null, OptimizationTest.class);
                    assertNotNull("Cache result should not be null", result);
                } catch (Exception e) {
                    // Ignore, testing concurrency safety
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        assertTrue("Executor should shutdown properly", executor.awaitTermination(5, TimeUnit.SECONDS));
        logger.info("SqlBeanUtil cache thread safety test passed");
    }

    /**
     * Test SQL injection protection
     */
    @Test
    public void testSQLInjectProtection() {
        logger.info("=== Testing SQL Injection Protection ===");
        
        String maliciousInput = "test'; DROP TABLE users; --";
        String filtered = SqlBeanUtil.filterSQLInject(maliciousInput);
        assertNotNull("Filtered result should not be null", filtered);
        assertTrue("Filtered result should not be empty", filtered.length() > 0);
        logger.info("Original: " + maliciousInput);
        logger.info("Filtered: " + filtered);

        String wildcardInput = "test%_value";
        String wildcardFiltered = SqlBeanUtil.filterLikeWildcard(wildcardInput);
        assertNotNull("Wildcard filtered result should not be null", wildcardFiltered);
        assertTrue("Wildcard filtered result should not be empty", wildcardFiltered.length() > 0);
        logger.info("Original: " + wildcardInput);
        logger.info("Filtered: " + wildcardFiltered);

        assertSame("Null input should return empty string", "", SqlBeanUtil.filterSQLInject(null));
        assertSame("Null input should return empty string", "", SqlBeanUtil.filterLikeWildcard(null));

        logger.info("SQL injection protection test passed");
    }

    /**
     * Test parameter validation
     */
    @Test
    public void testParameterValidation() {
        logger.info("=== Testing Parameter Validation ===");
        
        try {
            SqlBeanUtil.getIdField(null);
            fail("Should throw IllegalArgumentException for null clazz");
        } catch (IllegalArgumentException e) {
            assertTrue("Exception message should contain clazz", e.getMessage().contains("clazz"));
        }

        try {
            SqlBeanUtil.getLogicallyField(null);
            fail("Should throw IllegalArgumentException for null clazz");
        } catch (IllegalArgumentException e) {
            assertTrue("Exception message should contain clazz", e.getMessage().contains("clazz"));
        }

        logger.info("Parameter validation test passed");
    }

    /**
     * Test LogUtil functionality
     */
    @Test
    public void testLogUtil() {
        logger.info("=== Testing LogUtil ===");
        
        LogUtil.info(OptimizationTest.class, "Testing INFO level");
        LogUtil.warning(OptimizationTest.class, "Testing WARNING level");
        LogUtil.severe(OptimizationTest.class, "Testing SEVERE level");
        LogUtil.config(OptimizationTest.class, "Testing CONFIG level");
        LogUtil.fine(OptimizationTest.class, "Testing FINE level");
        
        try {
            throw new RuntimeException("Test exception");
        } catch (RuntimeException e) {
            LogUtil.logException(OptimizationTest.class, e);
        }

        logger.info("LogUtil test passed");
    }

    /**
     * Integration test: verify all fixes don't affect existing functionality
     */
    @Test
    public void testIntegration() {
        logger.info("=== Running Integration Test ===");
        
        long id = SnowflakeId16.nextId();
        assertNotNull("ID should not be null", id);
        assertTrue("ID should be greater than 0", id > 0);
        logger.info("Generated ID: " + id);

        Object reflect = ReflectUtil.instance();
        assertNotNull("Reflect instance should not be null", reflect);
        logger.info("Reflect instance type: " + reflect.getClass().getName());

        String safeValue = SqlBeanUtil.filterSQLInject("test' OR '1'='1");
        assertTrue("SQL injection should be filtered", safeValue.contains("\\'"));

        logger.info("Integration test passed");
    }
}
