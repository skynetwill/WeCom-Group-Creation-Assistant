package com.school.wechatgroup.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 默认认证失败处理器：API 返回 JSON 错误，页面重定向到登录页
 */
public class SimpleUrlAuthFailureHandler implements AuthenticationFailureHandler {

    private final String failureUrl;

    public SimpleUrlAuthFailureHandler(String failureUrl) {
        this.failureUrl = failureUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
            HttpServletResponse response, RuntimeException exception) throws IOException {
        if (isApiRequest(request)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\""
                    + escapeJson(exception.getMessage()) + "\"}");
            return;
        }
        response.sendRedirect(failureUrl + "?error=true");
    }

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
