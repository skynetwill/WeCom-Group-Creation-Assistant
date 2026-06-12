package com.school.wechatgroup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.school.wechatgroup.config.RabbitMQConfig.GROUP_CREATE_QUEUE;

/**
 * 消息生产者 — 将建群任务发送到 RabbitMQ 队列
 * 对标企业级异步解耦模式
 */
@Service
public class MessageProducer {

    private static final Logger log = LoggerFactory.getLogger(MessageProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public MessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送建群任务到队列（异步处理）
     */
    public void sendGroupCreateTask(Map<String, Object> taskPayload) {
        rabbitTemplate.convertAndSend(GROUP_CREATE_QUEUE, taskPayload);
        log.info("建群任务已入队: groupName={}", taskPayload.get("groupName"));
    }
}
