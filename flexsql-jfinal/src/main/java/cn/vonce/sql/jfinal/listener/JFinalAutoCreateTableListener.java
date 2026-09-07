package cn.vonce.sql.jfinal.listener;

import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.java.config.BaseAutoCreateTableListener;
import com.jfinal.aop.Aop;
import com.jfinal.kit.PropKit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JFinal自动创建表监听类
 * 
 * 使用说明：
 * 1. 服务实例必须通过 Aop.get() 或 @Inject 获取，使用 new 创建的实例不会触发 @Before 拦截器
 * 2. 自动创建表需要在配置文件中指定服务类列表：flexsql.autoCreate.services=com.example.service.UserService
 * 
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2024/8/12 15:30
 */
public class JFinalAutoCreateTableListener extends BaseAutoCreateTableListener {

    private final Lock lock = new ReentrantLock();

    public JFinalAutoCreateTableListener() {
        // 构造函数保持为空，processSqlBeanServices() 由 AutoConfigJFinal.init() 调用
    }

    /**
     * 执行自动创建表（由 AutoConfigJFinal.init() 在所有bean注册完成后调用）
     */
    public void processAutoCreate() {
        SqlBeanConfig sqlBeanConfig = getSqlBeanConfig();
        if (sqlBeanConfig == null || sqlBeanConfig.getAutoCreate()) {
            lock.lock();
            try {
                processSqlBeanServices();
            } finally {
                lock.unlock();
            }
        }
    }

    private SqlBeanConfig getSqlBeanConfig() {
        try {
            return Aop.get(SqlBeanConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(String name) {
        try {
            // JFinal的Aop.get()方法参数是Class类型，不是String
            Class<?> clazz = Class.forName(name);
            return (T) Aop.get(clazz);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getBeansForType(Class<T> baseType) {
        List<T> result = new ArrayList<>();
        
        // JFinal没有直接获取所有同类型Bean的API，需要用户手动注册
        // 这里使用一个简单的方式：尝试获取已知的服务实现类
        
        // 方式1：从配置文件中读取服务类列表
        String services = PropKit.get("flexsql.autoCreate.services");
        if (services != null && !services.isEmpty()) {
            String[] classNames = services.split(",");
            for (String className : classNames) {
                className = className.trim();
                if (!className.isEmpty()) {
                    try {
                        Class<?> clazz = Class.forName(className);
                        if (baseType.isAssignableFrom(clazz)) {
                            Object service = clazz.newInstance();
                            // 使用Aop.inject()注入依赖
                            Aop.inject(service);
                            result.add((T) service);
                        }
                    } catch (Exception e) {
                        System.err.println("FlexSQL: 无法实例化服务类 " + className + ": " + e.getMessage());
                    }
                }
            }
        }
        
        // 方式2：尝试通过Aop.get()获取
        try {
            Object bean = Aop.get(baseType);
            if (bean != null && baseType.isInstance(bean)) {
                // 检查是否已经添加过
                boolean exists = false;
                for (T t : result) {
                    if (t.getClass().equals(bean.getClass())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    result.add((T) bean);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        
        return result;
    }

    @Override
    public List<String> getBeanNamesForType(Class<?> baseType) {
        List<String> result = new ArrayList<>();
        
        // 从配置文件中读取服务类列表
        String services = PropKit.get("flexsql.autoCreate.services");
        if (services != null && !services.isEmpty()) {
            String[] classNames = services.split(",");
            for (String className : classNames) {
                className = className.trim();
                if (!className.isEmpty()) {
                    try {
                        Class<?> clazz = Class.forName(className);
                        if (baseType.isAssignableFrom(clazz)) {
                            result.add(clazz.getName());
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }
        
        // 尝试通过Aop.get()获取
        try {
            Object bean = Aop.get(baseType);
            if (bean != null) {
                String beanName = bean.getClass().getName();
                if (!result.contains(beanName)) {
                    result.add(beanName);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        
        return result;
    }

}