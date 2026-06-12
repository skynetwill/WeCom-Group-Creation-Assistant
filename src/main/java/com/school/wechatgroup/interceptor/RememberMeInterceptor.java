package com.school.wechatgroup.interceptor;

import com.school.wechatgroup.constant.SessionKeys;
import com.school.wechatgroup.repository.AppUserRepository;
import com.school.wechatgroup.security.SecurityContext;
import com.school.wechatgroup.security.SecurityContextHolder;
import com.school.wechatgroup.security.impl.AppUserDetails;
import com.school.wechatgroup.security.impl.RememberMeServicesImpl;
import com.school.wechatgroup.security.impl.RememberMeServicesImpl.RememberMeAuthResult;
import com.school.wechatgroup.security.impl.SecurityContextImpl;
import com.school.wechatgroup.security.impl.UsernamePasswordAuthenticationToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class RememberMeInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RememberMeInterceptor.class);

    private final RememberMeServicesImpl rememberMeServices;
    private final AppUserRepository userRepository;

    public RememberMeInterceptor(RememberMeServicesImpl rememberMeServices,
                                  AppUserRepository userRepository) {
        this.rememberMeServices = rememberMeServices;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 已有登录 session 则跳过
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null && existingSession.getAttribute(SessionKeys.LOGIN_USER_ID) != null) {
            return true;
        }

        RememberMeAuthResult result = rememberMeServices.autoLogin(request, response);
        if (result == null) {
            return true;
        }

        // 查询用户信息（autoLogin 已验证 token 有效性和用户状态，此处获取 nickname/role）
        var user = userRepository.findByUsername(result.getUsername()).orElse(null);
        if (user == null) {
            rememberMeServices.logout(request, response, result.getUsername());
            return true;
        }

        // 创建 session
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionKeys.LOGIN_USER_ID, user.getUsername());
        session.setAttribute(SessionKeys.LOGIN_NICKNAME, user.getNickname());
        session.setAttribute(SessionKeys.LOGIN_ROLE, user.getRole());

        // 设置 SecurityContext
        AppUserDetails details = new AppUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, user.getPassword(),
                        details.getAuthorities());
        SecurityContext ctx = new SecurityContextImpl();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        log.info("Remember-Me 自动登录成功: {}", user.getUsername());
        return true;
    }
}
