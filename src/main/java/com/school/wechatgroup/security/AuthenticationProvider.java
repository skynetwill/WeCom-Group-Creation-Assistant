package com.school.wechatgroup.security;

public interface AuthenticationProvider {
    Authentication authenticate(Authentication authentication);
    boolean supports(Class<?> authentication);
}
