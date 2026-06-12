package com.school.wechatgroup.service;

import com.school.wechatgroup.entity.AuditLog;
import com.school.wechatgroup.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 审计日志服务（对标 Spring Security AuditEventPublisher）
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    public void logLoginSuccess(String username, String clientIp) {
        save(username, "LOGIN_SUCCESS", "用户登录成功", clientIp, "SUCCESS");
    }

    @Async
    public void logLoginFailure(String username, String clientIp, String reason) {
        save(username != null ? username : "unknown", "LOGIN_FAILURE",
                reason, clientIp, "FAILURE");
    }

    @Async
    public void logLogout(String username, String clientIp) {
        save(username, "LOGOUT", "用户登出", clientIp, "SUCCESS");
    }

    @Async
    public void logPasswordChange(String username, String clientIp) {
        save(username, "PASSWORD_CHANGE", "密码修改成功", clientIp, "SUCCESS");
    }

    @Async
    public void logPasswordChangeFailure(String username, String clientIp, String reason) {
        save(username, "PASSWORD_CHANGE_FAILURE", reason, clientIp, "FAILURE");
    }

    @Async
    public void logAccountLocked(String username, String clientIp, String detail) {
        save(username, "ACCOUNT_LOCKED", detail, clientIp, "LOCKED");
    }

    @Async
    public void logAccountUnlocked(String username) {
        save(username, "ACCOUNT_UNLOCKED", "账号锁定解除", null, "SUCCESS");
    }

    @Async
    public void logSessionKicked(String adminUser, String targetUser) {
        save(targetUser, "SESSION_KICKED",
                "被管理员 " + adminUser + " 强制下线", null, "SUCCESS");
    }

    @Async
    public void logMfaVerify(String username, boolean success, String clientIp) {
        save(username, "MFA_VERIFY",
                success ? "MFA 验证成功" : "MFA 验证失败", clientIp,
                success ? "SUCCESS" : "FAILURE");
    }

    @Async
    public void logRememberMeLogin(String username, String clientIp) {
        save(username, "REMEMBER_ME_LOGIN", "自动登录", clientIp, "SUCCESS");
    }

    private void save(String username, String eventType, String detail,
                      String clientIp, String status) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUsername(username);
            auditLog.setEventType(eventType);
            auditLog.setDetail(detail);
            auditLog.setClientIp(clientIp);
            auditLog.setStatus(status);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("审计日志保存失败", e);
        }
    }
}
