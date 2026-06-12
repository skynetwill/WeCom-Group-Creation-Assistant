package com.school.wechatgroup.security.jwt;

import com.school.wechatgroup.entity.AppUser;
import com.school.wechatgroup.repository.AppUserRepository;
import com.school.wechatgroup.security.SecurityContext;
import com.school.wechatgroup.security.SecurityContextHolder;
import com.school.wechatgroup.security.impl.AppUserDetails;
import com.school.wechatgroup.security.impl.SecurityContextImpl;
import com.school.wechatgroup.security.impl.UsernamePasswordAuthenticationToken;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Bearer Token 认证过滤器（对标 Spring Security BearerTokenAuthenticationFilter）
 *
 * 从 Authorization 头中提取 JWT token，验证并设置 SecurityContext
 * 用于无状态 API 认证（不依赖 HttpSession）
 *
 * 用法: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
 */
public class BearerTokenAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenAuthFilter.class);

    private final JwtTokenService jwtTokenService;
    private final AppUserRepository userRepository;

    public BearerTokenAuthFilter(JwtTokenService jwtTokenService, AppUserRepository userRepository) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        Map<String, Object> claims = jwtTokenService.validateAndParse(token);

        if (claims == null) {
            chain.doFilter(request, response);
            return;
        }

        String username = (String) claims.get("sub");
        if (username == null) {
            chain.doFilter(request, response);
            return;
        }

        AppUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            chain.doFilter(request, response);
            return;
        }

        AppUserDetails details = new AppUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());

        SecurityContext ctx = new SecurityContextImpl();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        log.debug("JWT Bearer Token 认证成功: {}", username);

        chain.doFilter(request, response);
    }
}
