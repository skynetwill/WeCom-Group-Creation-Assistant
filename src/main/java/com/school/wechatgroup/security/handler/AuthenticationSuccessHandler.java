package com.school.wechatgroup.security.handler;

import com.school.wechatgroup.security.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 认证成功处理器（对标 Spring Security AuthenticationSuccessHandler）
 */
public interface AuthenticationSuccessHandler {
    void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response, Authentication authentication) throws IOException;
}
