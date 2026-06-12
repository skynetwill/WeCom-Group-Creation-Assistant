package com.school.wechatgroup.exception;

/**
 * 凭证过期异常（对标 Spring Security CredentialsExpiredException）
 */
public class CredentialsExpiredException extends RuntimeException {

    public CredentialsExpiredException(String message) {
        super(message);
    }

    public CredentialsExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
