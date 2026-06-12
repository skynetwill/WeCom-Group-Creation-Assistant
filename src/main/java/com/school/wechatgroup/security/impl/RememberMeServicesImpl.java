package com.school.wechatgroup.security.impl;

import com.school.wechatgroup.entity.AppUser;
import com.school.wechatgroup.entity.RememberMeToken;
import com.school.wechatgroup.repository.AppUserRepository;
import com.school.wechatgroup.repository.RememberMeTokenRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Remember-Me 服务实现（对标 Spring Security PersistentTokenBasedRememberMeServices）
 *
 * 使用 SHA-256（快速哈希）替代 BCrypt（慢哈希），因为：
 * - Remember-Me token 是随机生成的 32 字节安全随机数，不需要防暴力破解
 * - 每次自动登录都会刷新 token（一次一用），BCrypt 的性能开销没有意义
 */
public class RememberMeServicesImpl {

    private static final Logger log = LoggerFactory.getLogger(RememberMeServicesImpl.class);
    private static final String COOKIE_NAME = "remember-me";
    private static final int DEFAULT_VALIDITY_DAYS = 14;

    private final RememberMeTokenRepository tokenRepository;
    private final AppUserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public RememberMeServicesImpl(RememberMeTokenRepository tokenRepository,
                                   AppUserRepository userRepository) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
    }

    /**
     * 生成浏览器指纹：User-Agent + IP 的 SHA-256
     */
    public String generateFingerprint(HttpServletRequest request) {
        try {
            String ua = request.getHeader("User-Agent");
            String ip = request.getRemoteAddr();
            String fingerprint = ua + "|" + ip;
            return sha256(fingerprint);
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 登录成功后创建 Remember-Me Cookie 和 DB 记录
     */
    public void onLoginSuccess(HttpServletRequest request, HttpServletResponse response,
                                String username, int validityDays) {
        String series = UUID.randomUUID().toString();
        byte[] rawTokenBytes = new byte[32];
        secureRandom.nextBytes(rawTokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawTokenBytes);

        String fingerprint = generateFingerprint(request);
        String tokenWithFingerprint = rawToken + ":" + fingerprint + ":" + series;
        String tokenHash = sha256(tokenWithFingerprint);

        RememberMeToken token = new RememberMeToken();
        token.setSeries(series);
        token.setUsername(username);
        token.setTokenHash(tokenHash);
        token.setLastUsed(LocalDateTime.now());
        tokenRepository.save(token);

        String cookieValue = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((series + ":" + rawToken).getBytes());
        Cookie cookie = new Cookie(COOKIE_NAME, cookieValue);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(validityDays * 24 * 60 * 60);
        cookie.setSecure(request.isSecure());
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);

        log.info("Remember-Me cookie 已创建: username={}, series={}", username, series);
    }

    /**
     * 从 Cookie 中验证并自动登录
     */
    public RememberMeAuthResult autoLogin(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        Cookie rememberMeCookie = null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) {
                rememberMeCookie = c;
                break;
            }
        }

        if (rememberMeCookie == null || rememberMeCookie.getValue() == null
                || rememberMeCookie.getValue().isEmpty()) {
            return null;
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(rememberMeCookie.getValue()));
            int colonIdx = decoded.indexOf(':');
            if (colonIdx <= 0) {
                clearCookie(response);
                return null;
            }

            String series = decoded.substring(0, colonIdx);
            String rawToken = decoded.substring(colonIdx + 1);

            RememberMeToken token = tokenRepository.findById(series).orElse(null);
            if (token == null) {
                clearCookie(response);
                return null;
            }

            // 验证 token + 指纹
            String fingerprint = generateFingerprint(request);
            String expectedHash = sha256(rawToken + ":" + fingerprint + ":" + series);

            if (!constantTimeEquals(expectedHash, token.getTokenHash())) {
                tokenRepository.delete(token);
                clearCookie(response);
                log.warn("Remember-Me 验证失败（可能的 cookie 窃取），series={}", series);
                return null;
            }

            // 验证通过，检查用户状态
            AppUser user = userRepository.findByUsername(token.getUsername()).orElse(null);
            if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
                tokenRepository.delete(token);
                clearCookie(response);
                return null;
            }

            // 刷新 token
            byte[] newRawBytes = new byte[32];
            secureRandom.nextBytes(newRawBytes);
            String newRawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(newRawBytes);
            String newFingerprint = generateFingerprint(request);
            String newHash = sha256(newRawToken + ":" + newFingerprint + ":" + series);

            token.setTokenHash(newHash);
            token.setLastUsed(LocalDateTime.now());
            tokenRepository.save(token);

            // 更新 Cookie
            String newCookieValue = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((series + ":" + newRawToken).getBytes());
            Cookie newCookie = new Cookie(COOKIE_NAME, newCookieValue);
            newCookie.setHttpOnly(true);
            newCookie.setPath("/");
            newCookie.setMaxAge(DEFAULT_VALIDITY_DAYS * 24 * 60 * 60);
            newCookie.setSecure(request.isSecure());
            newCookie.setAttribute("SameSite", "Lax");
            response.addCookie(newCookie);

            return new RememberMeAuthResult(token.getUsername(), newRawToken, series);

        } catch (Exception e) {
            log.error("Remember-Me 自动登录异常", e);
            clearCookie(response);
            return null;
        }
    }

    /**
     * 注销时清除 Remember-Me
     */
    public void logout(HttpServletRequest request, HttpServletResponse response, String username) {
        clearCookie(response);
        if (username != null) {
            tokenRepository.deleteByUsername(username);
        }
    }

    private void clearCookie(HttpServletResponse response) {
        Cookie deleteCookie = new Cookie(COOKIE_NAME, "");
        deleteCookie.setHttpOnly(true);
        deleteCookie.setPath("/");
        deleteCookie.setMaxAge(0);
        response.addCookie(deleteCookie);
    }

    /**
     * SHA-256 哈希（Base64 编码）
     */
    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    /**
     * 常量时间比较（防时序攻击）
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] bytesA = a.getBytes(StandardCharsets.UTF_8);
        byte[] bytesB = b.getBytes(StandardCharsets.UTF_8);
        if (bytesA.length != bytesB.length) return false;
        int result = 0;
        for (int i = 0; i < bytesA.length; i++) {
            result |= bytesA[i] ^ bytesB[i];
        }
        return result == 0;
    }

    public static class RememberMeAuthResult {
        private final String username;
        private final String newRawToken;
        private final String series;

        public RememberMeAuthResult(String username, String newRawToken, String series) {
            this.username = username;
            this.newRawToken = newRawToken;
            this.series = series;
        }

        public String getUsername() { return username; }
        public String getNewRawToken() { return newRawToken; }
        public String getSeries() { return series; }
    }
}
