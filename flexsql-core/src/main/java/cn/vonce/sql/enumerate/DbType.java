package cn.vonce.sql.enumerate;

import cn.vonce.sql.dialect.*;
import cn.vonce.sql.uitls.StringUtil;

/**
 * 数据库类型
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2019年4月9日下午9:34:20
 */
public enum DbType {

    MySQL(new MysqlDialect(), "com.mysql.cj.jdbc.Driver"),
    MariaDB(new MysqlDialect(), "org.mariadb.jdbc.Driver"),
    SQLServer(new SqlServerDialect(), "com.microsoft.sqlserver.jdbc.SQLServerDriver"),
    Oracle(new OracleDialect(), "oracle.jdbc.OracleDriver"),
    Postgresql(new PostgresqlDialect(), "org.postgresql.Driver"),
    DB2(new DB2Dialect(), "com.ibm.db2.jcc.DB2Driver"),
    H2(new H2Dialect(), "org.h2.Driver"),
    Hsql(new HsqlDialect(), "org.hsqldb.jdbc.JDBCDriver"),
    Derby(new DerbyDialect(), "org.apache.derby.jdbc.EmbeddedDriver"),
    SQLite(new SqliteDialect(), "org.sqlite.JDBC");

    private SqlDialect sqlDialect;
    private String driverClass;

    DbType(SqlDialect sqlDialect) {
        this(sqlDialect, null);
    }

    DbType(SqlDialect sqlDialect, String driverClass) {
        this.sqlDialect = sqlDialect;
        this.driverClass = driverClass;
    }

    public SqlDialect getSqlDialect() {
        return sqlDialect;
    }

    /**
     * 获取数据库驱动类名
     *
     * @return 驱动类名
     */
    public String getDriverClass() {
        return driverClass;
    }

    public static DbType getDbType(String productName) {
        if (StringUtil.isNotEmpty(productName)) {
            productName = productName.replace(" ", "").toLowerCase();
            if (productName.indexOf(DbType.MySQL.name().toLowerCase()) >= 0) {
                return DbType.MySQL;
            } else if (productName.indexOf(DbType.MariaDB.name().toLowerCase()) >= 0) {
                return DbType.MariaDB;
            } else if (productName.indexOf(DbType.Oracle.name().toLowerCase()) >= 0) {
                return DbType.Oracle;
            } else if (productName.indexOf(DbType.SQLServer.name().toLowerCase()) >= 0) {
                return DbType.SQLServer;
            } else if (productName.indexOf(DbType.Postgresql.name().toLowerCase()) >= 0) {
                return DbType.Postgresql;
            } else if (productName.indexOf(DbType.DB2.name().toLowerCase()) >= 0) {
                return DbType.DB2;
            } else if (productName.indexOf(DbType.Derby.name().toLowerCase()) >= 0) {
                return DbType.Derby;
            } else if (productName.indexOf(DbType.SQLite.name().toLowerCase()) >= 0) {
                return DbType.SQLite;
            } else if (productName.indexOf(DbType.Hsql.name().toLowerCase()) >= 0) {
                return DbType.Hsql;
            } else if (productName.indexOf(DbType.H2.name().toLowerCase()) >= 0) {
                return DbType.H2;
            }
        }
        return null;
    }

}
