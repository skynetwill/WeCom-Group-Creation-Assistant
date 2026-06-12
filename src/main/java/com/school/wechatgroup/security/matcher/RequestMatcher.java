package com.school.wechatgroup.security.matcher;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求匹配器接口（对标 Spring Security RequestMatcher）
 */
public interface RequestMatcher {
    boolean matches(HttpServletRequest request);
}
