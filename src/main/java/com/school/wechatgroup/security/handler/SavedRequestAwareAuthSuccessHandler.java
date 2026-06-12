package com.school.wechatgroup.security.handler;

import com.school.wechatgroup.security.Authentication;
import com.school.wechatgroup.security.web.SavedRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 默认认证成功处理器：API 返回 JSON，页面保存请求并重定向
 */
public class SavedRequestAwareAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final String defaultTargetUrl;

    public SavedRequestAwareAuthSuccessHandler(String defaultTargetUrl) {
        this.defaultTargetUrl = defaultTargetUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response, Authentication authentication) throws IOException {
        if (isApiRequest(request)) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":true,\"userId\":\""
                    + authentication.getName() + "\",\"message\":\"登录成功\"}");
            return;
        }

        // 恢复原始请求 URL
        SavedRequest saved = (SavedRequest) request.getSession()
                .getAttribute("SPRING_SECURITY_SAVED_REQUEST");
        String target = (saved != null) ? saved.getRedirectUrl() : defaultTargetUrl;
        response.sendRedirect(target);
    }

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }
}
