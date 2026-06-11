package com.school.wechatgroup.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 安全响应头过滤器 — 为所有 HTTP 响应添加安全头
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 防 XSS：禁止浏览器 MIME 类型嗅探
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        // 防点击劫持：禁止页面被嵌入 frame
        httpResponse.setHeader("X-Frame-Options", "DENY");
        // 启用浏览器 XSS 过滤器
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
        // 限制 Referer 传递
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
