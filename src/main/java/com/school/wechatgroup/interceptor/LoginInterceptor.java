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
        var session = request.getSession(false);
        if (session == null) {
            response.sendRedirect("/login");
            return false;
        }

        Object userId = session.getAttribute(SessionKeys.LOGIN_USER_ID);
        if (userId == null) {
            response.sendRedirect("/login");
            return false;
        }

        // 必须修改密码检查: 只允许 /api/auth/change-password 和 /login
        Boolean mustChange = (Boolean) session.getAttribute(SessionKeys.MUST_CHANGE_PASSWORD);
        if (Boolean.TRUE.equals(mustChange)) {
            String path = request.getRequestURI();
            // 仅放行改密、登出、状态查询，拒绝访问其他业务接口
            if (!path.equals("/api/auth/change-password")
                    && !path.equals("/api/auth/logout")
                    && !path.equals("/api/auth/status")
                    && !path.equals("/login")) {
                response.sendRedirect("/login?mustChange=1");
                return false;
            }
        }

        return true;
    }
}
