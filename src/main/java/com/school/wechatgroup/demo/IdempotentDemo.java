package com.school.wechatgroup.demo;

/**
 * 幂等性 面试必考
 *
 * 什么是幂等？
 *   同一个请求执行 1 次和执行 100 次，结果完全一样
 *
 * 为什么需要幂等？
 *   用户: 点"创建群聊" → 卡住了 → 又点一次 → 又点一次
 *   系统: 收到了 3 个相同请求 → 如果没有幂等 → 创建了 3 个群
 *   网络: 超时重试 → 服务端其实已处理 → 又处理一次
 *
 * 实现方式:
 *
 *   1. 唯一索引（数据库层面）:
 *      CREATE UNIQUE INDEX idx_chat_id ON t_wx_group(chat_id);
 *      INSERT 重复 chat_id → 数据库报错 → 业务层捕获 → 返回"已存在"
 *
 *   2. Token 机制（业务层面）:
 *      提交前: GET /api/idempotent-token → 返回 UUID
 *      提交时: 带 token → 服务端 Redis DELETE token
 *      重复提交: token 已被删除 → 返回"请勿重复提交"
 *
 *   3. 状态机（本项目可实现）:
 *      GroupOperation 表加 idempotent_key 字段
 *      INSERT ... ON DUPLICATE KEY UPDATE (MySQL)
 *      或 Redis SETNX idempotent:{key} EX 300
 *
 * 面试官追问: 消息队列消费怎么保证幂等？
 *   答: RabbitMQ 的 ack 机制 + 消费者端做幂等
 *      - 消息体中带唯一 ID (message_id)
 *      - 消费者处理前检查 Redis: SETNX processed:{message_id} EX 3600
 *      - 重复消息直接跳过
 */
public class IdempotentDemo {
    // 纯文档类 — 项目中可通过 Redis SETNX + 数据库唯一索引实现
}
