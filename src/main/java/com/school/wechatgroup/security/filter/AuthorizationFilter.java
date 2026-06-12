package com.school.wechatgroup.security.filter;

import com.school.wechatgroup.exception.AccessDeniedException;
import com.school.wechatgroup.security.Authentication;
import com.school.wechatgroup.security.GrantedAuthority;
import com.school.wechatgroup.security.SecurityContextHolder;
import com.school.wechatgroup.security.matcher.RequestMatcher;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 授权过滤器（对标 Spring Security AuthorizationFilter / FilterSecurityInterceptor）
 * 基于 URL 匹配规则进行权限校验
 *
 * 规则值：
 *   "permitAll"   — 任何人可访问
 *   "authenticated" — 需登录即可
 *   "ROLE_xxx"    — 需要特定角色
 *   其他           — 作为 authority 精确匹配
 */
public class AuthorizationFilter implements Filter {

    private final LinkedHashMap<RequestMatcher, String> authRules;
    private final String anyRequestRule;

    public AuthorizationFilter(LinkedHashMap<RequestMatcher, String> authRules, String anyRequestRule) {
        this.authRules = authRules;
        this.anyRequestRule = anyRequestRule;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;

        // 遍历规则
        String requiredAuthority = null;
        boolean matched = false;
        for (Map.Entry<RequestMatcher, String> entry : authRules.entrySet()) {
            if (entry.getKey().matches(req)) {
                requiredAuthority = entry.getValue();
                matched = true;
                break;
            }
        }

        if (!matched) {
            requiredAuthority = anyRequestRule;
        }

        if (requiredAuthority == null || "permitAll".equals(requiredAuthority)) {
            chain.doFilter(request, response);
            return;
        }

        // 需要认证
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("请先登录");
        }

        if ("authenticated".equals(requiredAuthority)) {
            chain.doFilter(request, response);
            return;
        }

        // 检查角色/权限
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (requiredAuthority.equals(ga.getAuthority())
                    || ("ROLE_".equals(requiredAuthority.substring(0, Math.min(5, requiredAuthority.length())))
                    && requiredAuthority.equals(ga.getAuthority()))) {
                chain.doFilter(request, response);
                return;
            }
        }

        throw new AccessDeniedException("权限不足，需要: " + requiredAuthority);
    }
}
