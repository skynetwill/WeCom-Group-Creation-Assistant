package com.school.wechatgroup.security.config;

import jakarta.servlet.*;
import java.io.IOException;
import java.util.List;

/**
 * 安全过滤器链（对标 Spring Security SecurityFilterChain）
 * 将声明式配置的 Filter 列表实际注册到 Servlet 容器执行
 */
public class SecurityFilterChain implements Filter {

    private final List<Filter> filters;

    public SecurityFilterChain(List<Filter> filters) {
        this.filters = filters;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        new VirtualFilterChain(chain, filters).doFilter(request, response);
    }

    public List<Filter> getFilters() {
        return filters;
    }

    /**
     * 虚拟过滤器链：按顺序执行内部 Filter 列表后继续原始链
     */
    private static class VirtualFilterChain implements FilterChain {
        private final FilterChain originalChain;
        private final List<Filter> additionalFilters;
        private int currentPosition;

        VirtualFilterChain(FilterChain originalChain, List<Filter> additionalFilters) {
            this.originalChain = originalChain;
            this.additionalFilters = additionalFilters;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            if (currentPosition < additionalFilters.size()) {
                Filter nextFilter = additionalFilters.get(currentPosition++);
                nextFilter.doFilter(request, response, this);
            } else {
                originalChain.doFilter(request, response);
            }
        }
    }
}
