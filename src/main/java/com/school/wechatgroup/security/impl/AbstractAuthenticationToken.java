package com.school.wechatgroup.security.impl;

import com.school.wechatgroup.security.Authentication;
import com.school.wechatgroup.security.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;

public abstract class AbstractAuthenticationToken implements Authentication {

    private final Collection<? extends GrantedAuthority> authorities;
    private boolean authenticated;

    public AbstractAuthenticationToken(Collection<? extends GrantedAuthority> authorities) {
        this.authorities = (authorities != null)
                ? Collections.unmodifiableCollection(authorities) : Collections.emptyList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
}
