package com.school.wechatgroup.vo;

public class LoginResultVO {

    private final String userId;
    private final String nickname;
    private final boolean mustChangePassword;

    private LoginResultVO(String userId, String nickname, boolean mustChangePassword) {
        this.userId = userId;
        this.nickname = nickname;
        this.mustChangePassword = mustChangePassword;
    }

    public static LoginResultVO of(String userId, String nickname) {
        return new LoginResultVO(userId, nickname, false);
    }

    public static LoginResultVO of(String userId, String nickname, boolean mustChangePassword) {
        return new LoginResultVO(userId, nickname, mustChangePassword);
    }

    public String getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }
}
