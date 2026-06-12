package com.school.wechatgroup.security;

import java.util.Collection;

public interface UserDetails {
    String getUsername();
    String getPassword();
    Collection<? extends GrantedAuthority> getAuthorities();
    boolean isEnabled();
    default boolean isAccountNonLocked() { return true; }
    default boolean isAccountNonExpired() { return true; }
    default boolean isCredentialsNonExpired() { return true; }
    String getNickname();
    String getRole();
    boolean isMustChangePassword();
    java.time.LocalDateTime getLastLoginTime();
    int getLoginCount();
}
