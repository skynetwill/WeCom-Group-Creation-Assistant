package com.school.wechatgroup.security.filter;

import com.school.wechatgroup.exception.AccessDeniedException;
import com.school.wechatgroup.security.AccessDeniedHandler;
import com.school.wechatgroup.security.AuthenticationEntryPoint;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 异常转换过滤器（对标 Spring Security ExceptionTranslationFilter）
 * 将 AuthenticationException → 401（调用 AuthenticationEntryPoint）
 * 将 AccessDeniedException → 403（调用 AccessDeniedHandler）
 * 放在过滤器链最外层，捕获内层 filter 抛出的异常
 */
public class ExceptionTranslationFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ExceptionTranslationFilter.class);

    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;

    public ExceptionTranslationFilter(AuthenticationEntryPoint authenticationEntryPoint,
                                       AccessDeniedHandler accessDeniedHandler) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (AccessDeniedException e) {
            log.debug("权限拒绝: {}", e.getMessage());
            if (accessDeniedHandler != null) {
                accessDeniedHandler.handle((HttpServletRequest) request,
                        (HttpServletResponse) response, e);
            } else {
                sendJson((HttpServletResponse) response, 403, "权限不足");
            }
        } catch (RuntimeException e) {
            // 认证类异常（LockedException, DisabledException, CredentialsExpiredException 等）
            if (isAuthException(e)) {
                log.debug("认证异常: {}", e.getMessage());
                if (authenticationEntryPoint != null) {
                    authenticationEntryPoint.commence((HttpServletRequest) request,
                            (HttpServletResponse) response, e);
                } else {
                    sendJson((HttpServletResponse) response, 401, e.getMessage());
                }
            } else {
                throw e;
            }
        }
    }

    private boolean isAuthException(RuntimeException e) {
        String className = e.getClass().getName();
        return className.contains("LockedException")
                || className.contains("DisabledException")
                || className.contains("CredentialsExpiredException")
                || className.contains("AccountExpiredException")
                || e.getMessage() != null
                    && (e.getMessage().contains("用户名或密码错误")
                    || e.getMessage().contains("锁定")
                    || e.getMessage().contains("禁用"));
    }

    private void sendJson(HttpServletResponse response, int status, String msg) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\""
                + msg.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
    }
}
