package com.school.wechatgroup.vo;

public class CreateGroupResultVO {

    private final String message;
    private final String userList;
    private final String chatId;
    private final Integer errcode;
    private final String errmsg;
    private final String status;

    public CreateGroupResultVO(String message, String userList, String chatId,
                               Integer errcode, String errmsg, String status) {
        this.message = message;
        this.userList = userList;
        this.chatId = chatId;
        this.errcode = errcode;
        this.errmsg = errmsg;
        this.status = status;
    }

    public static CreateGroupResultVO success(String message, String userList, String chatId) {
        return new CreateGroupResultVO(message, userList, chatId, 0, "ok", "success");
    }

    public static CreateGroupResultVO failure(String message, Integer errcode, String errmsg) {
        return new CreateGroupResultVO(message, null, null, errcode, errmsg, "failed");
    }

    public String getMessage() {
        return message;
    }

    public String getUserList() {
        return userList;
    }

    public String getChatId() {
        return chatId;
    }

    public Integer getErrcode() {
        return errcode;
    }

    public String getErrmsg() {
        return errmsg;
    }

    public String getStatus() {
        return status;
    }
}
