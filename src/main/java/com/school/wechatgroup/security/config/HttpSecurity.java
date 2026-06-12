package com.school.wechatgroup.security.config;

import com.school.wechatgroup.security.*;
import com.school.wechatgroup.security.filter.AuthorizationFilter;
import com.school.wechatgroup.security.filter.ExceptionTranslationFilter;
import com.school.wechatgroup.security.filter.UsernamePasswordAuthFilter;
import com.school.wechatgroup.security.handler.AuthenticationFailureHandler;
import com.school.wechatgroup.security.handler.AuthenticationSuccessHandler;
import com.school.wechatgroup.security.handler.SavedRequestAwareAuthSuccessHandler;
import com.school.wechatgroup.security.handler.SimpleUrlAuthFailureHandler;
import com.school.wechatgroup.security.matcher.RequestMatcher;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HttpSecurity DSL 构建器（对标 Spring Security HttpSecurity）
 * 提供声明式的安全配置 API，流畅构建 SecurityFilterChain
 *
 * <pre>{@code
 * HttpSecurity http = new HttpSecurity(authenticationManager);
 * http
 *     .authorizeRequests(auth -> auth
 *         .antMatchers("/api/auth/**", "/login").permitAll()
 *         .antMatchers("/api/admin/**").hasRole("ADMIN")
 *         .anyRequest().authenticated()
 *     )
 *     .formLogin(form -> form
 *         .loginPage("/login")
 *         .loginProcessingUrl("/api/auth/login")
 *     )
 *     .exceptionHandling(ex -> ex
 *         .authenticationEntryPoint(entryPoint)
 *         .accessDeniedHandler(deniedHandler)
 *     );
 * SecurityFilterChain chain = http.build();
 * }</pre>
 */
public class HttpSecurity {

    private final AuthenticationManager authenticationManager;
    private final List<Filter> additionalFilters = new ArrayList<>();

    // 授权配置
    private final LinkedHashMap<RequestMatcher, String> authRules = new LinkedHashMap<>();
    private String anyRequestRule = "authenticated";

    // 表单登录配置
    private String loginPage = "/login";
    private String loginProcessingUrl = "/api/auth/login";
    private String defaultSuccessUrl = "/index";
    private AuthenticationSuccessHandler successHandler;
    private AuthenticationFailureHandler failureHandler;

    // Remember-Me
    private RememberMeConfig rememberMeConfig;

    // 异常处理
    private AuthenticationEntryPoint authenticationEntryPoint;
    private AccessDeniedHandler accessDeniedHandler;

    // Logout
    private String logoutUrl = "/api/auth/logout";
    private List<LogoutHandler> logoutHandlers = new ArrayList<>();

    public HttpSecurity(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    // ==================== 授权配置 ====================

    public AuthorizeRequests authorizeRequests() {
        return new AuthorizeRequests();
    }

    public HttpSecurity authorizeRequests(java.util.function.Consumer<AuthorizeRequests> configurer) {
        configurer.accept(new AuthorizeRequests());
        return this;
    }

    public class AuthorizeRequests {
        public AuthorizeRequests antMatchers(String... patterns) {
            for (String p : patterns) {
                authRules.put(new com.school.wechatgroup.security.matcher.AntPathRequestMatcher(p), null);
            }
            return this;
        }

        public AuthorizeRequests permitAll() {
            for (var entry : authRules.entrySet()) {
                if (entry.getValue() == null) entry.setValue("permitAll");
            }
            return this;
        }

        public AuthorizeRequests authenticated() {
            for (var entry : authRules.entrySet()) {
                if (entry.getValue() == null) entry.setValue("authenticated");
            }
            return this;
        }

        public AuthorizeRequests hasRole(String role) {
            for (var entry : authRules.entrySet()) {
                if (entry.getValue() == null) entry.setValue("ROLE_" + role);
            }
            return this;
        }

        public AuthorizeRequests hasAuthority(String authority) {
            for (var entry : authRules.entrySet()) {
                if (entry.getValue() == null) entry.setValue(authority);
            }
            return this;
        }

        public AuthorizeRequests anyRequest() {
            return this;
        }
    }

    // ==================== 表单登录 ====================

    public HttpSecurity formLogin(java.util.function.Consumer<FormLoginConfigurer> configurer) {
        configurer.accept(new FormLoginConfigurer());
        return this;
    }

    public class FormLoginConfigurer {
        public FormLoginConfigurer loginPage(String page) { loginPage = page; return this; }
        public FormLoginConfigurer loginProcessingUrl(String url) { loginProcessingUrl = url; return this; }
        public FormLoginConfigurer defaultSuccessUrl(String url) { defaultSuccessUrl = url; return this; }
        public FormLoginConfigurer successHandler(AuthenticationSuccessHandler handler) { successHandler = handler; return this; }
        public FormLoginConfigurer failureHandler(AuthenticationFailureHandler handler) { failureHandler = handler; return this; }
    }

    // ==================== Remember-Me ====================

    public static class RememberMeConfig {
        String key;
        int tokenValiditySeconds = 1209600;
    }

    // ==================== 异常处理 ====================

    public HttpSecurity exceptionHandling(java.util.function.Consumer<ExceptionHandlingConfigurer> configurer) {
        configurer.accept(new ExceptionHandlingConfigurer());
        return this;
    }

    public class ExceptionHandlingConfigurer {
        public ExceptionHandlingConfigurer authenticationEntryPoint(AuthenticationEntryPoint ep) {
            authenticationEntryPoint = ep;
            return this;
        }
        public ExceptionHandlingConfigurer accessDeniedHandler(AccessDeniedHandler h) {
            accessDeniedHandler = h;
            return this;
        }
    }

    // ==================== Logout ====================

    public HttpSecurity logout(java.util.function.Consumer<LogoutConfigurer> configurer) {
        configurer.accept(new LogoutConfigurer());
        return this;
    }

    public class LogoutConfigurer {
        public LogoutConfigurer logoutUrl(String url) { HttpSecurity.this.logoutUrl = url; return this; }
        public LogoutConfigurer addLogoutHandler(LogoutHandler handler) { logoutHandlers.add(handler); return this; }
    }

    public HttpSecurity addFilter(Filter filter) {
        additionalFilters.add(filter);
        return this;
    }

    // ==================== 构建 ====================

    public SecurityFilterChain build() {
        List<Filter> filters = new ArrayList<>();

        // 1. ExceptionTranslationFilter — 最外层，捕获认证/授权异常
        AuthenticationEntryPoint ep = authenticationEntryPoint;
        AccessDeniedHandler adh = accessDeniedHandler;
        filters.add(new ExceptionTranslationFilter(ep, adh));

        // 2. AuthorizationFilter — URL 级权限检查
        filters.add(new AuthorizationFilter(authRules, anyRequestRule));

        // 3. UsernamePasswordAuthFilter — 登录处理
        AuthenticationSuccessHandler sh = successHandler != null
                ? successHandler : new SavedRequestAwareAuthSuccessHandler(defaultSuccessUrl);
        AuthenticationFailureHandler fh = failureHandler != null
                ? failureHandler : new SimpleUrlAuthFailureHandler(loginPage);
        filters.add(new UsernamePasswordAuthFilter(loginProcessingUrl, authenticationManager, sh, fh));

        // 4. 额外的自定义 filter
        filters.addAll(additionalFilters);

        return new SecurityFilterChain(filters);
    }
}
