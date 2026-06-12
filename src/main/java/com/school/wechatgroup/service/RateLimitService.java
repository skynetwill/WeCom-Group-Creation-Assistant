package com.school.wechatgroup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 登录限流服务 — Redis 共享实现
 *
 * Redis 数据结构：
 *   ratelimit:{ip}:{window_second} → INCR 计数，EXPIRE 自动过期
 *   滑动窗口：60 秒内最多 5 次，每次请求 INCR 判断是否超阈值
 */
@Component
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String KEY_PREFIX = "ratelimit:";
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 60;

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 检查是否被限流（所有实例共享计数）
     */
    public boolean isRateLimited(String ip) {
        long window = System.currentTimeMillis() / 1000 / WINDOW_SECONDS;
        String key = KEY_PREFIX + ip + ":" + window;

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, WINDOW_SECONDS * 2, TimeUnit.SECONDS);
        }
        return count != null && count > MAX_ATTEMPTS;
    }

    /**
     * 清除指定 IP 的限流记录
     */
    public void clear(String ip) {
        long window = System.currentTimeMillis() / 1000 / WINDOW_SECONDS;
        redis.delete(KEY_PREFIX + ip + ":" + window);
    }
}
