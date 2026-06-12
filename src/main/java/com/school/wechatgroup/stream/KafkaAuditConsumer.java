package com.school.wechatgroup.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka 消息消费者 — 并行处理审计事件
 *
 * 企业级架构:
 *   1. 同一个 topic 可以有多个 consumer group
 *   2. 同一个 group 内，每个 partition 只有一个 consumer（保证顺序）
 *   3. 不同的 group 独立消费（写入 DB、写入 ES、风控分析）
 */
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaAuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaAuditConsumer.class);

    /**
     * 消费审计事件（写入数据库的消费者）
     */
    @KafkaListener(topics = "audit-events", groupId = "db-writer")
    public void handleDBWrite(String message) {
        log.info("[DB消费者] 收到审计事件: {}", message);
        // 实际写入数据库（本例中演示，AuditService 已同步写入）
    }

    /**
     * 消费审计事件（写入 ES 的消费者）
     */
    @KafkaListener(topics = "audit-events", groupId = "es-writer")
    public void handleESWrite(String message) {
        log.info("[ES消费者] 收到审计事件，准备索引到 ES: {}", message);
        // 实际写入 Elasticsearch
    }

    /**
     * 消费审计事件（实时风控分析）
     */
    @KafkaListener(topics = "audit-events", groupId = "risk-monitor")
    public void handleRiskMonitor(String message) {
        // 演示场景：检测异常登录频率
        log.debug("[风控消费者] 分析事件: {}", message);
    }
}
