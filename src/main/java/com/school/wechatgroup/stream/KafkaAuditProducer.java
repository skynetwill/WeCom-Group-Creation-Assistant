package com.school.wechatgroup.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka 消息生产者 — 审计事件流
 * 演示场景：将审计日志发送到 Kafka，由多个消费者并行处理
 *
 * 开启方式: application.properties 中设置 spring.kafka.enabled=true
 *
 * 企业级场景:
 *   1. 用户操作 → Kafka topic → 多个消费者
 *   2. 消费者A: 写入数据库
 *   3. 消费者B: 写入 ES（搜索用）
 *   4. 消费者C: 实时风控分析
 *   5. 消费者D: 数据仓库（大数据分析）
 */
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaAuditProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaAuditProducer.class);
    private static final String TOPIC = "audit-events";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaAuditProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        log.info("Kafka 消息队列已激活");
    }

    /**
     * 发送审计事件到 Kafka
     */
    public void sendAuditEvent(String username, String eventType, String detail) {
        String message = String.format("{\"username\":\"%s\",\"eventType\":\"%s\",\"detail\":\"%s\",\"ts\":%d}",
                username, eventType, detail, System.currentTimeMillis());

        kafkaTemplate.send(TOPIC, username, message);
        log.debug("审计事件已发送到 Kafka: {} - {}", eventType, username);
    }
}
