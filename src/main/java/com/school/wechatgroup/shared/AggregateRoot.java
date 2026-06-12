package com.school.wechatgroup.shared;

/**
 * DDD 聚合根标记接口
 *
 * 聚合根 = 外部访问聚合的唯一入口
 * 聚合内的其他实体/值对象不能直接从外部访问
 */
public interface AggregateRoot<ID> {
    ID getId();
}
