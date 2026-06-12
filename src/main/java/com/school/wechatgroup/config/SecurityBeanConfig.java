package com.school.wechatgroup.config;

import com.school.wechatgroup.repository.AppUserRepository;
import com.school.wechatgroup.repository.RememberMeTokenRepository;
import com.school.wechatgroup.security.*;
import com.school.wechatgroup.security.config.HttpSecurity;
import com.school.wechatgroup.security.config.SecurityFilterChain;
import com.school.wechatgroup.security.impl.*;
import com.school.wechatgroup.security.jwt.BearerTokenAuthFilter;
import com.school.wechatgroup.security.jwt.JwtTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

/**
 * Security 框架 Bean 注册（对标 Spring Security 的 AuthenticationConfiguration）
 * 仅在 app.auth.enabled=true 时生效
 */
@Configuration
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class SecurityBeanConfig {

    /**
     * JWT Token 服务 — 无状态认证
     */
    @Bean
    public JwtTokenService jwtTokenService() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isEmpty()) {
            // 无环境变量时生成随机密钥（重启后 token 失效）
            byte[] randomKey = new byte[32];
            new java.security.SecureRandom().nextBytes(randomKey);
            secret = java.util.Base64.getEncoder().encodeToString(randomKey);
            org.slf4j.LoggerFactory.getLogger(SecurityBeanConfig.class)
                    .warn("JWT_SECRET 环境变量未设置，已生成随机密钥。多实例部署或重启后 token 将失效！");
        }
        return new JwtTokenService(secret, 3600_000, 7 * 24 * 3600_000L);
    }

    /**
     * Bearer Token 认证过滤器（JWT 无状态认证）
     */
    @Bean
    public BearerTokenAuthFilter bearerTokenAuthFilter(
            JwtTokenService jwtTokenService, AppUserRepository userRepository) {
        return new BearerTokenAuthFilter(jwtTokenService, userRepository);
    }

    /**
     * 用户名密码认证提供者
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            AppUserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        return new DaoAuthenticationProvider(userRepository, passwordEncoder);
    }

    /**
     * 认证管理器（ProviderManager）
     */
    @Bean
    public AuthenticationManager authenticationManager(
            List<AuthenticationProvider> providers) {
        return new ProviderManager(providers);
    }

    /**
     * SecurityFilterChain — DSL 声明式安全配置（对标 Spring Security HttpSecurity DSL）
     *
     * 演示了声明式安全配置的完整用法：
     *   1. URL 授权规则（antMatchers + permitAll/hasRole/authenticated）
     *   2. 表单登录配置
     *   3. 异常处理
     *   4. 登出配置
     *   5. 自定义过滤器（JWT Bearer Token）
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            AuthenticationManager authManager,
            AuthenticationEntryPoint entryPoint,
            AccessDeniedHandler deniedHandler,
            BearerTokenAuthFilter bearerTokenFilter) {

        HttpSecurity http = new HttpSecurity(authManager);

        http
            // 1. URL 授权规则
            .authorizeRequests(auth -> auth
                .antMatchers("/api/auth/**", "/login", "/login.html", "/api/public/**").permitAll()
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .antMatchers("/api/group/**", "/api/template/**", "/api/guide/**").authenticated()
                .anyRequest().authenticated()
            )
            // 2. 表单登录
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/api/auth/login")
                .defaultSuccessUrl("/index")
            )
            // 3. 异常处理
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(deniedHandler)
            )
            // 4. 登出
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
            )
            // 5. 自定义过滤器：JWT Bearer Token 无状态认证
            .addFilter(bearerTokenFilter);

        return http.build();
    }

    /**
     * Remember-Me 服务实现（含浏览器指纹）
     */
    @Bean
    public RememberMeServicesImpl rememberMeServices(
            RememberMeTokenRepository tokenRepository,
            AppUserRepository userRepository) {
        return new RememberMeServicesImpl(tokenRepository, userRepository);
    }

    /**
     * 认证入口点：未认证请求
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return new LoginUrlAuthenticationEntryPoint("/login");
    }

    /**
     * 权限拒绝处理器
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return new DefaultAccessDeniedHandler();
    }

}
