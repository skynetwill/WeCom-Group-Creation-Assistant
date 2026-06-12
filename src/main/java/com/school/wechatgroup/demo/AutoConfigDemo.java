package com.school.wechatgroup.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 自动配置原理演示
 *
 * 面试必问题: @SpringBootApplication 做了什么？
 *   1. @EnableAutoConfiguration → 扫描 META-INF/spring.factories 中所有 AutoConfiguration 类
 *   2. 每个 AutoConfiguration 类通过 @ConditionalOnXxx 判断是否激活
 *   3. 本项目 SecurityBeanConfig 就是典型的自动配置:
 *      @ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
 *      → 配置文件中 app.auth.enabled=true 才创建安全相关 Bean
 *      → app.auth.enabled=false 时，所有安全 Bean 都不创建
 *
 * 这就是 Spring Boot "约定大于配置" 的核心机制。
 */
@Configuration
public class AutoConfigDemo {

    private static final Logger log = LoggerFactory.getLogger(AutoConfigDemo.class);

    /**
     * 演示: 仅在 dev 环境创建此 Bean，prod 环境不创建
     */
    @Bean
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dev")
    public String devOnlyBean() {
        log.info("[自动配置] devOnlyBean 已创建 — 仅开发环境生效");
        return "仅开发环境可见的 Bean";
    }

    /**
     * 演示: 仅当某个属性存在时才创建
     * 如果 application.properties 中没有 demo.feature.enabled=true，此 Bean 不创建
     */
    @Bean
    @ConditionalOnProperty(name = "demo.feature.enabled", havingValue = "true", matchIfMissing = false)
    public String conditionalFeatureBean() {
        log.info("[自动配置] conditionalFeatureBean 已创建 — 仅 demo.feature.enabled=true 时生效");
        return "按需激活的功能 Bean";
    }
}
