package com.school.wechatgroup.controller;

import com.school.wechatgroup.constant.SessionKeys;
import com.school.wechatgroup.service.AuthService;
import com.school.wechatgroup.vo.LoginResultVO;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    private final ConcurrentHashMap<String, Integer> loginAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> attemptWindowStart = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 60_000;

    public AuthController(@Autowired(required = false) AuthService authService) {
        this.authService = authService;
    }

    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        Long windowStart = attemptWindowStart.get(ip);
        if (windowStart == null || now - windowStart > WINDOW_MS) {
            attemptWindowStart.put(ip, now);
            loginAttempts.put(ip, 1);
            return false;
        }
        int attempts = loginAttempts.merge(ip, 1, Integer::sum);
        return attempts > MAX_ATTEMPTS;
    }

    private void incrementAttempts(String ip) {
        loginAttempts.merge(ip, 1, Integer::sum);
    }

    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
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
                                     @RequestParam(required = false) String csrfToken,
                                     HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        if (authService == null) {
            result.put("success", false);
            result.put("message", "认证功能未启用");
            return result;
        }

        String clientIp = extractClientIp(request);
        if (isRateLimited(clientIp)) {
            result.put("success", false);
            result.put("message", "登录尝试次数过多，请稍后重试");
            return result;
        }

        try {
            LoginResultVO loginResult = authService.login(username, password);

            // Session fixation protection: invalidate old session, create new one
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession newSession = request.getSession(true);
            newSession.setAttribute(SessionKeys.LOGIN_USER_ID, loginResult.getUserId());
            newSession.setAttribute(SessionKeys.LOGIN_NICKNAME, loginResult.getNickname());

            result.put("success", true);
            result.put("userId", loginResult.getUserId());
            result.put("nickname", loginResult.getNickname());

            loginAttempts.remove(clientIp);
            attemptWindowStart.remove(clientIp);
        } catch (IllegalArgumentException e) {
            incrementAttempts(clientIp);
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
    public Map<String, Object> logout(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        if (authService == null) {
            result.put("success", false);
            result.put("message", "认证功能未启用");
            return result;
        }

        try {
            authService.logout(request.getSession());
            result.put("success", true);
        } catch (Exception e) {
            log.error("登出异常", e);
            result.put("success", false);
            result.put("message", "登出失败");
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

        return result;
    }
}
