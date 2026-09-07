package cn.vonce.sql.java.datasource;

import javax.sql.DataSource;

/**
 * FlexSQL 数据源提供者接口
 * 支持多种连接池实现（HikariCP、Druid、C3P0、DBCP2等）
 * 通过 SPI 机制加载，支持动态检测连接池类型
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
public interface FlexDataSourceProvider {

    /**
     * 判断是否支持指定的连接池类型
     * 通过 Class.forName() 检测是否存在连接池类
     *
     * @param poolType 连接池类型（hikari、druid、c3p0、dbcp2 或完整类名）
     * @return 是否支持
     */
    boolean supports(String poolType);

    /**
     * 创建数据源
     *
     * @param config 数据源配置
     * @return 创建的数据源
     */
    DataSource createDataSource(FlexDataSourceConfig config);

    /**
     * 获取连接池类型标识
     *
     * @return 连接池类型标识（hikari、druid、c3p0、dbcp2）
     */
    String getPoolType();

    /**
     * 获取优先级（数字越小优先级越高）
     * 用于自动检测时选择默认连接池
     *
     * @return 优先级
     */
    int getPriority();

}