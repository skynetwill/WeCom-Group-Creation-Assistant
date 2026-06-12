package com.school.wechatgroup.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Elasticsearch 搜索服务
 * 演示场景：快速全文搜索审计日志（替代 SQL LIKE）
 *
 * 开启方式: application.properties 中设置 spring.elasticsearch.enabled=true
 */
@Service
@ConditionalOnProperty(name = "spring.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final AuditSearchRepository searchRepository;

    public SearchService(AuditSearchRepository searchRepository) {
        this.searchRepository = searchRepository;
        log.info("Elasticsearch 搜索引擎已激活");
    }

    /** 全文搜索 — ES 核心能力 */
    public List<AuditDocument> fullTextSearch(String keyword) {
        return searchRepository.findByDetailContaining(keyword);
    }

    /** 按条件组合查询 */
    public List<AuditDocument> searchByUser(String username) {
        return searchRepository.findByUsername(username);
    }

    /** 索引一条日志（建群成功/失败时调用） */
    public void index(AuditDocument doc) {
        searchRepository.save(doc);
    }

    /** 统计事件分布 */
    public long countByEvent(String eventType) {
        List<AuditDocument> docs = searchRepository.findByEventType(eventType);
        return docs.size();
    }
}
