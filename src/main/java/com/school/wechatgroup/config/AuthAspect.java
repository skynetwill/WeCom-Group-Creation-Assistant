package com.school.wechatgroup.config;

import com.school.wechatgroup.annotation.RequireAuth;
import com.school.wechatgroup.annotation.RequireRole;
import com.school.wechatgroup.constant.SessionKeys;
import com.school.wechatgroup.entity.AppUser;
import com.school.wechatgroup.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class AuthAspect {

    private static final Logger log = LoggerFactory.getLogger(AuthAspect.class);

    private final AppUserRepository userRepository;

    public AuthAspect(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Around("@within(requireAuth) || @annotation(requireAuth)")
    public Object checkAuth(ProceedingJoinPoint joinPoint, RequireAuth requireAuth) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return send403();
        }

        HttpServletRequest request = attrs.getRequest();
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKeys.LOGIN_USER_ID) == null) {
            return send403(request, attrs.getResponse());
        }

        return joinPoint.proceed();
    }

    @Around("@within(requireRole) || @annotation(requireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return send403();
        }

        HttpServletRequest request = attrs.getRequest();
        HttpSession session = request.getSession(false);
        if (session == null) {
            return send403(request, attrs.getResponse());
        }

        String userId = (String) session.getAttribute(SessionKeys.LOGIN_USER_ID);
        if (userId == null) {
            return send403(request, attrs.getResponse());
        }

        AppUser user = userRepository.findByUsername(userId).orElse(null);
        if (user == null) {
            return send403(request, attrs.getResponse());
        }

        if ("ADMIN".equals(requireRole.value()) && !"ADMIN".equals(user.getRole())) {
            log.warn("用户 {} 尝试访问需要 {} 角色的方法，当前角色: {}",
                    userId, requireRole.value(), user.getRole());
            return send403(request, attrs.getResponse());
        }

        return joinPoint.proceed();
    }

    private Object send403() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "权限不足");
        return result;
    }

    private Object send403(HttpServletRequest request, HttpServletResponse response) {
        if (isApiRequest(request)) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "权限不足");
            return result;
        }
        try {
            if (response != null) {
                response.sendRedirect("/login");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/");
    }
}
