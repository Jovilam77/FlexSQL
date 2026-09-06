package cn.vonce.sql.spring.config;


import cn.vonce.sql.spring.datasource.DataSourceAspect;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用自动配置
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2019年6月25日下午12:7:50
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({MybatisAutoConfig.class, AutoCreateTableListener.class, DataSourceAspect.class, SqlBeanCacheAutoConfig.class})
@MapperScan("cn.vonce.sql.java.dao")
public @interface UseMybatis {


}
