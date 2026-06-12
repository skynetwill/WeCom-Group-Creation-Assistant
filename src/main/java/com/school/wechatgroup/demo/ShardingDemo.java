package com.school.wechatgroup.demo;

/**
 * ShardingSphere 分库分表演示
 *
 * 为什么要分库分表？
 * ┌──────────────────────────────────────────────────────────┐
 * │  单表 100 万行: 查询 < 0.1s ✓                             │
 * │  单表 1000 万行: 查询 0.5s~1s，索引开始吃力               │
 * │  单表 1 亿行: 查询 5~30s，必须分表                         │
 * └──────────────────────────────────────────────────────────┘
 *
 * 本项目分片策略:
 *
 *   t_audit_log → 按月分表
 *     t_audit_log_202601  (1月审计日志)
 *     t_audit_log_202602  (2月审计日志)
 *     t_audit_log_202603  (3月审计日志)
 *     ...
 *
 *   分片键: created_at（创建时间）
 *   路由算法: 提取 YYYYMM → 路由到对应月份的表
 *
 * 分库分表中间件选型:
 *
 *   ShardingSphere-JDBC:
 *     jar 包形式，嵌入应用，无额外部署
 *     适合: 中小规模，应用可控
 *
 *   ShardingSphere-Proxy:
 *     独立进程，对应用透明（像普通 MySQL 一样连接）
 *     适合: 大规模，统一管理
 *
 *   企业级方案:
 *     MyCat / DBLE / Vitess / TiDB
 *
 * 面试题: 分库分表后怎么跨表查询？
 *   答: 避免跨表！按查询维度选择分片键。
 *      - 按 created_at 分表 → 查"最近一个月"很快
 *      - 查"某个用户的全部日志" → 需要全表扫描 → 用 ES 代替！
 *      这就是 ES + 分库分表配合使用的经典场景。
 *
 * 面试题: 分表后怎么分页？
 *   答: ShardingSphere 自动合并，但深度分页性能差。
 *      SELECT * FROM t_audit_log ORDER BY created_at LIMIT 10000,10
 *      → ShardingSphere 从每个分表取 10010 条 → 内存合并排序 → 取第 10000-10010 条
 *      → 分表越多越慢！解决方案: 用 ES 搜索 + 返回 ID + 回表查详情
 */
public class ShardingDemo {
    // 纯文档类
}
