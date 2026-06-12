package com.school.wechatgroup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.school.wechatgroup.config.RabbitMQConfig.GROUP_CREATE_QUEUE;

/**
 * 消息消费者 — 从 RabbitMQ 队列中取任务并处理
 */
@Service
public class MessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageConsumer.class);

    /**
     * 监听建群队列，异步处理
     */
    @RabbitListener(queues = GROUP_CREATE_QUEUE)
    public void handleGroupCreateTask(Map<String, Object> task) {
        String groupName = (String) task.get("groupName");
        String ownerId = (String) task.get("ownerId");
        log.info("消费建群任务: groupName={}, ownerId={}", groupName, ownerId);

        // 实际调用建群逻辑（此处为示例，实际需调用 WeChatGroupService）
        try {
            Thread.sleep(1000); // 模拟 API 调用
            log.info("建群任务完成: {}", groupName);
        } catch (Exception e) {
            log.error("建群任务失败: {}", groupName, e);
            // 生产环境应配置死信队列重试
        }
    }
}
