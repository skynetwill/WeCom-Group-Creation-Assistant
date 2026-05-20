package com.school.wechatgroup.service;

import java.util.List;

/**
 * 建群结果封装，同时返回给前端的提示消息和用于日志记录的成员列表
 */
public class CreateGroupResult {

    private final String message;
    private final List<String> userList;

    public CreateGroupResult(String message, List<String> userList) {
        this.message = message;
        this.userList = userList;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getUserList() {
        return userList;
    }
}
