package com.school.wechatgroup.security;

import java.util.Collection;

public interface SecurityContext {
    Authentication getAuthentication();
    void setAuthentication(Authentication authentication);
}
