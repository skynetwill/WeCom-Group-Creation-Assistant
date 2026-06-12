package com.school.wechatgroup.demo;

/**
 * DDD (Domain Driven Design) 在本项目中的实践
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │                    DDD 四层架构                          │
 * ├────────────┬────────────────────────────────────────────┤
 * │ 接口层      │ controller/ — REST API 入口                │
 * │ (Interface) │ 接收请求、返回响应，不含业务逻辑            │
 * ├────────────┼────────────────────────────────────────────┤
 * │ 应用层      │ application/ — 用例编排                    │
 * │ (Application)│ 比如 "CreateGroupUseCase" 编排:           │
 * │            │   校验文件 → 调领域服务 → 发消息事件 → 记录审计│
 * ├────────────┼────────────────────────────────────────────┤
 * │ 领域层      │ domain/ — 核心业务逻辑                     │
 * │ (Domain)   │ domain/user/UserAggregate (聚合根)          │
 * │            │ domain/user/Email (值对象)                   │
 * │            │ domain/user/UserDomainService (领域服务)     │
 * │            │ domain/group/GroupAggregate (聚合根)         │
 * ├────────────┼────────────────────────────────────────────┤
 * │ 基础设施层  │ infrastructure/ — 技术支撑                  │
 * │ (Infra)    │ JPA Repository、MyBatis Mapper              │
 * │            │ Redis、RabbitMQ、Kafka、ES 的实现            │
 * │            │ ShardingSphere 分库分表                     │
 * └────────────┴────────────────────────────────────────────┘
 *
 * DDD 核心概念 vs 本项目实现:
 *
 *   聚合根 (Aggregate Root):
 *     UserAggregate — 外部只能通过它操作 User
 *     GroupAggregate — 外部只能通过它操作 Group
 *     规则: 聚合内的对象不能从外部直接修改
 *
 *   值对象 (Value Object):
 *     Email — 无 ID，不可变，通过值判断相等
 *     规则: new Email("a@b.com").equals(new Email("a@b.com")) == true
 *
 *   实体 (Entity):
 *     AppUser — 有 ID，可变
 *     规则: 通过 ID 判断相等，属性可以变化
 *
 *   领域服务 (Domain Service):
 *     UserDomainService — 不属于任何单个实体的业务逻辑
 *     规则: 跨聚合的操作放在领域服务
 *
 *   应用服务 (Application Service):
 *     AuthServiceImpl — 编排流程（调用领域服务 + 发审计事件）
 *     规则: 不含业务规则，只做编排
 *
 *   资源库 (Repository):
 *     AppUserRepository — 聚合根的持久化接口
 *     规则: 一个聚合只有一个 Repository
 *
 * DDD vs 传统分层:
 *   传统: Controller → Service → DAO
 *   传统的问题: 业务逻辑散落在 Service 层，没有"聚合"概念
 *   DDD: 把相关对象和规则内聚到同一个聚合里，外部通过聚合根访问
 */
public class DddDemo {
    // 纯文档类
}
