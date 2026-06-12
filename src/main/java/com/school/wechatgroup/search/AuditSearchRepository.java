package com.school.wechatgroup.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ES 审计日志搜索仓库
 * 仅在 spring.elasticsearch.enabled=true 时激活
 */
@Repository
@ConditionalOnProperty(name = "spring.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public interface AuditSearchRepository extends ElasticsearchRepository<AuditDocument, String> {

    /** 按用户名搜索 */
    List<AuditDocument> findByUsername(String username);

    /** 按事件类型搜索 */
    List<AuditDocument> findByEventType(String eventType);

    /** 全文搜索详情（分词匹配） */
    List<AuditDocument> findByDetailContaining(String keyword);

    /** 按状态搜索 */
    List<AuditDocument> findByStatus(String status);
}
