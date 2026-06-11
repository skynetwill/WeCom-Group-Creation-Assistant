package com.school.wechatgroup.vo;

public class LoginResultVO {

    private final String userId;
    private final String nickname;

    private LoginResultVO(String userId, String nickname) {
        this.userId = userId;
        this.nickname = nickname;
    }

    public static LoginResultVO of(String userId, String nickname) {
        return new LoginResultVO(userId, nickname);
    }

    public String getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }
}
