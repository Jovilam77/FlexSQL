package cn.vonce.sql.test;

import cn.vonce.sql.bean.Column;
import cn.vonce.sql.uitls.SqlBeanUtil;
import org.junit.Test;

import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * Test for SqlBeanUtil.copy() method
 * Verifies that copying Column objects works correctly
 */
public class SqlBeanUtilCopyTest {

    private static final Logger logger = Logger.getLogger(SqlBeanUtilCopyTest.class.getName());

    /**
     * Test copying a Column object with default constructor
     */
    @Test
    public void testCopyColumnWithDefaultConstructor() {
        logger.info("=== Testing SqlBeanUtil.copy() with Column ===");
        
        // Create a Column object with some values
        Column column = new Column("tableAlias", "columnName", "columnAlias");
        column.setRemarks("This is a test column");
        
        assertNotNull("Original column should not be null", column);
        assertEquals("tableAlias", column.getTableAlias());
        assertEquals("columnName", column.getName());
        assertEquals("columnAlias", column.getAlias());
        assertEquals("This is a test column", column.getRemarks());
        
        // Copy the column
        Column newColumn = SqlBeanUtil.copy(column);
        
        // Verify the copy is not null
        assertNotNull("Copied column should not be null", newColumn);
        
        // Verify the values are copied correctly
        assertEquals("tableAlias", newColumn.getTableAlias());
        assertEquals("columnName", newColumn.getName());
        assertEquals("columnAlias", newColumn.getAlias());
        assertEquals("This is a test column", newColumn.getRemarks());
        
        // Verify it's a deep copy (not the same object)
        assertNotSame("Original and copied column should be different objects", column, newColumn);
        
        logger.info("SqlBeanUtil.copy() test passed!");
    }

    /**
     * Test copying a Column object with different constructor
     */
    @Test
    public void testCopyColumnWithDifferentConstructor() {
        logger.info("=== Testing SqlBeanUtil.copy() with Column (immutable) ===");
        
        // Create a Column object with immutable flag
        Column column = new Column(true, "tbl", "id", "primaryKey", "Primary key column", false);
        
        assertNotNull("Original column should not be null", column);
        assertTrue("Column should be immutable", column.isImmutable());
        assertEquals("tbl", column.getTableAlias());
        assertEquals("id", column.getName());
        assertEquals("primaryKey", column.getAlias());
        assertEquals("Primary key column", column.getRemarks());
        assertFalse("Name escape should be false", column.isNameEscape());
        
        // Copy the column
        Column newColumn = SqlBeanUtil.copy(column);
        
        // Verify the copy is not null
        assertNotNull("Copied column should not be null", newColumn);
        
        // Verify all values are copied correctly
        assertTrue("Copied column should be immutable", newColumn.isImmutable());
        assertEquals("tbl", newColumn.getTableAlias());
        assertEquals("id", newColumn.getName());
        assertEquals("primaryKey", newColumn.getAlias());
        assertEquals("Primary key column", newColumn.getRemarks());
        assertFalse("Name escape should be false", newColumn.isNameEscape());
        
        // Verify it's a deep copy
        assertNotSame("Original and copied column should be different objects", column, newColumn);
        
        logger.info("SqlBeanUtil.copy() immutable column test passed!");
    }

    /**
     * Test copying null returns null
     */
    @Test
    public void testCopyNull() {
        logger.info("=== Testing SqlBeanUtil.copy() with null ===");
        
        Column result = SqlBeanUtil.copy(null);
        
        assertNull("Copying null should return null", result);
        
        logger.info("SqlBeanUtil.copy() null test passed!");
    }
}