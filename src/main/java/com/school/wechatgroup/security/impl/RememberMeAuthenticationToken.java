package com.school.wechatgroup.security.impl;

import com.school.wechatgroup.security.GrantedAuthority;
import com.school.wechatgroup.security.UserDetails;

import java.util.Collection;

public class RememberMeAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;

    public RememberMeAuthenticationToken(Object principal,
                                          Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public String getName() {
        return principal instanceof UserDetails
                ? ((UserDetails) principal).getUsername()
                : principal.toString();
    }
}
