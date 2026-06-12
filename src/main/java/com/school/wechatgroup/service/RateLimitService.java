package com.school.wechatgroup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录限流服务
 * 从 AuthController 分离，避免 @Scheduled 与 @RestController 混合
 */
@Component
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final ConcurrentHashMap<String, Integer> loginAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> attemptWindowStart = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 60_000;

    /**
     * 检查是否被限流
     */
    public boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        Long windowStart = attemptWindowStart.get(ip);
        if (windowStart == null || now - windowStart > WINDOW_MS) {
            attemptWindowStart.put(ip, now);
            loginAttempts.put(ip, 1);
            return false;
        }
        int attempts = loginAttempts.merge(ip, 1, Integer::sum);
        return attempts > MAX_ATTEMPTS;
    }

    /**
     * 清除指定 IP 的限流记录（登录成功后）
     */
    public void clear(String ip) {
        loginAttempts.remove(ip);
        attemptWindowStart.remove(ip);
    }

    @Scheduled(fixedRate = 300000)
    public void cleanExpiredEntries() {
        long now = System.currentTimeMillis();
        attemptWindowStart.keySet().removeIf(ip -> {
            Long time = attemptWindowStart.get(ip);
            if (time != null && now - time > WINDOW_MS * 2) {
                loginAttempts.remove(ip);
                return true;
            }
            return false;
        });
    }
}
