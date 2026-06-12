package com.school.wechatgroup.security.matcher;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;

/**
 * Ant 风格路径匹配器（对标 Spring Security AntPathRequestMatcher）
 */
public class AntPathRequestMatcher implements RequestMatcher {

    private final String pattern;
    private final String httpMethod;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public AntPathRequestMatcher(String pattern) {
        this(pattern, null);
    }

    public AntPathRequestMatcher(String pattern, String httpMethod) {
        this.pattern = pattern;
        this.httpMethod = httpMethod;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        if (httpMethod != null && !httpMethod.equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String url = request.getRequestURI();
        return matcher.match(pattern, url);
    }
}
