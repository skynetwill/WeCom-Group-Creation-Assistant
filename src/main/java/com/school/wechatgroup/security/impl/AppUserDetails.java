package com.school.wechatgroup.security.impl;

import com.school.wechatgroup.entity.AppUser;
import com.school.wechatgroup.security.GrantedAuthority;
import com.school.wechatgroup.security.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AppUserDetails implements UserDetails {

    private final String username;
    private final String password;
    private final String nickname;
    private final String role;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final boolean mustChangePassword;
    private final LocalDateTime lastLoginTime;
    private final int loginCount;
    private final List<GrantedAuthority> authorities;

    public AppUserDetails(AppUser user) {
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.nickname = user.getNickname();
        this.role = user.getRole() != null ? user.getRole() : "USER";
        this.enabled = Boolean.TRUE.equals(user.getEnabled());
        this.accountNonLocked = user.getLockedUntil() == null
                || user.getLockedUntil().isBefore(LocalDateTime.now());
        this.mustChangePassword = Boolean.TRUE.equals(user.getMustChangePassword());
        this.lastLoginTime = user.getLastLoginTime();
        this.loginCount = user.getLoginCount() != null ? user.getLoginCount() : 0;

        this.authorities = new ArrayList<>();
        this.authorities.add(new SimpleGrantedAuthority("ROLE_" + this.role));
        if ("ADMIN".equals(this.role)) {
            this.authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !mustChangePassword;
    }

    @Override
    public String getNickname() {
        return nickname;
    }

    @Override
    public String getRole() {
        return role;
    }

    @Override
    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    @Override
    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    @Override
    public int getLoginCount() {
        return loginCount;
    }
}
