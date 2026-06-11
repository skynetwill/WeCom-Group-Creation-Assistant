package com.school.wechatgroup.service;

import com.school.wechatgroup.vo.AuthStatusVO;
import com.school.wechatgroup.vo.LoginResultVO;
import jakarta.servlet.http.HttpSession;

public interface AuthService {
    LoginResultVO login(String username, String password);
    void logout(HttpSession session);
    AuthStatusVO getStatus(HttpSession session);
}
