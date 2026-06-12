package com.school.wechatgroup.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法返回值集合后置过滤（对标 Spring Security @PostFilter）
 * 对返回的 Collection 进行过滤
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PostFilter {
    String value();
}
