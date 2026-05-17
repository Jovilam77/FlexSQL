package cn.vonce.sql.config;

import cn.vonce.sql.enumerate.DbType;

import java.io.Serializable;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 数据库信息
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 */
public class SqlBeanMeta implements Serializable {

    /**
     * 数据库类型
     */
    private DbType dbType;
    /**
     * 数据库名称
     */
    private String productName;
    /**
     * 数据库主版本
     */
    private int databaseMajorVersion;
    /**
     * 数据库小版本
     */
    private int databaseMinorVersion;
    /**
     * 数据库产品版本
     */
    private String databaseProductVersion;
    /**
     * jdbc驱动主版本
     */
    private int jdbcMajorVersion;
    /**
     * jdbc驱动小版本
     */
    private int jdbcMinorVersion;
    /**
     * 驱动
     */
    private int driverMajorVersion;
    /**
     * 驱动
     */
    private int driverMinorVersion;
    /**
     * 驱动版本
     */
    private String driverVersion;
    /**
     * 驱动名称
     */
    private String driverName;
    /**
     * 数据库配置
     */
    private SqlBeanConfig sqlBeanConfig;

    public DbType getDbType() {
        return dbType;
    }

    public void setDbType(DbType dbType) {
        if (this.dbType == null) {
            this.dbType = dbType;
        }
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getDatabaseMajorVersion() {
        return databaseMajorVersion;
    }

    public void setDatabaseMajorVersion(int databaseMajorVersion) {
        this.databaseMajorVersion = databaseMajorVersion;
    }

    public int getDatabaseMinorVersion() {
        return databaseMinorVersion;
    }

    public void setDatabaseMinorVersion(int databaseMinorVersion) {
        this.databaseMinorVersion = databaseMinorVersion;
    }

    public String getDatabaseProductVersion() {
        return databaseProductVersion;
    }

    public void setDatabaseProductVersion(String databaseProductVersion) {
        this.databaseProductVersion = databaseProductVersion;
    }

    public int getJdbcMajorVersion() {
        return jdbcMajorVersion;
    }

    public void setJdbcMajorVersion(int jdbcMajorVersion) {
        this.jdbcMajorVersion = jdbcMajorVersion;
    }

    public int getJdbcMinorVersion() {
        return jdbcMinorVersion;
    }

    public void setJdbcMinorVersion(int jdbcMinorVersion) {
        this.jdbcMinorVersion = jdbcMinorVersion;
    }

    public int getDriverMajorVersion() {
        return driverMajorVersion;
    }

    public void setDriverMajorVersion(int driverMajorVersion) {
        this.driverMajorVersion = driverMajorVersion;
    }

    public int getDriverMinorVersion() {
        return driverMinorVersion;
    }

    public void setDriverMinorVersion(int driverMinorVersion) {
        this.driverMinorVersion = driverMinorVersion;
    }

    public String getDriverVersion() {
        return driverVersion;
    }

    public void setDriverVersion(String driverVersion) {
        this.driverVersion = driverVersion;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public SqlBeanConfig getSqlBeanConfig() {
        return sqlBeanConfig;
    }

    public void setSqlBeanConfig(SqlBeanConfig sqlBeanConfig) {
        if (this.sqlBeanConfig == null) {
            this.sqlBeanConfig = sqlBeanConfig;
        }
    }

    public static SqlBeanMeta build(SqlBeanConfig sqlBeanConfig, DatabaseMetaData metaData) throws SQLException {
        SqlBeanMeta sqlBeanMeta = new SqlBeanMeta();
        sqlBeanMeta.setProductName(metaData.getDatabaseProductName());
        sqlBeanMeta.setDatabaseMajorVersion(metaData.getDatabaseMajorVersion());
        sqlBeanMeta.setDatabaseMinorVersion(metaData.getDatabaseMinorVersion());
        sqlBeanMeta.setDatabaseProductVersion(metaData.getDatabaseProductVersion());
        sqlBeanMeta.setJdbcMajorVersion(metaData.getJDBCMajorVersion());
        sqlBeanMeta.setJdbcMinorVersion(metaData.getJDBCMinorVersion());
        sqlBeanMeta.setDriverMajorVersion(metaData.getDriverMajorVersion());
        sqlBeanMeta.setDriverMinorVersion(metaData.getDriverMinorVersion());
        sqlBeanMeta.setDriverVersion(metaData.getDriverVersion());
        sqlBeanMeta.setDriverName(metaData.getDriverName());
        sqlBeanMeta.setDbType(DbType.getDbType(sqlBeanMeta.getProductName()));
        //如果用户未进行配置
        if (sqlBeanConfig == null) {
            sqlBeanMeta.setSqlBeanConfig(new SqlBeanConfig());
            switch (Objects.requireNonNull(sqlBeanMeta.getDbType())) {
                case Oracle:
                case DB2:
                case Derby:
                case Hsql:
                case H2:
                    sqlBeanMeta.getSqlBeanConfig().setToUpperCase(true);
                    break;
            }
        } else {
            sqlBeanMeta.setSqlBeanConfig(sqlBeanConfig);
        }
        return sqlBeanMeta;
    }

    @Override
    public String toString() {
        return "SqlBeanMeta{" +
                "dbType=" + dbType +
                ", productName='" + productName + '\'' +
                ", databaseMajorVersion=" + databaseMajorVersion +
                ", databaseMinorVersion=" + databaseMinorVersion +
                ", databaseProductVersion='" + databaseProductVersion + '\'' +
                ", jdbcMajorVersion=" + jdbcMajorVersion +
                ", jdbcMinorVersion=" + jdbcMinorVersion +
                ", driverMajorVersion=" + driverMajorVersion +
                ", driverMinorVersion=" + driverMinorVersion +
                ", driverVersion='" + driverVersion + '\'' +
                ", driverName='" + driverName + '\'' +
                ", sqlBeanConfig=" + sqlBeanConfig +
                '}';
    }

}
