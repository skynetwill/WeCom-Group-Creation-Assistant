package com.school.wechatgroup.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AOP 代理模式演示
 *
 * JDK 动态代理 vs CGLIB 代理（面试高频）:
 *
 *   JDK 代理: 目标类必须实现接口
 *     MyService proxy = (MyService) Proxy.newProxyInstance(
 *         classLoader, new Class[]{MyService.class}, invocationHandler);
 *     → 代理对象是 com.sun.proxy.$Proxy123
 *
 *   CGLIB 代理: 通过继承目标类生成子类
 *     → 代理对象是 MyServiceImpl$$SpringCGLIB$$0
 *
 * Spring Boot 2.x+ 默认使用 CGLIB，因为不需要接口。
 *
 * 经典陷阱 — 同一个类内方法调用不触发代理:
 *   service.methodA() 内部调用 this.methodB()
 *   → this 是原始对象，不是代理
 *   → methodB 上的 @Transactional/@Cacheable 无效
 *
 * 解决方案: AopContext.currentProxy()
 */
@Service
public class AopProxyDemo {

    private static final Logger log = LoggerFactory.getLogger(AopProxyDemo.class);

    /**
     * 陷阱演示：内部调用不走代理
     */
    @Transactional
    public void callerWithBug() {
        log.info("[AOP演示] callerWithBug 被代理调用 — 事务生效");
        // BUG: this.doSomething() 直接调用，绕过代理
        // 下面的 @Transactional 不会生效
        this.doSomething();
    }

    @Transactional
    public void doSomething() {
        log.info("[AOP演示] doSomething 被直接调用 — 事务可能不生效（如果通过 this 调用）");
    }

    /**
     * 正确做法：通过代理调用
     */
    @Transactional
    public void callerWithFix() {
        log.info("[AOP演示] callerWithFix — 通过 AopContext.currentProxy() 调用");
        // 正确：通过 AOP 代理调用，事务生效
        AopProxyDemo proxy = (AopProxyDemo) AopContext.currentProxy();
        proxy.doSomething();
    }

    /**
     * 暴露代理（让 AopContext.currentProxy() 可用）
     * 需要在 application.properties 中设置:
     *   spring.aop.proxy-target-class=true
     *   spring.aop.expose-proxy=true
     */
}
