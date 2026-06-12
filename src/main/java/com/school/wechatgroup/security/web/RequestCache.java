package com.school.wechatgroup.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 请求缓存（对标 Spring Security RequestCache）
 * 保存未认证时的原始请求，登录后恢复
 */
public class RequestCache {

    private static final String SAVED_REQUEST_KEY = "SPRING_SECURITY_SAVED_REQUEST";

    public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SAVED_REQUEST_KEY) == null) {
            session.setAttribute(SAVED_REQUEST_KEY, new SavedRequest(request));
        }
    }

    public SavedRequest getRequest(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            SavedRequest saved = (SavedRequest) session.getAttribute(SAVED_REQUEST_KEY);
            session.removeAttribute(SAVED_REQUEST_KEY);
            return saved;
        }
        return null;
    }
}
