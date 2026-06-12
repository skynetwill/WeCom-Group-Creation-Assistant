package com.school.wechatgroup.demo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Spring Bean 生命周期演示
 *
 * 执行顺序（面试必考题）:
 *   1. 构造器 new BeanLifecycleDemo()
 *   2. @Autowired 注入依赖
 *   3. @PostConstruct init()
 *   4. InitializingBean.afterPropertiesSet()
 *   5. Bean 就绪，提供服务
 *   6. @PreDestroy cleanup()
 *   7. DisposableBean.destroy()
 *
 * 启动时看日志即可验证顺序。
 */
@Component
public class BeanLifecycleDemo implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(BeanLifecycleDemo.class);

    public BeanLifecycleDemo() {
        log.info("[生命周期] 1. 构造器执行 — Bean 实例化");
    }

    @PostConstruct
    public void init() {
        log.info("[生命周期] 3. @PostConstruct — 依赖注入完成，自定义初始化");
    }

    @Override
    public void afterPropertiesSet() {
        log.info("[生命周期] 4. InitializingBean.afterPropertiesSet() — Spring 接口回调");
    }

    @PreDestroy
    public void cleanup() {
        log.info("[生命周期] 6. @PreDestroy — 容器关闭前清理");
    }

    @Override
    public void destroy() {
        log.info("[生命周期] 7. DisposableBean.destroy() — Spring 接口回调销毁");
    }
}
