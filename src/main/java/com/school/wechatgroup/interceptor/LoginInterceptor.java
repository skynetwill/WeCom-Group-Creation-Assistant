package com.school.wechatgroup.interceptor;

import com.school.wechatgroup.constant.SessionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Object userId = request.getSession().getAttribute(SessionKeys.LOGIN_USER_ID);
        if (userId != null) {
            return true;
        }
        response.sendRedirect("/login.html");
        return false;
    }
}
