package com.school.wechatgroup.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法参数集合前置过滤（对标 Spring Security @PreFilter）
 * 对传入的 Collection 参数进行过滤
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PreFilter {
    String value();

    /** 目标参数名 */
    String filterTarget() default "";
}
