package com.school.wechatgroup.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求 DTO — Spring Validation 示例
 */
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 64, message = "用户名长度2-64位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度8-128位")
    private String password;

    private boolean rememberMe;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isRememberMe() { return rememberMe; }
    public void setRememberMe(boolean rememberMe) { this.rememberMe = rememberMe; }
}
