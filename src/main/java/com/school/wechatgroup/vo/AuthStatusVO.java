package com.school.wechatgroup.vo;

public class AuthStatusVO {

    private final boolean authenticated;
    private final String userId;
    private final String nickname;

    private AuthStatusVO(boolean authenticated, String userId, String nickname) {
        this.authenticated = authenticated;
        this.userId = userId;
        this.nickname = nickname;
    }

    public static AuthStatusVO authenticated(String userId, String nickname) {
        return new AuthStatusVO(true, userId, nickname);
    }

    public static AuthStatusVO unauthenticated() {
        return new AuthStatusVO(false, null, null);
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
}
