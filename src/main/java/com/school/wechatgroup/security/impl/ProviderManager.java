package com.school.wechatgroup.security.impl;

import com.school.wechatgroup.security.Authentication;
import com.school.wechatgroup.security.AuthenticationManager;
import com.school.wechatgroup.security.AuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ProviderManager implements AuthenticationManager {

    private static final Logger log = LoggerFactory.getLogger(ProviderManager.class);

    private final List<AuthenticationProvider> providers;

    public ProviderManager(List<AuthenticationProvider> providers) {
        this.providers = providers;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        Class<?> authClass = authentication.getClass();

        for (AuthenticationProvider provider : providers) {
            if (!provider.supports(authClass)) {
                continue;
            }

            try {
                Authentication result = provider.authenticate(authentication);
                if (result != null) {
                    return result;
                }
            } catch (RuntimeException e) {
                log.debug("认证提供者 {} 认证失败: {}", provider.getClass().getSimpleName(), e.getMessage());
                throw e;
            }
        }

        log.warn("未找到支持 {} 的认证提供者", authClass.getSimpleName());
        return null;
    }
}
