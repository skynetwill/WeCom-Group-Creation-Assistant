package com.school.wechatgroup.security;

import java.util.Collection;

public interface Authentication {
    Collection<? extends GrantedAuthority> getAuthorities();
    Object getCredentials();
    Object getDetails();
    Object getPrincipal();
    boolean isAuthenticated();
    void setAuthenticated(boolean authenticated);
    String getName();
}
