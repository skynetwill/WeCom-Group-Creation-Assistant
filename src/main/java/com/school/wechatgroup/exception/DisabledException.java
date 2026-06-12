package com.school.wechatgroup.exception;

/**
 * 账号禁用异常（对标 Spring Security DisabledException）
 */
public class DisabledException extends RuntimeException {

    public DisabledException(String message) {
        super(message);
    }

    public DisabledException(String message, Throwable cause) {
        super(message, cause);
    }
}
