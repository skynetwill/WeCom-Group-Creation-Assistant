package com.school.wechatgroup.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 安全响应头过滤器（对标 Spring Security headers 配置）
 * 添加 X-Frame-Options, X-Content-Type-Options, CSP, X-XSS-Protection 等安全头
 */
@Component
@Order(2)
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 防点击劫持
        httpResponse.setHeader("X-Frame-Options", "DENY");

        // 防 MIME 类型混淆
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");

        // XSS 防护（启用浏览器内置 XSS 过滤器）
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

        // Referrer Policy
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // 跨域隔离策略
        httpResponse.setHeader("Cross-Origin-Opener-Policy", "same-origin");

        // Permissions Policy: 限制浏览器 API
        httpResponse.setHeader("Permissions-Policy",
                "camera=(), microphone=(), geolocation=()");

        // Content-Security-Policy: 允许同源脚本和 Bootstrap/Google Fonts CDN
        httpResponse.setHeader("Content-Security-Policy",
                "default-src 'self'; "
                        + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                        + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; "
                        + "font-src 'self' https://cdn.jsdelivr.net https://fonts.gstatic.com; "
                        + "img-src 'self' data:; "
                        + "connect-src 'self'; "
                        + "frame-ancestors 'none'");

        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
