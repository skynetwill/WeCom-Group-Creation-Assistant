package com.school.wechatgroup.controller;

import com.school.wechatgroup.config.SessionRegistryConfig.SessionRegistry;
import com.school.wechatgroup.constant.SessionKeys;
import com.school.wechatgroup.exception.BusinessException;
import com.school.wechatgroup.exception.CredentialsExpiredException;
import com.school.wechatgroup.exception.DisabledException;
import com.school.wechatgroup.exception.LockedException;
import com.school.wechatgroup.security.SecurityContextHolder;
import com.school.wechatgroup.security.impl.RememberMeServicesImpl;
import com.school.wechatgroup.service.AuthService;
import com.school.wechatgroup.service.RateLimitService;
import com.school.wechatgroup.util.IpUtils;
import com.school.wechatgroup.vo.LoginResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final RememberMeServicesImpl rememberMeServices;
    private final SessionRegistry sessionRegistry;
    private final com.school.wechatgroup.config.AuthProperties authProperties;
    private final RateLimitService rateLimitService;

    public AuthController(@Autowired(required = false) AuthService authService,
                          @Autowired(required = false) RememberMeServicesImpl rememberMeServices,
                          @Autowired(required = false) SessionRegistry sessionRegistry,
                          @Autowired(required = false) com.school.wechatgroup.config.AuthProperties authProperties,
                          @Autowired(required = false) RateLimitService rateLimitService) {
        this.authService = authService;
        this.rememberMeServices = rememberMeServices;
        this.sessionRegistry = sessionRegistry;
        this.authProperties = authProperties;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/csrf")
    public Map<String, Object> csrf(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String token = UUID.randomUUID().toString();
        request.getSession().setAttribute("CSRF_TOKEN", token);
        result.put("token", token);
        return result;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String username,
                                     @RequestParam String password,
                                     @RequestParam(defaultValue = "false") boolean rememberMe,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        Map<String, Object> result = new HashMap<>();

        if (authService == null) {
            result.put("success", false);
            result.put("message", "认证功能未启用");
            return result;
        }

        // CSRF 验证
        String csrfToken = request.getHeader("X-CSRF-TOKEN");
        String sessionToken = (String) request.getSession().getAttribute("CSRF_TOKEN");
        if (sessionToken == null || csrfToken == null || !sessionToken.equals(csrfToken)) {
            result.put("success", false);
            result.put("message", "无效请求");
            return result;
        }

        String clientIp = IpUtils.getClientIp(request);
        if (rateLimitService != null && rateLimitService.isRateLimited(clientIp)) {
            result.put("success", false);
            result.put("message", "登录尝试次数过多，请稍后重试");
            return result;
        }

        try {
            LoginResultVO loginResult = authService.login(username, password, clientIp, rememberMe);

            // MFA 检查：用户启用了 MFA 则需要额外验证
            if (authService != null) {
                boolean mfaRequired = false;
                try {
                    var profile = authService.getProfile(loginResult.getUserId());
                    mfaRequired = Boolean.TRUE.equals(profile.get("mfaEnabled"));
                } catch (Exception ignored) { }
                if (mfaRequired) {
                    // 暂存用户名到 session 用于 MFA 验证（尚未创建完整登录状态）
                    HttpSession oldSession = request.getSession(false);
                    if (oldSession != null) oldSession.invalidate();
                    request.changeSessionId();
                    HttpSession tempSession = request.getSession(true);
                    tempSession.setAttribute("MFA_PENDING_USER", loginResult.getUserId());
                    tempSession.setAttribute("MFA_PENDING_NICKNAME", loginResult.getNickname());
                    tempSession.setAttribute("MFA_PENDING_REMEMBERME", rememberMe);
                    result.put("success", true);
                    result.put("mfaRequired", true);
                    result.put("userId", loginResult.getUserId());
                    if (rateLimitService != null) rateLimitService.clear(clientIp);
                    return result;
                }
            }

            // Session fixation protection: invalidate old session + changeSessionId()
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            request.changeSessionId();
            HttpSession newSession = request.getSession(true);
            newSession.setAttribute(SessionKeys.LOGIN_USER_ID, loginResult.getUserId());
            newSession.setAttribute(SessionKeys.LOGIN_NICKNAME, loginResult.getNickname());
            newSession.setAttribute(SessionKeys.MUST_CHANGE_PASSWORD, loginResult.isMustChangePassword());

            // 获取 role 并设置
            try {
                var profile = authService.getProfile(loginResult.getUserId());
                var role = profile.get("role");
                newSession.setAttribute(SessionKeys.LOGIN_ROLE, role);
            } catch (Exception ex) {
                log.warn("获取用户角色失败: {}", ex.getMessage());
            }

            // 并发会话控制
            if (sessionRegistry != null && authProperties != null) {
                boolean registered = sessionRegistry.registerSession(
                        loginResult.getUserId(), newSession.getId(), authProperties.getMaxSessions());
                if (!registered) {
                    log.info("用户 {} 超过最大会话数限制", loginResult.getUserId());
                }
            }

            // Remember-Me cookie（含浏览器指纹）
            if (rememberMe && rememberMeServices != null) {
                int validityDays = authProperties != null
                        ? authProperties.getRememberMeValidityDays() : 14;
                rememberMeServices.onLoginSuccess(request, response, loginResult.getUserId(), validityDays);
            }

            result.put("success", true);
            result.put("userId", loginResult.getUserId());
            result.put("nickname", loginResult.getNickname());
            result.put("mustChangePassword", loginResult.isMustChangePassword());

            if (rateLimitService != null) rateLimitService.clear(clientIp);
        } catch (LockedException | DisabledException | CredentialsExpiredException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (IllegalArgumentException | BusinessException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("登录异常", e);
            result.put("success", false);
            result.put("message", "登录失败，请稍后重试");
        }

        return result;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> result = new HashMap<>();

        if (authService == null) {
            result.put("success", false);
            result.put("message", "认证功能未启用");
            return result;
        }

        try {
            HttpSession session = request.getSession(false);
            String userId = null;
            if (session != null) {
                userId = (String) session.getAttribute(SessionKeys.LOGIN_USER_ID);
                if (userId != null && sessionRegistry != null) {
                    sessionRegistry.removeSession(userId, session.getId());
                }
                session.invalidate();
            }

            // 清除 Remember-Me
            if (rememberMeServices != null) {
                rememberMeServices.logout(request, response, userId);
            }

            SecurityContextHolder.clearContext();

            result.put("success", true);
        } catch (Exception e) {
            log.error("登出异常", e);
            result.put("success", false);
            result.put("message", "登出失败");
        }

        return result;
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@RequestParam String oldPassword,
                                              @RequestParam String newPassword,
                                              HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        if (authService == null) {
            result.put("success", false);
            result.put("message", "认证功能未启用");
            return result;
        }

        String userId = (String) request.getSession().getAttribute(SessionKeys.LOGIN_USER_ID);
        if (userId == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }

        try {
            authService.changePassword(userId, oldPassword, newPassword);
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.removeAttribute(SessionKeys.MUST_CHANGE_PASSWORD);
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            result.put("success", true);
            result.put("message", "密码修改成功，请重新登录");
        } catch (IllegalArgumentException | BusinessException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("修改密码异常", e);
            result.put("success", false);
            result.put("message", "修改密码失败");
        }

        return result;
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        if (authService == null) {
            result.put("success", false);
            result.put("message", "认证功能未启用");
            return result;
        }

        String userId = (String) request.getSession().getAttribute(SessionKeys.LOGIN_USER_ID);
        if (userId == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }

        try {
            Map<String, Object> profile = authService.getProfile(userId);
            result.put("success", true);
            result.putAll(profile);
        } catch (Exception e) {
            log.error("获取用户信息异常", e);
            result.put("success", false);
            result.put("message", "获取用户信息失败");
        }

        return result;
    }

    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        if (authService == null) {
            result.put("authenticated", false);
            return result;
        }

        var authStatus = authService.getStatus(request.getSession());
        result.put("authenticated", authStatus.isAuthenticated());
        result.put("userId", authStatus.getUserId());
        result.put("nickname", authStatus.getNickname());
        result.put("role", authStatus.getRole());

        return result;
    }

    // ==================== MFA TOTP 相关端点 ====================

    @GetMapping("/mfa/setup")
    public Map<String, Object> mfaSetup(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (authService == null) {
            result.put("success", false);
            result.put("message", "认证功能未启用");
            return result;
        }
        String userId = (String) request.getSession().getAttribute(SessionKeys.LOGIN_USER_ID);
        if (userId == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }
        try {
            String secret = authService.setupMFA(userId);
            result.put("success", true);
            result.put("secret", secret);
            result.put("uri", "otpauth://totp/WeChatGroup:" + userId
                    + "?secret=" + secret + "&issuer=WeChatGroup&algorithm=SHA1&digits=6&period=30");
        } catch (BusinessException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/mfa/enable")
    public Map<String, Object> mfaEnable(@RequestParam String code,
                                          HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (authService == null) {
            result.put("success", false);
            result.put("message", "认证功能未启用");
            return result;
        }
        // CSRF 验证
        String csrfToken = request.getHeader("X-CSRF-TOKEN");
        String sessionToken = (String) request.getSession().getAttribute("CSRF_TOKEN");
        if (sessionToken == null || csrfToken == null || !sessionToken.equals(csrfToken)) {
            result.put("success", false);
            result.put("message", "无效请求");
            return result;
        }

        String userId = (String) request.getSession().getAttribute(SessionKeys.LOGIN_USER_ID);
        if (userId == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }
        try {
            authService.enableMFA(userId, code, IpUtils.getClientIp(request));
            result.put("success", true);
            result.put("message", "MFA 已启用");
        } catch (BusinessException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/mfa/verify")
    public Map<String, Object> mfaVerify(@RequestParam String code,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        Map<String, Object> result = new HashMap<>();
        if (authService == null) {
            result.put("success", false);
            result.put("message", "认证功能未启用");
            return result;
        }

        // CSRF 验证：使用临时 session 中的 token
        HttpSession tempSession = request.getSession(false);
        if (tempSession != null) {
            String csrfToken = request.getHeader("X-CSRF-TOKEN");
            String sessionToken = (String) tempSession.getAttribute("CSRF_TOKEN");
            if (sessionToken != null && csrfToken != null && !sessionToken.equals(csrfToken)) {
                result.put("success", false);
                result.put("message", "无效请求");
                return result;
            }
        }

        tempSession = request.getSession(false);
        if (tempSession == null) {
            result.put("success", false);
            result.put("message", "MFA 会话已过期，请重新登录");
            return result;
        }

        String pendingUser = (String) tempSession.getAttribute("MFA_PENDING_USER");
        String pendingNickname = (String) tempSession.getAttribute("MFA_PENDING_NICKNAME");
        Boolean pendingRememberMe = (Boolean) tempSession.getAttribute("MFA_PENDING_REMEMBERME");

        if (pendingUser == null) {
            result.put("success", false);
            result.put("message", "MFA 会话已过期，请重新登录");
            return result;
        }

        boolean verified = authService.verifyMFA(pendingUser, code, IpUtils.getClientIp(request));
        if (!verified) {
            result.put("success", false);
            result.put("message", "MFA 验证码不正确");
            return result;
        }

        // MFA 验证通过 → 创建完整登录 session
        tempSession.removeAttribute("MFA_PENDING_USER");
        tempSession.removeAttribute("MFA_PENDING_NICKNAME");
        tempSession.removeAttribute("MFA_PENDING_REMEMBERME");

        tempSession.setAttribute(SessionKeys.LOGIN_USER_ID, pendingUser);
        tempSession.setAttribute(SessionKeys.LOGIN_NICKNAME, pendingNickname);
        tempSession.setAttribute(SessionKeys.MUST_CHANGE_PASSWORD, false);

        // 获取 role
        try {
            var profile = authService.getProfile(pendingUser);
            tempSession.setAttribute(SessionKeys.LOGIN_ROLE, profile.get("role"));
        } catch (Exception ignored) { }

        // 并发会话
        if (sessionRegistry != null && authProperties != null) {
            sessionRegistry.registerSession(pendingUser, tempSession.getId(),
                    authProperties.getMaxSessions());
        }

        // Remember-Me
        if (Boolean.TRUE.equals(pendingRememberMe) && rememberMeServices != null) {
            int days = authProperties != null ? authProperties.getRememberMeValidityDays() : 14;
            rememberMeServices.onLoginSuccess(request, response, pendingUser, days);
        }

        result.put("success", true);
        result.put("verified", true);
        result.put("userId", pendingUser);
        result.put("mustChangePassword", false);
        return result;
    }
}
