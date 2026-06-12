package com.school.wechatgroup.task;

import com.school.wechatgroup.config.WeChatProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信 Access Token 管理器 — Redis 共享实现
 *
 * 分布式策略：
 *   1. 每次读取先从 Redis 取（key: wechat:access_token）
 *   2. 若 Redis 中没有或即将过期，用 SETNX 竞争分布式锁（wechat:token:lock）
 *   3. 拿到锁的实例调用 API 刷新 → 写入 Redis → 释放锁
 *   4. 没拿到锁的实例短暂等待后重试 Redis 读取
 */
@Component
public class WeChatTokenManager {

    private static final Logger log = LoggerFactory.getLogger(WeChatTokenManager.class);
    private static final String TOKEN_KEY = "wechat:access_token";
    private static final String LOCK_KEY = "wechat:token:lock";
    private static final long LOCK_TTL_SEC = 30;
    private static final long REFRESH_INTERVAL_MS = 3600_000;

    private final WeChatProperties properties;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate redis;

    public WeChatTokenManager(WeChatProperties properties, RestTemplate restTemplate,
                               StringRedisTemplate redis) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.redis = redis;
    }

    @PostConstruct
    public void init() {
        refreshAccessToken();
    }

    @Scheduled(fixedRate = REFRESH_INTERVAL_MS)
    public void refreshAccessToken() {
        try {
            // 尝试获取分布式锁
            Boolean locked = redis.opsForValue()
                    .setIfAbsent(LOCK_KEY, "1", LOCK_TTL_SEC, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(locked)) {
                try {
                    doRefresh();
                } finally {
                    redis.delete(LOCK_KEY);
                }
            } else {
                log.debug("其他实例正在刷新 token，跳过");
                // 等待 2 秒后从 Redis 读取
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            log.error("Token 刷新异常", e);
        }
    }

    private void doRefresh() {
        String url = String.format(
                "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s",
                properties.getCorpid(), properties.getCorpsecret());

        try {
            Map<String, Object> response = restTemplate.exchange(url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();

            if (response != null && safeParseErrcode(response.get("errcode")) == 0) {
                String token = (String) response.get("access_token");
                // 写入 Redis，TTL 比刷新间隔多 5 分钟
                redis.opsForValue().set(TOKEN_KEY, token, 3900, TimeUnit.SECONDS);
                log.info("Token 更新成功并写入 Redis（TTL=65min）");
            } else {
                log.error("获取 Token 失败: errcode={}",
                        response != null ? response.get("errcode") : "null");
            }
        } catch (Exception e) {
            log.error("获取 Token 网络异常，请检查企业微信 API 连通性");
        }
    }

    /**
     * 获取当前有效的 access_token（优先从 Redis 读取）
     */
    public String getAccessToken() {
        String token = redis.opsForValue().get(TOKEN_KEY);
        if (token == null || token.isEmpty()) {
            log.warn("Redis 中无有效 token，触发刷新");
            refreshAccessToken();
            token = redis.opsForValue().get(TOKEN_KEY);
        }
        return token;
    }

    private int safeParseErrcode(Object errcode) {
        try {
            return Integer.parseInt(errcode.toString());
        } catch (NumberFormatException e) {
            log.warn("无法解析 errcode: {}", errcode);
            return -1;
        }
    }
}
