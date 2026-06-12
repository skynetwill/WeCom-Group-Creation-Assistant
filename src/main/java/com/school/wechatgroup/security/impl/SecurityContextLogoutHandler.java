package com.school.wechatgroup.security.impl;

import com.school.wechatgroup.security.Authentication;
import com.school.wechatgroup.security.LogoutHandler;
import com.school.wechatgroup.security.SecurityContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 清除 SecurityContext 和 Session 的 LogoutHandler
 */
public class SecurityContextLogoutHandler implements LogoutHandler {

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response,
                       Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
