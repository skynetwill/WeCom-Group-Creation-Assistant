package com.school.wechatgroup.security.impl;

import com.school.wechatgroup.security.GrantedAuthority;

public class SimpleGrantedAuthority implements GrantedAuthority {

    private final String authority;

    public SimpleGrantedAuthority(String authority) {
        this.authority = authority;
    }

    @Override
    public String getAuthority() {
        return authority;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SimpleGrantedAuthority)) return false;
        return authority.equals(((SimpleGrantedAuthority) obj).authority);
    }

    @Override
    public int hashCode() {
        return authority.hashCode();
    }

    @Override
    public String toString() {
        return authority;
    }
}
