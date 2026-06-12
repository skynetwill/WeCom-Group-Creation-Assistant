package com.school.wechatgroup.security.impl;

import com.school.wechatgroup.entity.AppUser;
import com.school.wechatgroup.exception.CredentialsExpiredException;
import com.school.wechatgroup.exception.DisabledException;
import com.school.wechatgroup.exception.LockedException;
import com.school.wechatgroup.repository.AppUserRepository;
import com.school.wechatgroup.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DaoAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(DaoAuthenticationProvider.class);

    private final AppUserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public DaoAuthenticationProvider(AppUserRepository userRepository,
                                      BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = authentication.getName();
        String password = (String) authentication.getCredentials();

        AppUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            log.debug("用户不存在: {}", username);
            return null;
        }

        // 预检查：账号状态
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new DisabledException("账号已被禁用");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutes = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1;
            throw new LockedException("账号已被锁定，请 " + minutes + " 分钟后重试");
        }
        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            throw new CredentialsExpiredException("密码已过期，请修改密码");
        }

        // 密码验证
        if (!passwordEncoder.matches(password, user.getPassword())) {
            // 失败追踪
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= 5) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                log.warn("用户 {} 连续失败 {} 次，已锁定", username, attempts);
            }
            userRepository.save(user);
            return null;
        }

        // 成功后重置
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        AppUserDetails userDetails = new AppUserDetails(user);
        return new UsernamePasswordAuthenticationToken(userDetails, password,
                userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
