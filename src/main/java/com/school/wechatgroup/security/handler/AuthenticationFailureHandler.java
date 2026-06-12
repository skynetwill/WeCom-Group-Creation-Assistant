package com.school.wechatgroup.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 认证失败处理器（对标 Spring Security AuthenticationFailureHandler）
 */
public interface AuthenticationFailureHandler {
    void onAuthenticationFailure(HttpServletRequest request,
            HttpServletResponse response, RuntimeException exception) throws IOException;
}
