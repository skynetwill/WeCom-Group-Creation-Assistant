package com.school.wechatgroup.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法执行前权限校验（对标 Spring Security @PreAuthorize）
 *
 * 支持的 SpEL 表达式：
 *   hasRole('ADMIN')       — 检查角色
 *   hasAuthority('ROLE_ADMIN')  — 检查权限
 *   isAuthenticated()      — 是否已登录
 *   isAnonymous()          — 是否匿名
 *   permitAll              — 允许所有
 *   denyAll                — 拒绝所有
 *   #paramName             — 引用方法参数
 *   isCurrentUser(#name)   — 是否当前用户
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PreAuthorize {
    String value();
}
