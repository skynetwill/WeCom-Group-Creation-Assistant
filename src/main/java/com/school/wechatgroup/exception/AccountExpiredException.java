package com.school.wechatgroup.exception;

/**
 * 账号过期异常（对标 Spring Security AccountExpiredException）
 */
public class AccountExpiredException extends RuntimeException {

    public AccountExpiredException(String message) {
        super(message);
    }

    public AccountExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
