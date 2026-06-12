package com.school.wechatgroup.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface AccessDeniedHandler {
    void handle(HttpServletRequest request, HttpServletResponse response,
            RuntimeException accessDeniedException) throws IOException;
}
