package com.school.wechatgroup.vo;

public class AuthStatusVO {

    private final boolean authenticated;
    private final String userId;
    private final String nickname;
    private final String role;

    private AuthStatusVO(boolean authenticated, String userId, String nickname, String role) {
        this.authenticated = authenticated;
        this.userId = userId;
        this.nickname = nickname;
        this.role = role;
    }

    public static AuthStatusVO authenticated(String userId, String nickname) {
        return new AuthStatusVO(true, userId, nickname, null);
    }

    public static AuthStatusVO authenticated(String userId, String nickname, String role) {
        return new AuthStatusVO(true, userId, nickname, role);
    }

    public static AuthStatusVO unauthenticated() {
        return new AuthStatusVO(false, null, null, null);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getRole() {
        return role;
    }
}
