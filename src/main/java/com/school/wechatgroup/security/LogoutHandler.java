package com.school.wechatgroup.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface LogoutHandler {
    void logout(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication);
}
