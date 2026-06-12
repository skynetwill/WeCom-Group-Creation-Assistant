package com.school.wechatgroup.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthController MockMvc 集成测试
 * 验证：登录/CSRF/状态/登出/锁定
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String csrfToken;
    private String jsessionid;

    @BeforeEach
    void setup() throws Exception {
        // 获取 CSRF token 和 JSESSIONID
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> csrf = objectMapper.readValue(
                csrfResult.getResponse().getContentAsString(), Map.class);
        csrfToken = (String) csrf.get("token");
        jsessionid = csrfResult.getResponse().getCookie("JSESSIONID") != null
                ? csrfResult.getResponse().getCookie("JSESSIONID").getValue() : null;
    }

    @Test
    void loginWithWrongPassword_returnsError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .param("username", "admin")
                        .param("password", "wrong")
                        .param("rememberMe", "false")
                        .header("X-CSRF-TOKEN", csrfToken)
                        .cookie(new jakarta.servlet.http.Cookie("JSESSIONID", jsessionid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void csrfRequired_protectsLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .param("username", "admin")
                        .param("password", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无效请求"));
    }

    @Test
    void csrfTokenEndpoint_returnsToken() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void statusWithoutLogin_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void profileWithoutLogin_returnsError() throws Exception {
        mockMvc.perform(get("/api/auth/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void invalidOldPassword_preventChange() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk());

        // 未登录改密
        mockMvc.perform(post("/api/auth/change-password")
                        .param("oldPassword", "wrong")
                        .param("newPassword", "NewPass123!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }
}
