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

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class SessionRegistryConfig {

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistry();
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
     * 会话注册表（对标 Spring Security SessionRegistry）
     * 管理用户会话列表，支持并发控制和管理员查询/强制下线
     */
    public static class SessionRegistry {
        private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);
        private final ConcurrentHashMap<String, Deque<SessionInfo>> userSessions = new ConcurrentHashMap<>();

        /**
         * 注册新会话
         * @return true=注册成功，false=超出限制（最旧会话已被记录为待踢出）
         */
        public synchronized boolean registerSession(String userId, String sessionId, int maxSessions) {
            userSessions.computeIfAbsent(userId, k -> new LinkedList<>());
            Deque<SessionInfo> sessions = userSessions.get(userId);

            String ip = extractIp(sessionId);
            SessionInfo info = new SessionInfo(sessionId, LocalDateTime.now(), ip);

            if (maxSessions > 0 && sessions.size() >= maxSessions) {
                SessionInfo oldest = sessions.pollFirst();
                log.info("用户 {} 超过最大会话数 {}，标记最旧会话为待踢出: {}", userId, maxSessions, oldest.getSessionId());
                info.setKickedOut(true);
                sessions.addLast(info);
                return false;
            }

            sessions.addLast(info);
            return true;
        }

        /**
         * 移除会话
         */
        public void removeSession(String userId, String sessionId) {
            userSessions.computeIfPresent(userId, (k, sessions) -> {
                sessions.removeIf(s -> s.getSessionId().equals(sessionId));
                if (sessions.isEmpty()) return null;
                return sessions;
            });
        }

        /**
         * 获取用户活跃会话数
         */
        public int getSessionCount(String userId) {
            Deque<SessionInfo> sessions = userSessions.get(userId);
            return sessions != null ? sessions.size() : 0;
        }

        /**
         * 获取所有活跃会话（管理员查询用）
         */
        public List<Map<String, Object>> getAllActiveSessions() {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<String, Deque<SessionInfo>> entry : userSessions.entrySet()) {
                for (SessionInfo info : entry.getValue()) {
                    Map<String, Object> sessionMap = new HashMap<>();
                    sessionMap.put("username", entry.getKey());
                    sessionMap.put("sessionId", info.getSessionId());
                    sessionMap.put("loginTime", info.getLoginTime());
                    sessionMap.put("ip", info.getIp());
                    sessionMap.put("kickedOut", info.isKickedOut());
                    result.add(sessionMap);
                }
            }
            return result;
        }

        /**
         * 强制踢出用户所有会话
         */
        public int expireUserSessions(String userId) {
            Deque<SessionInfo> sessions = userSessions.remove(userId);
            return sessions != null ? sessions.size() : 0;
        }

        private String extractIp(String sessionId) {
            return "unknown";
        }

        public static class SessionInfo {
            private final String sessionId;
            private final LocalDateTime loginTime;
            private final String ip;
            private boolean kickedOut;

            public SessionInfo(String sessionId, LocalDateTime loginTime, String ip) {
                this.sessionId = sessionId;
                this.loginTime = loginTime;
                this.ip = ip;
            }

            public String getSessionId() { return sessionId; }
            public LocalDateTime getLoginTime() { return loginTime; }
            public String getIp() { return ip; }
            public boolean isKickedOut() { return kickedOut; }
            public void setKickedOut(boolean kickedOut) { this.kickedOut = kickedOut; }
        }
    }
}
