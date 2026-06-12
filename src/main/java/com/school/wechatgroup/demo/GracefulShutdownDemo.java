package com.school.wechatgroup.demo;

/**
 * 优雅停机 面试必考
 *
 * kill -9 vs kill -15:
 *   kill -9 (SIGKILL): 操作系统直接杀进程，不给任何反应时间
 *     → @PreDestroy 不执行
 *     → 事务不回滚
 *     → 连接池泄漏
 *     → 正在处理的请求直接断开
 *
 *   kill -15 (SIGTERM): 进程收到信号，Spring Boot 优雅停机
 *     → 拒绝新请求
 *     → 等待现有请求处理完（默认 30 秒）
 *     → 关闭线程池
 *     → 关闭数据库连接池
 *     → 关闭 Redis 连接
 *     → @PreDestroy 执行
 *     → 进程退出
 *
 * 配置 (application.properties):
 *   server.shutdown=graceful
 *   spring.lifecycle.timeout-per-shutdown-phase=30s
 *
 * 验证方式:
 *   1. 发起一个慢请求（sleep 20秒）
 *   2. 同时 kill -15 进程
 *   3. 观察: 慢请求完成 → 日志打印 @PreDestroy → 进程退出
 *
 * K8s 关联:
 *   terminationGracePeriodSeconds: 30
 *   preStop hook: 通知注册中心下线 → sleep 5 → 等待流量排干
 */
public class GracefulShutdownDemo {
    // 纯文档类 — 配置在 application.properties
}
