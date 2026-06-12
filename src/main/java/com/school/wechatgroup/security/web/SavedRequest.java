package com.school.wechatgroup.security.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 保存的原始请求（对标 Spring Security SavedRequest）
 * 用于登录后重定向回用户最初想访问的页面
 */
public class SavedRequest {

    private final String redirectUrl;
    private final String method;
    private final Map<String, List<String>> parameters;

    public SavedRequest(HttpServletRequest request) {
        this.redirectUrl = request.getRequestURI()
                + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        this.method = request.getMethod();
        this.parameters = new HashMap<>();
        request.getParameterMap().forEach((k, v) ->
                parameters.put(k, Collections.unmodifiableList(Arrays.asList(v))));
    }

    public String getRedirectUrl() { return redirectUrl; }
    public String getMethod() { return method; }
    public Map<String, List<String>> getParameters() { return parameters; }
}
