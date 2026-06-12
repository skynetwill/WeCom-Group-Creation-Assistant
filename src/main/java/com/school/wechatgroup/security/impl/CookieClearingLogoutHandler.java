package com.school.wechatgroup.security.impl;

import com.school.wechatgroup.security.Authentication;
import com.school.wechatgroup.security.LogoutHandler;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * 清除 Remember-Me Cookie 的 LogoutHandler
 */
public class CookieClearingLogoutHandler implements LogoutHandler {

    private final List<String> cookieNames;

    public CookieClearingLogoutHandler(List<String> cookieNames) {
        this.cookieNames = cookieNames;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response,
                       Authentication authentication) {
        for (String name : cookieNames) {
            Cookie cookie = new Cookie(name, "");
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(0);
            response.addCookie(cookie);
        }
    }
}
