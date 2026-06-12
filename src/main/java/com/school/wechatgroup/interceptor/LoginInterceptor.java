package com.school.wechatgroup.interceptor;

import com.school.wechatgroup.constant.SessionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Object userId = request.getSession().getAttribute(SessionKeys.LOGIN_USER_ID);
        if (userId == null) {
            response.sendRedirect("/login");
            return false;
        }

        // 必须修改密码检查: 只允许 /api/auth/change-password 和 /login
        Boolean mustChange = (Boolean) request.getSession()
                .getAttribute(SessionKeys.MUST_CHANGE_PASSWORD);
        if (Boolean.TRUE.equals(mustChange)) {
            String path = request.getRequestURI();
            if (!path.equals("/api/auth/change-password") && !path.equals("/login")
                    && !path.startsWith("/api/auth/")) {
                response.sendRedirect("/login?mustChange=1");
                return false;
            }
        }

        return true;
    }
}
