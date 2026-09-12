package cn.vonce.sql.spring.service;

/**
 * 「插入并回填自增id」集成测试用的 Service。
 * <p>故意不注册任何事务切面（无 {@code @EnableTransactionManagement}、无 flexsql 多数据源配置），
 * 用于复现「无 Spring 事务」这一原先必然报 connection closed 的场景。</p>
 */
public class ItUserService extends MybatisSqlBeanServiceImpl<ItUser, Long> {
}
