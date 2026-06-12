package com.school.wechatgroup.controller;

import com.school.wechatgroup.annotation.RequireRole;
import com.school.wechatgroup.config.SessionRegistryConfig.SessionRegistry;
import com.school.wechatgroup.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员会话管理接口
 * 对标 Spring Security SessionRegistry 的管理功能
 */
@RestController
@RequestMapping("/api/admin/sessions")
public class AdminSessionController {

    private static final Logger log = LoggerFactory.getLogger(AdminSessionController.class);

    private final SessionRegistry sessionRegistry;

    public AdminSessionController(@Autowired(required = false) SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * 查询所有活跃会话（仅管理员）
     */
    @GetMapping
    @RequireRole("ADMIN")
    public Map<String, Object> listSessions() {
        Map<String, Object> result = new HashMap<>();
        if (sessionRegistry == null) {
            result.put("success", false);
            result.put("message", "会话管理未启用");
            return result;
        }

        List<Map<String, Object>> sessions = sessionRegistry.getAllActiveSessions();
        result.put("success", true);
        result.put("sessions", sessions);
        result.put("total", sessions.size());
        return result;
    }

    /**
     * 强制踢出指定用户的所有会话
     */
    @DeleteMapping("/{username}")
    @RequireRole("ADMIN")
    public Map<String, Object> expireUserSessions(@PathVariable String username) {
        Map<String, Object> result = new HashMap<>();
        if (sessionRegistry == null) {
            result.put("success", false);
            result.put("message", "会话管理未启用");
            return result;
        }

        int count = sessionRegistry.expireUserSessions(username);
        log.info("管理员强制踢出用户 {} 的 {} 个会话", username, count);
        result.put("success", true);
        result.put("expiredCount", count);
        result.put("message", "已强制下线用户 " + username + " 的 " + count + " 个会话");
        return result;
    }
}
