package com.school.wechatgroup.security.password;

import com.school.wechatgroup.security.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;

/**
 * 委派密码编码器（对标 Spring Security DelegatingPasswordEncoder）
 *
 * 支持多种编码算法的密码存储和渐进式迁移。
 * 存储格式: {算法标识}哈希值
 *
 * 示例:
 *   {bcrypt}$2a$10$...
 *   {noop}plaintextpassword
 *   {sha256}hashedvalue
 *   {pbkdf2}hashedvalue
 *
 * 迁移场景:
 *   1. 用户用 {sha256} 登录 → 验证通过 → 自动升级为 {bcrypt}
 *   2. 新用户注册 → 默认使用 {bcrypt}
 */
public class DelegatingPasswordEncoder implements PasswordEncoder {

    private final String defaultEncoderId;
    private final Map<String, PasswordEncoder> encoders;
    private final Map<String, PasswordEncoder> encoderMap = new HashMap<>();

    public DelegatingPasswordEncoder(String defaultEncoderId,
                                      Map<String, PasswordEncoder> encoders) {
        this.defaultEncoderId = defaultEncoderId;
        this.encoders = encoders;
        this.encoderMap.putAll(encoders);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return "{" + defaultEncoderId + "}" + encoders.get(defaultEncoderId).encode(rawPassword);
    }

    public String encode(CharSequence rawPassword, String encoderId) {
        PasswordEncoder encoder = encoderMap.get(encoderId);
        if (encoder == null) {
            throw new IllegalArgumentException("不支持的密码编码器: " + encoderId);
        }
        return "{" + encoderId + "}" + encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) return false;

        String encoderId = extractEncoderId(encodedPassword);
        PasswordEncoder encoder = encoderMap.get(encoderId);

        if (encoder == null) {
            // 无前缀时使用默认编码器尝试
            return encoders.get(defaultEncoderId).matches(rawPassword, encodedPassword);
        }

        String actualPassword = extractPassword(encodedPassword);
        return encoder.matches(rawPassword, actualPassword);
    }

    /**
     * 密码升级检测：如果当前编码算法不是默认算法，返回重新编码的密码
     */
    public String upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null) return null;
        String encoderId = extractEncoderId(encodedPassword);
        if (!defaultEncoderId.equals(encoderId)) return null; // waiting for re-encode
        return encodedPassword;
    }

    /**
     * 检查是否需要升级（非默认编码器）
     */
    public boolean needsUpgrade(String encodedPassword) {
        if (encodedPassword == null) return false;
        String id = extractEncoderId(encodedPassword);
        return id != null && !defaultEncoderId.equals(id);
    }

    private String extractEncoderId(String encodedPassword) {
        if (encodedPassword == null || !encodedPassword.startsWith("{")) return null;
        int endIdx = encodedPassword.indexOf("}");
        if (endIdx < 0) return null;
        return encodedPassword.substring(1, endIdx);
    }

    private String extractPassword(String encodedPassword) {
        int endIdx = encodedPassword.indexOf("}");
        return endIdx < 0 ? encodedPassword : encodedPassword.substring(endIdx + 1);
    }

    /**
     * 工厂方法：创建默认的委派编码器
     * 默认算法: bcrypt
     * 支持: bcrypt, noop, sha256
     */
    public static DelegatingPasswordEncoder createDefault(PasswordEncoder bcryptEncoder) {
        Map<String, PasswordEncoder> encoderMap = new HashMap<>();
        encoderMap.put("bcrypt", bcryptEncoder);
        encoderMap.put("noop", new NoOpPasswordEncoder());
        encoderMap.put("sha256", new Sha256PasswordEncoder());
        return new DelegatingPasswordEncoder("bcrypt", encoderMap);
    }

    // ================== 内置编码器 ==================

    /** 明文编码器（仅开发环境） */
    static class NoOpPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return rawPassword.toString();
        }
        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return rawPassword.toString().equals(encodedPassword);
        }
    }

    /** SHA-256 编码器 */
    static class Sha256PasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(rawPassword.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return java.util.Base64.getEncoder().encodeToString(hash);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encode(rawPassword).equals(encodedPassword);
        }
    }
}
