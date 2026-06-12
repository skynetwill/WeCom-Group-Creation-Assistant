package com.school.wechatgroup.security.impl;

import com.school.wechatgroup.security.Authentication;
import com.school.wechatgroup.security.SecurityContext;

public class SecurityContextImpl implements SecurityContext {

    private Authentication authentication;

    @Override
    public Authentication getAuthentication() {
        return authentication;
    }

    @Override
    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }
}
