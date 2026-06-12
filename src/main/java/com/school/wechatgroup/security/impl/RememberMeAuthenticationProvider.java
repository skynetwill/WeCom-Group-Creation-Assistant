package com.school.wechatgroup.security.impl;

import com.school.wechatgroup.entity.AppUser;
import com.school.wechatgroup.entity.RememberMeToken;
import com.school.wechatgroup.repository.AppUserRepository;
import com.school.wechatgroup.repository.RememberMeTokenRepository;
import com.school.wechatgroup.security.Authentication;
import com.school.wechatgroup.security.AuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class RememberMeAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(RememberMeAuthenticationProvider.class);

    private final RememberMeTokenRepository tokenRepository;
    private final AppUserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public RememberMeAuthenticationProvider(RememberMeTokenRepository tokenRepository,
                                             AppUserRepository userRepository,
                                             BCryptPasswordEncoder passwordEncoder) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        // RememberMeAuthenticationToken 此时携带 series:rawToken
        String series = authentication.getName(); // series
        String rawToken = (String) authentication.getCredentials();

        RememberMeToken token = tokenRepository.findById(series).orElse(null);
        if (token == null) {
            log.debug("Remember-Me series 不存在: {}", series);
            return null;
        }

        if (!passwordEncoder.matches(rawToken, token.getTokenHash())) {
            tokenRepository.delete(token);
            log.warn("Remember-Me token 验证失败（可能的 cookie 窃取），series={}", series);
            return null;
        }

        AppUser user = userRepository.findByUsername(token.getUsername()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            tokenRepository.delete(token);
            return null;
        }

        AppUserDetails userDetails = new AppUserDetails(user);
        return new RememberMeAuthenticationToken(userDetails, userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return RememberMeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
