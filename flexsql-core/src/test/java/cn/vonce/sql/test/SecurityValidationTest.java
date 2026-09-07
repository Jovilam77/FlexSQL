package cn.vonce.sql.test;

import cn.vonce.sql.annotation.SqlColumn;
import cn.vonce.sql.annotation.SqlId;
import cn.vonce.sql.annotation.SqlTable;
import cn.vonce.sql.bean.Column;
import cn.vonce.sql.bean.Drop;
import cn.vonce.sql.bean.Insert;
import cn.vonce.sql.bean.Table;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.dialect.SqlDialect;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.helper.SqlHelper;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * 安全校验测试
 * 验证昨天提交的代码修改：
 * 1. getSchemaName 方法对非法字符的校验
 * 2. buildDrop 方法对表名和schema名的校验
 * 3. fieldAndValuesSql 方法拆分后的功能正确性
 */
public class SecurityValidationTest {

    private static final Logger logger = Logger.getLogger(SecurityValidationTest.class.getName());
    
    private SqlBeanMeta sqlBeanMeta;
    private SqlBeanConfig sqlBeanConfig;

    @Before
    public void setUp() {
        sqlBeanConfig = new SqlBeanConfig();
        sqlBeanConfig.setToUpperCase(false);
        sqlBeanMeta = new SqlBeanMeta();
        sqlBeanMeta.setDbType(DbType.MySQL);
        sqlBeanMeta.setSqlBeanConfig(sqlBeanConfig);
    }

    /**
     * 测试 getSchemaName 方法对非法字符的校验
     */
    @Test
    public void testGetSchemaNameWithInvalidCharacters() {
        logger.info("=== Testing getSchemaName with invalid characters ===");
        
        SqlDialect dialect = sqlBeanMeta.getDbType().getSqlDialect();
        
        // 测试正常的 schema 名称（由于 toUpperCase=false，返回原始大小写）
        String validSchema = "valid_schema";
        assertEquals("Valid schema should return correctly", validSchema, 
                     dialect.getSchemaName(sqlBeanMeta, validSchema));
        
        // 测试包含空格的非法名称
        String invalidWithSpace = "invalid schema";
        try {
            dialect.getSchemaName(sqlBeanMeta, invalidWithSpace);
            fail("Should throw exception for schema with space");
        } catch (SqlBeanException e) {
            assertTrue("Exception message should mention invalid characters", 
                      e.getMessage().contains("invalid characters"));
            logger.info("Caught expected exception for space: " + e.getMessage());
        }
        
        // 测试 SQL 注入攻击
        String sqlInjection = "test'; DROP TABLE users; --";
        try {
            dialect.getSchemaName(sqlBeanMeta, sqlInjection);
            fail("Should throw exception for SQL injection attempt");
        } catch (SqlBeanException e) {
            assertTrue("Exception message should mention invalid characters", 
                      e.getMessage().contains("invalid characters"));
            logger.info("Caught expected exception for SQL injection: " + e.getMessage());
        }
        
        // 测试特殊字符
        String specialChars = "test@#$";
        try {
            dialect.getSchemaName(sqlBeanMeta, specialChars);
            fail("Should throw exception for special characters");
        } catch (SqlBeanException e) {
            assertTrue("Exception message should mention invalid characters", 
                      e.getMessage().contains("invalid characters"));
            logger.info("Caught expected exception for special chars: " + e.getMessage());
        }
        
        // 测试 null 值
        assertNull("Null schema should return null", dialect.getSchemaName(sqlBeanMeta, null));
        
        logger.info("getSchemaName validation tests passed");
    }

    /**
     * 测试 buildDrop 方法对表名和 schema 名的校验
     */
    @Test
    public void testBuildDropWithInvalidTableName() {
        logger.info("=== Testing buildDrop with invalid table name ===");
        
        Drop drop = new Drop();
        drop.setSqlBeanMeta(sqlBeanMeta);
        
        // 测试正常的表名
        Table validTable = new Table();
        validTable.setName("valid_table");
        validTable.setSchema("valid_schema");
        drop.setTable(validTable);
        
        String validSql = SqlHelper.buildDrop(drop);
        assertNotNull("Valid DROP SQL should not be null", validSql);
        assertTrue("DROP SQL should contain valid table", validSql.contains("valid_table"));
        logger.info("Valid DROP SQL: " + validSql);
        
        // 测试包含 SQL 注入的表名
        Table invalidTable = new Table();
        invalidTable.setName("test'; DROP TABLE users; --");
        invalidTable.setSchema("public");
        drop.setTable(invalidTable);
        
        try {
            SqlHelper.buildDrop(drop);
            fail("Should throw exception for SQL injection in table name");
        } catch (SqlBeanException e) {
            assertTrue("Exception message should mention invalid characters", 
                      e.getMessage().contains("invalid characters"));
            assertTrue("Exception message should mention table name", 
                      e.getMessage().contains("Table name"));
            logger.info("Caught expected exception for table name injection: " + e.getMessage());
        }
        
        // 测试包含非法字符的 schema 名
        Table invalidSchemaTable = new Table();
        invalidSchemaTable.setName("valid_table");
        invalidSchemaTable.setSchema("test'; DROP SCHEMA public; --");
        drop.setTable(invalidSchemaTable);
        
        try {
            SqlHelper.buildDrop(drop);
            fail("Should throw exception for SQL injection in schema name");
        } catch (SqlBeanException e) {
            assertTrue("Exception message should mention invalid characters", 
                      e.getMessage().contains("invalid characters"));
            assertTrue("Exception message should mention schema name", 
                      e.getMessage().contains("Schema name"));
            logger.info("Caught expected exception for schema injection: " + e.getMessage());
        }
        
        logger.info("buildDrop validation tests passed");
    }

    /**
     * 测试 fieldAndValuesSql 方法拆分后的功能正确性（Bean模式）
     */
    @Test
    public void testFieldAndValuesSqlBeanMode() {
        logger.info("=== Testing fieldAndValuesSql in Bean mode ===");
        
        Insert<TestUser> insert = new Insert<>();
        insert.setBeanClass(TestUser.class);
        insert.setSqlBeanMeta(sqlBeanMeta);
        
        List<TestUser> userList = new ArrayList<>();
        TestUser user1 = new TestUser();
        user1.setId("1");
        user1.setName("TestUser1");
        user1.setEmail("test1@example.com");
        userList.add(user1);
        
        TestUser user2 = new TestUser();
        user2.setId("2");
        user2.setName("TestUser2");
        user2.setEmail("test2@example.com");
        userList.add(user2);
        
        insert.setBean(userList);
        
        String sql = SqlHelper.buildInsertSql(insert);
        assertNotNull("INSERT SQL should not be null", sql);
        assertTrue("INSERT SQL should start with INSERT", sql.toUpperCase().startsWith("INSERT INTO"));
        assertTrue("INSERT SQL should contain both users", sql.contains("TestUser1"));
        assertTrue("INSERT SQL should contain both users", sql.contains("TestUser2"));
        
        logger.info("INSERT SQL (Bean mode): " + sql);
        logger.info("fieldAndValuesSql Bean mode test passed");
    }

    /**
     * 测试 fieldAndValuesSql 方法拆分后的功能正确性（Column模式）
     */
    @Test
    public void testFieldAndValuesSqlColumnMode() {
        logger.info("=== Testing fieldAndValuesSql in Column mode ===");
        
        Insert<TestUser> insert = new Insert<>();
        insert.setBeanClass(TestUser.class);
        insert.setSqlBeanMeta(sqlBeanMeta);
        
        insert.column(new Column("id"), new Column("name"), new Column("email"))
              .values("1", "ColumnUser1", "col1@example.com")
              .values("2", "ColumnUser2", "col2@example.com");
        
        String sql = SqlHelper.buildInsertSql(insert);
        assertNotNull("INSERT SQL should not be null", sql);
        assertTrue("INSERT SQL should start with INSERT", sql.toUpperCase().startsWith("INSERT INTO"));
        assertTrue("INSERT SQL should contain both users", sql.contains("ColumnUser1"));
        assertTrue("INSERT SQL should contain both users", sql.contains("ColumnUser2"));
        
        logger.info("INSERT SQL (Column mode): " + sql);
        logger.info("fieldAndValuesSql Column mode test passed");
    }

    /**
     * 测试 fieldAndValuesSql 方法拆分后的功能正确性（单条插入）
     */
    @Test
    public void testFieldAndValuesSqlSingleInsert() {
        logger.info("=== Testing fieldAndValuesSql with single insert ===");
        
        Insert<TestUser> insert = new Insert<>();
        insert.setBeanClass(TestUser.class);
        insert.setSqlBeanMeta(sqlBeanMeta);
        
        TestUser user = new TestUser();
        user.setId("single_id");
        user.setName("SingleUser");
        user.setEmail("single@example.com");
        insert.setBean(user);
        
        String sql = SqlHelper.buildInsertSql(insert);
        assertNotNull("INSERT SQL should not be null", sql);
        assertTrue("INSERT SQL should start with INSERT", sql.toUpperCase().startsWith("INSERT INTO"));
        assertTrue("INSERT SQL should contain user data", sql.contains("SingleUser"));
        
        logger.info("INSERT SQL (Single): " + sql);
        logger.info("fieldAndValuesSql single insert test passed");
    }

    /**
     * 测试 fieldAndValuesSql 方法拆分后 Column 模式的错误处理
     */
    @Test
    public void testFieldAndValuesSqlColumnModeErrorHandling() {
        logger.info("=== Testing fieldAndValuesSql Column mode error handling ===");
        
        Insert<TestUser> insert = new Insert<>();
        insert.setBeanClass(TestUser.class);
        insert.setSqlBeanMeta(sqlBeanMeta);
        
        // 测试没有指定列的情况
        try {
            insert.values("1", "test", "test@example.com");
            SqlHelper.buildInsertSql(insert);
            fail("Should throw exception when columns not specified");
        } catch (SqlBeanException e) {
            assertTrue("Exception message should mention columns", 
                      e.getMessage().contains("字段"));
            logger.info("Caught expected exception for missing columns: " + e.getMessage());
        }
        
        // 测试值数量与列数量不匹配的情况
        insert.column(new Column("id"), new Column("name"));
        insert.values("1", "test", "extra_value");
        
        try {
            SqlHelper.buildInsertSql(insert);
            fail("Should throw exception when value count mismatch");
        } catch (SqlBeanException e) {
            assertTrue("Exception message should mention count mismatch", 
                      e.getMessage().contains("不一致"));
            logger.info("Caught expected exception for value count mismatch: " + e.getMessage());
        }
        
        logger.info("fieldAndValuesSql Column mode error handling test passed");
    }

    /**
     * 测试 Oracle 数据库的多行插入语法
     */
    @Test
    public void testFieldAndValuesSqlOracleMultiInsert() {
        logger.info("=== Testing fieldAndValuesSql Oracle multi-insert ===");
        
        SqlBeanMeta oracleMeta = new SqlBeanMeta();
        oracleMeta.setDbType(DbType.Oracle);
        oracleMeta.setSqlBeanConfig(sqlBeanConfig);
        
        Insert<TestUser> insert = new Insert<>();
        insert.setBeanClass(TestUser.class);
        insert.setSqlBeanMeta(oracleMeta);
        
        List<TestUser> userList = new ArrayList<>();
        TestUser user1 = new TestUser();
        user1.setId("1");
        user1.setName("OracleUser1");
        userList.add(user1);
        
        TestUser user2 = new TestUser();
        user2.setId("2");
        user2.setName("OracleUser2");
        userList.add(user2);
        
        insert.setBean(userList);
        
        String sql = SqlHelper.buildInsertSql(insert);
        assertNotNull("INSERT SQL should not be null", sql);
        assertTrue("Oracle INSERT should contain INSERT ALL", sql.toUpperCase().contains("INSERT ALL"));
        assertTrue("Oracle INSERT should contain INTO for each row", sql.toUpperCase().contains("INTO"));
        assertTrue("Oracle INSERT should contain SELECT DUAL", sql.toUpperCase().contains("SELECT 1 FROM DUAL"));
        
        logger.info("Oracle INSERT SQL: " + sql);
        logger.info("fieldAndValuesSql Oracle multi-insert test passed");
    }

    /**
     * 测试表名和 schema 名的边界情况
     */
    @Test
    public void testTableNameBoundaryCases() {
        logger.info("=== Testing table name boundary cases ===");
        
        // 测试空表名
        Drop drop = new Drop();
        drop.setSqlBeanMeta(sqlBeanMeta);
        
        Table emptyTable = new Table();
        emptyTable.setName("");
        emptyTable.setSchema("");
        drop.setTable(emptyTable);
        
        // 空表名应该能通过校验（虽然可能在后续处理中出错）
        String sql = SqlHelper.buildDrop(drop);
        assertNotNull("DROP SQL should not be null", sql);
        logger.info("DROP SQL with empty table: " + sql);
        
        // 测试合法字符边界
        Table boundaryTable = new Table();
        boundaryTable.setName("_valid");
        boundaryTable.setSchema("$test");
        drop.setTable(boundaryTable);
        
        sql = SqlHelper.buildDrop(drop);
        assertNotNull("DROP SQL should not be null", sql);
        assertTrue("DROP SQL should contain table name", sql.contains("_valid"));
        logger.info("DROP SQL with boundary characters: " + sql);
        
        logger.info("Table name boundary cases test passed");
    }

    /**
     * 测试实体类
     */
    @SqlTable("t_test_user")
    public static class TestUser {
        
        @SqlId
        @SqlColumn("id")
        private String id;
        
        @SqlColumn("name")
        private String name;
        
        @SqlColumn("email")
        private String email;

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

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}