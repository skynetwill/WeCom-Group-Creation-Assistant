package com.school.wechatgroup.security.impl;

import com.school.wechatgroup.security.AuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class LoginUrlAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final String loginUrl;

    public LoginUrlAuthenticationEntryPoint(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         RuntimeException authException) {
        try {
            if (isApiRequest(request)) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"请先登录\"}");
            } else {
                response.sendRedirect(loginUrl);
            }
        } catch (IOException e) {
            throw new RuntimeException("重定向到登录页失败", e);
        }
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/");
    }
}
