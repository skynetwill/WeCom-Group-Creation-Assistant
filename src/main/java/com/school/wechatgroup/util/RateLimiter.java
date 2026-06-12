package com.school.wechatgroup.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌桶限流器
 * 基于滑动窗口的简单限流实现，用于 API 限流保护
 */
public class RateLimiter {

    private final int maxRequests;
    private final long windowMs;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    /**
     * 检查请求是否允许
     * @param key 限流键（如 IP 地址）
     * @return true=允许，false=被限流
     */
    public boolean tryAcquire(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());
        return bucket.tryAcquire(maxRequests, windowMs);
    }

    /**
     * 获取键的当前请求计数
     */
    public int getCount(String key) {
        Bucket bucket = buckets.get(key);
        return bucket != null ? bucket.get() : 0;
    }

    /**
     * 清除过期的桶
     */
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry ->
                now - entry.getValue().windowStart.get() > windowMs * 2);
    }

    private static class Bucket {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicLong windowStart = new AtomicLong(0);

        synchronized boolean tryAcquire(int maxRequests, long windowMs) {
            long now = System.currentTimeMillis();
            long ws = windowStart.get();

            if (ws == 0 || now - ws > windowMs) {
                windowStart.set(now);
                count.set(1);
                return true;
            }

            return count.incrementAndGet() <= maxRequests;
        }

        int get() {
            return count.get();
        }
    }
}
