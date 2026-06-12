package com.school.wechatgroup.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * HSTS (HTTP Strict Transport Security) 过滤器
 * 强制浏览器只通过 HTTPS 访问，防止 SSL 剥离攻击
 *
 * max-age=31536000: 一年内浏览器自动将 HTTP 升级为 HTTPS
 * includeSubDomains: 也适用于所有子域名
 */
@Component
@Order(3)
public class HstsFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 仅在 HTTPS 请求时设置 HSTS
        if (httpRequest.isSecure()) {
            httpResponse.setHeader("Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains; preload");
        }

        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) { }

    @Override
    public void destroy() { }
}
