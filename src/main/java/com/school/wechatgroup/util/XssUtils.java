package com.school.wechatgroup.util;

/**
 * XSS 防护工具类
 * 对用户输入进行 HTML 特殊字符转义，防止 XSS 攻击
 */
public final class XssUtils {

    private XssUtils() {
    }

    /**
     * 将 HTML 特殊字符转义为实体，防止 XSS
     */
    public static String escapeHtml(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;"); break;
                case '<':  sb.append("&lt;"); break;
                case '>':  sb.append("&gt;"); break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&#x27;"); break;
                case '/':  sb.append("&#x2F;"); break;
                case '=':  sb.append("&#x3D;"); break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 清理输入（去除潜在的 XSS 向量）
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return escapeHtml(input.trim());
    }
}
