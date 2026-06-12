package com.school.wechatgroup.security.jwt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * JWT (JSON Web Token) 服务（对标 Spring Security OAuth2 Resource Server JWT）
 *
 * 支持：
 * - HS256 签名
 * - Token 生成（包含用户名、角色、过期时间）
 * - Token 验证 + 解析
 * - Token 刷新
 */
public class JwtTokenService {

    private final String secret;
    private final long expirationMs;
    private final long refreshExpirationMs;
    private static final String ALGORITHM = "HmacSHA256";

    public JwtTokenService(String secret, long expirationMs, long refreshExpirationMs) {
        this.secret = secret;
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /**
     * 生成访问令牌
     */
    public String generateToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", username);
        claims.put("role", role);
        claims.put("type", "access");
        claims.put("iat", System.currentTimeMillis() / 1000);
        claims.put("exp", (System.currentTimeMillis() + expirationMs) / 1000);
        return buildToken(claims);
    }

    /**
     * 生成刷新令牌
     */
    public String generateRefreshToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", username);
        claims.put("type", "refresh");
        claims.put("iat", System.currentTimeMillis() / 1000);
        claims.put("exp", (System.currentTimeMillis() + refreshExpirationMs) / 1000);
        return buildToken(claims);
    }

    /**
     * 验证并解析 token
     * @return claims map，验证失败返回 null
     */
    public Map<String, Object> validateAndParse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String signature = parts[2];

            // 验证签名
            String computed = sign(parts[0] + "." + parts[1]);
            if (!computed.equals(signature)) {
                return null;
            }

            // 解析 claims
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = new com.fasterxml.jackson.databind.ObjectMapper().readValue(payload, Map.class);

            // 检查过期
            Object exp = claims.get("exp");
            if (exp instanceof Number) {
                long expTime = ((Number) exp).longValue();
                if (System.currentTimeMillis() / 1000 > expTime) {
                    return null; // 已过期
                }
            }

            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 token 中提取用户名
     */
    public String getUsername(String token) {
        Map<String, Object> claims = validateAndParse(token);
        return claims != null ? (String) claims.get("sub") : null;
    }

    /**
     * 从 token 中提取角色
     */
    public String getRole(String token) {
        Map<String, Object> claims = validateAndParse(token);
        return claims != null ? (String) claims.get("role") : null;
    }

    /**
     * 判断 token 是否过期
     */
    public boolean isExpired(String token) {
        Map<String, Object> claims = validateAndParse(token);
        if (claims == null) return true;
        Object exp = claims.get("exp");
        return exp instanceof Number
                && System.currentTimeMillis() / 1000 > ((Number) exp).longValue();
    }

    private String buildToken(Map<String, Object> claims) {
        try {
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(claims).getBytes(StandardCharsets.UTF_8));

            String signingInput = header + "." + payload;
            String signature = sign(signingInput);

            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("JWT 生成失败", e);
        }
    }

    private String sign(String input) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("JWT 签名失败", e);
        }
    }
}
