package com.school.wechatgroup.security.expression;

import com.school.wechatgroup.security.Authentication;
import com.school.wechatgroup.security.GrantedAuthority;
import com.school.wechatgroup.security.SecurityContextHolder;

/**
 * Security 表达式操作（对标 Spring Security SecurityExpressionOperations）
 * 提供 SpEL 表达式可调用的方法：hasRole, hasAuthority, isAuthenticated 等
 */
public class SecurityExpressionOperations {

    private final Authentication authentication;
    private final Object filterObject;
    private final Object returnObject;

    public SecurityExpressionOperations(Authentication authentication,
                                         Object filterObject,
                                         Object returnObject) {
        this.authentication = authentication;
        this.filterObject = filterObject;
        this.returnObject = returnObject;
    }

    public static SecurityExpressionOperations create() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return new SecurityExpressionOperations(auth, null, null);
    }

    public static SecurityExpressionOperations create(Object filterObject, Object returnObject) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return new SecurityExpressionOperations(auth, filterObject, returnObject);
    }

    /** 是否已认证 */
    public boolean isAuthenticated() {
        return authentication != null && authentication.isAuthenticated();
    }

    /** 是否未认证（匿名访问） */
    public boolean isAnonymous() {
        return authentication == null || !authentication.isAuthenticated();
    }

    /** 是否有指定角色 */
    public boolean hasRole(String role) {
        return hasAuthority("ROLE_" + role);
    }

    /** 是否有指定权限 */
    public boolean hasAuthority(String authority) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        for (GrantedAuthority ga : authentication.getAuthorities()) {
            if (authority.equals(ga.getAuthority())) return true;
        }
        return false;
    }

    /** 是否有多个角色中的任意一个 */
    public boolean hasAnyRole(String... roles) {
        for (String r : roles) {
            if (hasRole(r)) return true;
        }
        return false;
    }

    /** 是否有多个权限中的任意一个 */
    public boolean hasAnyAuthority(String... authorities) {
        for (String a : authorities) {
            if (hasAuthority(a)) return true;
        }
        return false;
    }

    /** 当前用户是否等于指定用户 */
    public boolean isCurrentUser(String username) {
        return authentication != null && authentication.getName().equals(username);
    }

    /** 方法参数（PreAuthorize 中引用 #paramName） */
    public Object getFilterObject() { return filterObject; }

    /** 返回值（PostAuthorize 中引用 returnObject） */
    public Object getReturnObject() { return returnObject; }

    /** 允许所有 */
    public boolean permitAll() { return true; }

    /** 拒绝所有 */
    public boolean denyAll() { return false; }
}
