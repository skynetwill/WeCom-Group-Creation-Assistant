package com.school.wechatgroup.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 消息队列配置
 *
 * 队列设计：
 *   group.create.queue — 建群任务异步队列
 *   用途：用户提交建群请求后立即返回，后台异步调用企业微信 API
 */
@Configuration
public class RabbitMQConfig {

    public static final String GROUP_CREATE_QUEUE = "group.create.queue";

    @Bean
    public Queue groupCreateQueue() {
        // durable=true: 队列持久化，服务重启不丢消息
        return new Queue(GROUP_CREATE_QUEUE, true);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
