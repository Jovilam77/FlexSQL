package cn.vonce.sql.spring.config;

import cn.vonce.sql.spring.datasource.DataSourceAspect;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用 FlexSQL 原生 JDBC 自动配置
 * 
 * 使用方式：在 Spring 配置类上添加 @UseFlexSql 注解
 * 
 * 自动配置内容：
 * 1. FlexSQL 数据源（使用标准配置前缀 spring.datasource.*）
 * 2. SqlBeanMeta（用于 SQL 生成）
 * 3. DataSourceTransactionManager（Spring 标准事务管理器）
 * 4. SpringFlexJdbcTemplate（支持 Spring 事务感知）
 * 
 * 用户可以使用：
 * - 单数据源：Spring 的 @Transactional 注解
 * - 多数据源：FlexSQL 的 @DbTransactional 注解（需启用 EnableAutoConfigMultiDataSource）
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:03
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({FlexSqlConfig.class, AutoCreateTableListener.class, DataSourceAspect.class})
public @interface UseFlexSql {

}
