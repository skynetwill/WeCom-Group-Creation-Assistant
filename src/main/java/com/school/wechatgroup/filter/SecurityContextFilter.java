package com.school.wechatgroup.filter;

import com.school.wechatgroup.constant.SessionKeys;
import com.school.wechatgroup.repository.AppUserRepository;
import com.school.wechatgroup.security.SecurityContext;
import com.school.wechatgroup.security.SecurityContextHolder;
import com.school.wechatgroup.security.impl.AppUserDetails;
import com.school.wechatgroup.security.impl.RememberMeAuthenticationToken;
import com.school.wechatgroup.security.impl.SecurityContextImpl;
import com.school.wechatgroup.security.impl.UsernamePasswordAuthenticationToken;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * SecurityContext 过滤器（对标 Spring Security SecurityContextPersistenceFilter）
 * 每个请求开始时从 Session 加载用户信息到 SecurityContextHolder（ThreadLocal）
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class SecurityContextFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SecurityContextFilter.class);

    private final AppUserRepository userRepository;

    public SecurityContextFilter(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        try {
            HttpSession session = httpRequest.getSession(false);
            if (session != null) {
                Object userId = session.getAttribute(SessionKeys.LOGIN_USER_ID);
                if (userId != null && userId instanceof String) {
                    userRepository.findByUsername((String) userId).ifPresent(user -> {
                        AppUserDetails details = new AppUserDetails(user);
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        details, user.getPassword(), details.getAuthorities());
                        SecurityContext ctx = new SecurityContextImpl();
                        ctx.setAuthentication(auth);
                        SecurityContextHolder.setContext(ctx);
                    });
                }
            }

            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
