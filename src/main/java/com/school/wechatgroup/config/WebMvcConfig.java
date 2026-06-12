package com.school.wechatgroup.config;

import com.school.wechatgroup.interceptor.LoginInterceptor;
import com.school.wechatgroup.interceptor.RememberMeInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class WebMvcConfig implements WebMvcConfigurer {

    private final RememberMeInterceptor rememberMeInterceptor;
    private final LoginInterceptor loginInterceptor;

    public WebMvcConfig(RememberMeInterceptor rememberMeInterceptor, LoginInterceptor loginInterceptor) {
        this.rememberMeInterceptor = rememberMeInterceptor;
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rememberMeInterceptor)
                .order(1)
                .addPathPatterns("/**");

        registry.addInterceptor(loginInterceptor)
                .order(2)
                .addPathPatterns("/", "/index", "/index.html", "/api/group/**", "/api/template/**", "/api/guide/**")
                .excludePathPatterns("/api/auth/**", "/login", "/login.html");
    }
}
