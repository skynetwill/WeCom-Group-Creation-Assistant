package com.school.wechatgroup.exception;

/**
 * 账号锁定异常（对标 Spring Security LockedException）
 */
public class LockedException extends RuntimeException {

    public LockedException(String message) {
        super(message);
    }

    public LockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
