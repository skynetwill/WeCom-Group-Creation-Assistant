package com.school.wechatgroup.service;

import com.school.wechatgroup.vo.AuthStatusVO;
import com.school.wechatgroup.vo.LoginResultVO;
import jakarta.servlet.http.HttpSession;

import java.util.Map;

public interface AuthService {
    LoginResultVO login(String username, String password, String clientIp);
    LoginResultVO login(String username, String password, String clientIp, boolean rememberMe);
    void logout(HttpSession session);
    AuthStatusVO getStatus(HttpSession session);
    void changePassword(String username, String oldPassword, String newPassword);
    Map<String, Object> getProfile(String username);

    /** MFA TOTP 密钥生成 */
    String setupMFA(String username);
    /** MFA TOTP 码验证 */
    boolean verifyMFA(String username, String code, String clientIp);
    /** 启用 MFA */
    void enableMFA(String username, String code, String clientIp);
}
