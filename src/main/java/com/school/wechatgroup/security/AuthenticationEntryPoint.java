package com.school.wechatgroup.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthenticationEntryPoint {
    void commence(HttpServletRequest request, HttpServletResponse response,
            RuntimeException authException);
}
