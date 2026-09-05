package cn.vonce.sql.constant;

/**
 * 关键字常量
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 */
public class SqlConstant {

    // sql
    public static final String SELECT = "SELECT ";
    public static final String COUNT = "COUNT";
    public static final String SELECT_DISTINCT = "SELECT DISTINCT ";
    public static final String FROM = " FROM ";
    public static final String WHERE = " WHERE ";
    public static final String GROUP_BY = " GROUP BY ";
    public static final String HAVING = " HAVING ";
    public static final String ORDER_BY = " ORDER BY ";
    public static final String UNION = " UNION ";
    public static final String UNION_ALL = " UNION ALL ";
    public static final String LIMIT = " LIMIT ";
    public static final String BEGIN_BRACKET = "(";
    public static final String END_BRACKET = ")";
    public static final String AND_BRACKET = ") AND (";
    public static final String OR_BRACKET = ") OR (";
    public static final String AND = " AND ";
    public static final String OR = " OR ";
    public static final String UPDATE = "UPDATE ";
    public static final String SET = " SET ";
    public static final String DATA_TYPE = "DATA TYPE ";
    public static final String VALUES = "VALUES";
    public static final String NULL_VALUE = "NULL";
    public static final String INSERT_INTO = "INSERT INTO ";
    public static final String DELETE_FROM = "DELETE FROM ";
    public static final String IS = " IS ";
    public static final String IS_NOT = " IS NOT ";
    public static final String IN = " IN (";
    public static final String NOT_IN = " NOT IN (";
    //    public static final String EXISTS = " EXISTS (";
//    public static final String NOT_EXISTS = " NOT EXISTS (";
    public static final String LIKE = " LIKE ";
    public static final String NOT_LIKE = " NOT LIKE ";
    public static final String PERCENT = "%";
    public static final String BETWEEN = " BETWEEN ";
    public static final String GREATER_THAN = " > ";
    public static final String GREAT_THAN_OR_EQUAL_TO = " >= ";
    public static final String LESS_THAN = " < ";
    public static final String LESS_THAN_OR_EQUAL_TO = " <= ";
    public static final String EQUAL_TO = " = ";
    public static final String ADDITION = " + ";
    public static final String SUBTRACT = " - ";
    public static final String NOT_EQUAL_TO = " <> ";
    public static final String SPACES = " ";
    public static final String COMMA = ", ";
    public static final String ALL = " * ";
    public static final String SINGLE_ESCAPE_CHARACTER = "`";
    public static final String POINT = ".";
    public static final String UNDERLINE = "_";
    //    public static final String WELL_NUMBER = "#";
    public static final String AS = " AS ";
    public static final String SINGLE_QUOTATION_MARK = "'";
    public static final String INNER_JOIN = " INNER JOIN ";
    public static final String LEFT_JOIN = " LEFT JOIN ";
    public static final String RIGHT_JOIN = " RIGHT JOIN ";
    public static final String FULL_JOIN = " FULL JOIN ";
    public static final String ON = " ON ";

    public static final String CREATE_TABLE = "CREATE TABLE ";
    public static final String PRIMARY_KEY = "PRIMARY KEY";
    public static final String AUTO_INCREMENT = "AUTO_INCREMENT";
    public static final String NULL = "NULL";
    public static final String NOT_NULL = "NOT NULL";
    public static final String DEFAULT = "DEFAULT";
    public static final String COMMENT = "COMMENT";

    public static final String ALTER_TABLE = "ALTER TABLE ";
    public static final String TABLE = "TABLE ";
    public static final String ALTER = "ALTER ";
    public static final String COLUMN = "COLUMN ";
    public static final String TO = " TO ";
    public static final String ADD = "ADD ";
    public static final String CHANGE = "CHANGE ";
    public static final String RENAME = "RENAME ";
    public static final String RENAME_TO = "RENAME TO ";
    public static final String MODIFY = "MODIFY ";
    public static final String DROP = "DROP ";
    public static final String AFTER = "AFTER ";
    public static final String FOR = "FOR ";

    // mssql
    public static final String TOP = " TOP ";
    public static final String ROW_NUMBER = " ROW_NUMBER() ";
    public static final String OVER = " OVER ";
    public static final String ID = "ID";
    public static final String ROWNUM = " ROWNUM ";
    public static final String T = " T";
    public static final String EXEC_SP_RENAME = "EXEC sp_rename ";
    public static final String SEMICOLON = ";";
    public static final String BEGIN_SQUARE_BRACKETS = "[";
    public static final String END_SQUARE_BRACKETS = "]";
    public static final String DBO = "dbo";

    // oracle
    public static final String RN = "RN";
    public static final String TB = " TB ";
    public static final String DOUBLE_ESCAPE_CHARACTER = "\"";
    public static final String INSERT_ALL_INTO = "INSERT ALL INTO ";
    public static final String INTO = " INTO ";
    public static final String SELECT_DUAL = " SELECT 1 FROM DUAL ";

    public static final String OFFSET = " OFFSET ";
    public static final String ROWNUMBER = " ROWNUMBER() ";

    // sqlite
    public static final String INTEGER = "INTEGER";
    public static final String REAL = "REAL";
    public static final String TEXT = "TEXT";
    public static final String BLOB = "BLOB";

    // Postgresql
    public static final String TYPE = "TYPE ";
    public static final String COLLATE = "COLLATE ";
    public static final String PG_CATALOG = "PG_CATALOG";
    public static final String USING = " USING ";
    public static final String DOUBLE_COLON = "::";
    public static final String CONSTRAINT = "CONSTRAINT ";
    public static final String PKEY = "pkey";

    // upsert / merge
    public static final String ON_DUPLICATE_KEY_UPDATE = " ON DUPLICATE KEY UPDATE ";
    public static final String ON_CONFLICT = " ON CONFLICT ";
    public static final String DO_UPDATE_SET = " DO UPDATE SET ";
    public static final String DO_NOTHING = " DO NOTHING";
    public static final String EXCLUDED = "EXCLUDED";
    public static final String MERGE_INTO = "MERGE INTO ";
    public static final String WHEN_MATCHED = " WHEN MATCHED THEN UPDATE SET ";
    public static final String WHEN_NOT_MATCHED = " WHEN NOT MATCHED THEN INSERT ";
    public static final String VALUES_FUNC = "VALUES";
    public static final String UNION_ALL_SPACE = " UNION ALL ";

}
