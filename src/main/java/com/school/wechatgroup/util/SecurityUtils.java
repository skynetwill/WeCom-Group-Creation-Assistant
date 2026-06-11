package com.school.wechatgroup.util;

import com.school.wechatgroup.constant.SessionKeys;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 安全认证工具类
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static String getCurrentUserId(HttpServletRequest request) {
        Object id = request.getSession().getAttribute(SessionKeys.LOGIN_USER_ID);
        return id != null ? (String) id : null;
    }

    public static boolean isAuthenticated(HttpServletRequest request) {
        return getCurrentUserId(request) != null;
    }
}
