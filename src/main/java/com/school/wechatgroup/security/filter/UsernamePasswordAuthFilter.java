package com.school.wechatgroup.security.filter;

import com.school.wechatgroup.security.Authentication;
import com.school.wechatgroup.security.AuthenticationManager;
import com.school.wechatgroup.security.SecurityContextHolder;
import com.school.wechatgroup.security.handler.AuthenticationFailureHandler;
import com.school.wechatgroup.security.handler.AuthenticationSuccessHandler;
import com.school.wechatgroup.security.impl.UsernamePasswordAuthenticationToken;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 用户名密码认证过滤器（对标 Spring Security AbstractAuthenticationProcessingFilter）
 *
 * 职责：
 * 1. 只拦截配置的 loginProcessingUrl（POST 方法）
 * 2. 从请求中提取用户名/密码构造 Authentication token
 * 3. 委托给 AuthenticationManager 认证
 * 4. 成功 → 调用 AuthenticationSuccessHandler
 * 5. 失败 → 调用 AuthenticationFailureHandler
 * 6. 管理 SecurityContext 持久化
 */
public class UsernamePasswordAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(UsernamePasswordAuthFilter.class);

    private final String loginProcessingUrl;
    private final AuthenticationManager authenticationManager;
    private final AuthenticationSuccessHandler successHandler;
    private final AuthenticationFailureHandler failureHandler;

    public UsernamePasswordAuthFilter(String loginProcessingUrl,
                                       AuthenticationManager authenticationManager,
                                       AuthenticationSuccessHandler successHandler,
                                       AuthenticationFailureHandler failureHandler) {
        this.loginProcessingUrl = loginProcessingUrl;
        this.authenticationManager = authenticationManager;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // 只处理登录 URL 的 POST 请求
        if (!requiresAuthentication(req)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // 已登录则跳过
            if (SecurityContextHolder.getContext().getAuthentication() != null
                    && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
                chain.doFilter(request, response);
                return;
            }

            // 提取认证信息
            String username = req.getParameter("username");
            String password = req.getParameter("password");

            if (username == null || password == null) {
                failureHandler.onAuthenticationFailure(req, res,
                        new RuntimeException("用户名和密码不能为空"));
                return;
            }

            // 构造 token 并认证
            UsernamePasswordAuthenticationToken authRequest =
                    new UsernamePasswordAuthenticationToken(username, password);

            Authentication authResult = authenticationManager.authenticate(authRequest);

            if (authResult == null || !authResult.isAuthenticated()) {
                failureHandler.onAuthenticationFailure(req, res,
                        new RuntimeException("用户名或密码错误"));
                return;
            }

            // 认证成功
            SecurityContextHolder.getContext().setAuthentication(authResult);

            // Session fixation 防护
            req.changeSessionId();

            successHandler.onAuthenticationSuccess(req, res, authResult);

        } catch (RuntimeException e) {
            try {
                failureHandler.onAuthenticationFailure(req, res, e);
            } catch (IOException ex) {
                log.error("认证失败处理异常", ex);
            }
        }
    }

    private boolean requiresAuthentication(HttpServletRequest request) {
        return loginProcessingUrl.equals(request.getRequestURI())
                && "POST".equalsIgnoreCase(request.getMethod());
    }
}
