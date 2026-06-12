package com.school.wechatgroup.demo;

/**
 * 多数据源 & 读写分离 面试必考
 *
 * 为什么需要读写分离？
 *   主库 (Master): 处理写入（INSERT/UPDATE/DELETE）
 *   从库 (Slave): 处理查询（SELECT）
 *   原因: 写入必须同步到所有节点，查询随便读就行
 *         → 写操作放主库，读操作放从库，互不影响
 *
 * 实现方式:
 *
 *   1. 中间件层（ShardingSphere / MyCat）:
 *      应用 → ShardingSphere → 自动路由读写
 *      应用无感知，配置即可
 *
 *   2. 应用层（AbstractRoutingDataSource）:
 *      @ReadOnly 注解标记读操作 → AOP 拦截 → 路由到从库
 *
 *   3. 本项目演示（配置即可）:
 *      application-docker.properties 配置:
 *        spring.datasource.master.url=jdbc:mysql://mysql-master:3306/wechat_group
 *        spring.datasource.slave.url=jdbc:mysql://mysql-slave:3306/wechat_group
 *
 *      AbstractRoutingDataSource 实现:
 *        public class DynamicDataSource extends AbstractRoutingDataSource {
 *            @Override
 *            protected Object determineCurrentLookupKey() {
 *                return DataSourceContext.isReadOnly() ? "slave" : "master";
 *            }
 *        }
 *
 * 面试官追问: 主从延迟怎么办？
 *   答: 写入后立即读 → 可能读到旧数据
 *      解决: 1. 关键读走主库  2. 等半同步复制确认  3. 业务能容忍延迟
 */
public class MultiDataSourceDemo {
    // 纯文档类 — 具体配置在 application-docker.properties + AbstractRoutingDataSource
}
