package com.school.wechatgroup.controller;

import com.school.wechatgroup.annotation.RequireRole;
import com.school.wechatgroup.entity.AuditLog;
import com.school.wechatgroup.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志查询接口（仅管理员）
 */
@RestController
@RequestMapping("/api/admin/audit")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    @RequireRole("ADMIN")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Map<String, Object> result = new HashMap<>();
        Page<AuditLog> logs = auditLogRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        result.put("success", true);
        result.put("logs", logs.getContent());
        result.put("total", logs.getTotalElements());
        result.put("page", page);
        return result;
    }

    @GetMapping("/user/{username}")
    @RequireRole("ADMIN")
    public Map<String, Object> byUser(@PathVariable String username) {
        Map<String, Object> result = new HashMap<>();
        List<AuditLog> logs = auditLogRepository.findByUsernameOrderByCreatedAtDesc(username);
        result.put("success", true);
        result.put("logs", logs);
        return result;
    }

    @GetMapping("/event/{eventType}")
    @RequireRole("ADMIN")
    public Map<String, Object> byEvent(
            @PathVariable String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Map<String, Object> result = new HashMap<>();
        Page<AuditLog> logs = auditLogRepository.findByEventTypeOrderByCreatedAtDesc(
                eventType, PageRequest.of(page, size));
        result.put("success", true);
        result.put("logs", logs.getContent());
        result.put("total", logs.getTotalElements());
        return result;
    }
}
