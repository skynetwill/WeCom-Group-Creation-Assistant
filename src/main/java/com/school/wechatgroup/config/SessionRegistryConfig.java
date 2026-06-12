package com.school.wechatgroup.config;

import com.school.wechatgroup.constant.SessionKeys;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class SessionRegistryConfig {

    @Bean
    public SessionRegistry sessionRegistry(StringRedisTemplate redisTemplate) {
        return new RedisSessionRegistry(redisTemplate);
    }

    @Bean
    public HttpSessionListener sessionCountingListener(SessionRegistry sessionRegistry,
                                                       AuthProperties authProperties) {
        return new HttpSessionListener() {
            @Override
            public void sessionCreated(HttpSessionEvent se) {
            }

            @Override
            public void sessionDestroyed(HttpSessionEvent se) {
                HttpSession session = se.getSession();
                String userId = (String) session.getAttribute(SessionKeys.LOGIN_USER_ID);
                if (userId != null) {
                    sessionRegistry.removeSession(userId, session.getId());
                }
            }
        };
    }

    /**
     * 会话注册表接口
     */
    public interface SessionRegistry {
        boolean registerSession(String userId, String sessionId, String ip, int maxSessions);
        void removeSession(String userId, String sessionId);
        int getSessionCount(String userId);
        List<Map<String, Object>> getAllActiveSessions();
        int expireUserSessions(String userId);
    }

    /**
     * Redis 实现：跨实例共享会话注册表
     *
     * Redis 数据结构：
     *   sessions:{userId} → Sorted Set (member=sessionId|ip|timestamp, score=timestamp)
     *   每次操作通过 SCARD 检查数量，ZREMRANGEBYRANK 超出限制时移除最旧
     */
    static class RedisSessionRegistry implements SessionRegistry {
        private static final Logger log = LoggerFactory.getLogger(RedisSessionRegistry.class);
        private static final String KEY_PREFIX = "sessions:";

        private final StringRedisTemplate redis;

        RedisSessionRegistry(StringRedisTemplate redis) {
            this.redis = redis;
        }

        @Override
        public boolean registerSession(String userId, String sessionId, String ip, int maxSessions) {
            String key = KEY_PREFIX + userId;
            long now = System.currentTimeMillis();
            String member = sessionId + "|" + ip + "|" + now;

            redis.opsForZSet().add(key, member, now);

            Long count = redis.opsForZSet().zCard(key);
            if (count != null && maxSessions > 0 && count > maxSessions) {
                // 移除最旧的
                Set<String> removed = redis.opsForZSet().range(key, 0, 0);
                if (removed != null && !removed.isEmpty()) {
                    String oldestMember = removed.iterator().next();
                    redis.opsForZSet().remove(key, oldestMember);
                    log.info("用户 {} 超过最大会话数 {}，移除最旧会话: {}", userId, maxSessions,
                            oldestMember.split("\\|")[0]);
                }
                return false;
            }
            return true;
        }

        @Override
        public void removeSession(String userId, String sessionId) {
            String key = KEY_PREFIX + userId;
            // 按前缀匹配删除
            Set<String> members = redis.opsForZSet().range(key, 0, -1);
            if (members != null) {
                for (String m : members) {
                    if (m.startsWith(sessionId + "|")) {
                        redis.opsForZSet().remove(key, m);
                        break;
                    }
                }
            }
        }

        @Override
        public int getSessionCount(String userId) {
            Long count = redis.opsForZSet().zCard(KEY_PREFIX + userId);
            return count != null ? count.intValue() : 0;
        }

        @Override
        public List<Map<String, Object>> getAllActiveSessions() {
            Set<String> keys = redis.keys(KEY_PREFIX + "*");
            if (keys == null) return Collections.emptyList();

            List<Map<String, Object>> result = new ArrayList<>();
            for (String key : keys) {
                String username = key.substring(KEY_PREFIX.length());
                Set<String> members = redis.opsForZSet().range(key, 0, -1);
                if (members != null) {
                    for (String m : members) {
                        String[] parts = m.split("\\|");
                        Map<String, Object> map = new HashMap<>();
                        map.put("username", username);
                        map.put("sessionId", parts.length > 0 ? parts[0] : "");
                        map.put("ip", parts.length > 1 ? parts[1] : "unknown");
                        map.put("loginTime", parts.length > 2
                                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(parts[2])),
                                        ZoneId.systemDefault())
                                : null);
                        result.add(map);
                    }
                }
            }
            return result;
        }

        @Override
        public int expireUserSessions(String userId) {
            String key = KEY_PREFIX + userId;
            Long count = redis.opsForZSet().zCard(key);
            redis.delete(key);
            return count != null ? count.intValue() : 0;
        }
    }
}
