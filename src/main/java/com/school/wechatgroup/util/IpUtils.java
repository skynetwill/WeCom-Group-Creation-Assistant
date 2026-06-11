package com.school.wechatgroup.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP 地址工具类
 */
public final class IpUtils {

    private IpUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isInvalid(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (isInvalid(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (isInvalid(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (isInvalid(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private static boolean isInvalid(String ip) {
        return ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip);
    }
}
