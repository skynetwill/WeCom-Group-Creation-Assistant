package com.school.wechatgroup.exception;

/**
 * 权限拒绝异常（对标 Spring Security AccessDeniedException）
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }

    public AccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
