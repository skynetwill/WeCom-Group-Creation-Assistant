package com.school.wechatgroup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * TOTP（时间同步一次性密码）服务
 * 兼容 Google Authenticator / Microsoft Authenticator
 * 对标 Spring Security 的 TOTP 多因素认证
 */
@Service
@ConditionalOnProperty(name = "app.auth.mfa.enabled", havingValue = "true", matchIfMissing = false)
public class TOTPService {

    private static final Logger log = LoggerFactory.getLogger(TOTPService.class);

    private static final String ALGORITHM = "HmacSHA1";
    private static final int TIME_STEP = 30; // 30 秒
    private static final int CODE_DIGITS = 6;
    private static final int WINDOW_SIZE = 1; // 前后各 1 个窗口

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 生成新的 TOTP 密钥（Base32 编码）
     */
    public String generateSecret() {
        byte[] secret = new byte[20];
        secureRandom.nextBytes(secret);
        return base32Encode(secret);
    }

    /**
     * 验证 TOTP 码
     * @param secret Base32 编码的密钥
     * @param code   用户输入的 6 位验证码
     * @return true=验证通过
     */
    public boolean verify(String secret, String code) {
        if (secret == null || code == null || code.length() != CODE_DIGITS) {
            return false;
        }

        try {
            byte[] secretBytes = base32Decode(secret);
            long timeWindow = System.currentTimeMillis() / 1000 / TIME_STEP;

            for (int offset = -WINDOW_SIZE; offset <= WINDOW_SIZE; offset++) {
                String expected = generateTOTP(secretBytes, timeWindow + offset);
                if (expected.equals(code)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("TOTP 验证失败", e);
        }
        return false;
    }

    /**
     * 生成 TOTP URI（用于生成 QR 码）
     */
    public String generateUri(String secret, String account, String issuer) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                issuer, account, secret, issuer, CODE_DIGITS, TIME_STEP);
    }

    private String generateTOTP(byte[] secret, long timeWindow)
            throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] msg = new byte[8];
        long value = timeWindow;
        for (int i = 8; i-- > 0; value >>>= 8) {
            msg[i] = (byte) value;
        }

        SecretKeySpec signKey = new SecretKeySpec(secret, "RAW");
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(signKey);
        byte[] hash = mac.doFinal(msg);

        int offset = hash[hash.length - 1] & 0x0F;
        long truncatedHash = 0;
        for (int i = 0; i < 4; i++) {
            truncatedHash <<= 8;
            truncatedHash |= (hash[offset + i] & 0xFF);
        }
        truncatedHash &= 0x7FFFFFFF;
        truncatedHash %= (long) Math.pow(10, CODE_DIGITS);

        return String.format("%0" + CODE_DIGITS + "d", truncatedHash);
    }

    private String base32Encode(byte[] data) {
        // 使用 RFC 4648 Base32 编码
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i += 5) {
            byte[] chunk = new byte[Math.min(5, data.length - i) + 1];
            System.arraycopy(data, i, chunk, 1, chunk.length - 1);
            int bits = 0;
            for (byte b : chunk) {
                bits = (bits << 8) | (b & 0xFF);
            }
            int padCount = (5 - (chunk.length - 1)) * 8 / 5;
            for (int j = 0; j < 8; j++) {
                if (j < 8 - padCount) {
                    int idx = (bits >> (35 - j * 5)) & 0x1F;
                    sb.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".charAt(idx));
                }
            }
        }
        return sb.toString();
    }

    private byte[] base32Decode(String base32) {
        base32 = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        byte[] decoded = new byte[base32.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int idx = 0;

        for (char c : base32.toCharArray()) {
            int val = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                decoded[idx++] = (byte) (buffer >> bitsLeft);
            }
        }
        return decoded;
    }
}
