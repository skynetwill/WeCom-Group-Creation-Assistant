package com.school.wechatgroup.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法执行后权限校验（对标 Spring Security @PostAuthorize）
 * 可以引用返回值 returnObject
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PostAuthorize {
    String value();
}
