package cn.vonce.sql.spring.config;

import cn.vonce.sql.cache.CacheableSqlBeanService;
import cn.vonce.sql.cache.SqlBeanServices;
import cn.vonce.sql.service.SqlBeanService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

/**
 * 查询缓存自动包裹后置处理器。
 * <p>在 {@link SqlBeanService} bean 初始化后，用 {@link SqlBeanServices#caching(SqlBeanService)} 包裹；
 * 若全局 {@link cn.vonce.sql.cache.QueryCacheConfig} 为 {@link cn.vonce.sql.config.CacheMode#OFF}（默认），caching 返回原对象，零影响。</p>
 * <p>实现 {@link PriorityOrdered} 并以最高优先级运行，确保在 AOP 代理（如 @Transactional / @Aspect）之前包裹原始 bean，
 * 从而避免对 AOP 生成的 JDK 代理调用 getSqlBeanMeta() 时方法不在接口上而不可达的问题。</p>
 *
 * @author Jovi
 * @version 1.0
 */
public class CacheableSqlBeanServicePostProcessor implements BeanPostProcessor, PriorityOrdered {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof SqlBeanService && !CacheableSqlBeanService.isCacheProxy(bean)) {
            SqlBeanService<?, ?> service = (SqlBeanService<?, ?>) bean;
            SqlBeanService<?, ?> cached = SqlBeanServices.caching(service);
            if (cached != service) {
                return cached;
            }
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
