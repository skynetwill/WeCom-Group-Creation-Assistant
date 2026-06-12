package com.school.wechatgroup.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * ShardingSphere 分库分表配置 — 演示概念
 *
 * 启用方式: spring.profiles.active=docker-sharding
 *
 * 实际配置（YAML 或 Java API）:
 *   sharding:
 *     tables:
 *       t_audit_log:
 *         actualDataNodes: ds0.t_audit_log_$->{202601..202612}
 *         tableStrategy:
 *           standard:
 *             shardingColumn: created_at
 *             shardingAlgorithmName: month_algorithm
 *       t_group_operation:
 *         actualDataNodes: ds0.t_group_operation_$->{202601..202612}
 *         tableStrategy:
 *           standard:
 *             shardingColumn: created_at
 *             shardingAlgorithmName: month_algorithm
 *
 *   为什么分表？
 *     审计日志和操作记录无限制增长 → 单表百万级后查询变慢
 *     按月分表 → 查指定月份直接定位对应表，速度不受总量影响
 *
 *   ShardingSphere 三种使用方式:
 *     JDBC (本项目): jar 包嵌入应用，配置驱动
 *     Proxy: 独立进程，对应用透明
 *     Driver: JDBC 驱动替换，零侵入
 */
@Configuration
@Profile("docker-sharding")
public class ShardingConfig {

    private static final Logger log = LoggerFactory.getLogger(ShardingConfig.class);

    public ShardingConfig() {
        log.info("[分库分表] ShardingSphere 配置已加载（docker-sharding profile）");
        log.info("  分片表: t_audit_log → 按月分表 t_audit_log_202601~202612");
        log.info("  分片表: t_group_operation → 按月分表 t_group_operation_202601~202612");
    }
}
