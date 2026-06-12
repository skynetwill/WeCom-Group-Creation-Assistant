package com.school.wechatgroup.service;

import com.school.wechatgroup.config.AuthProperties;
import com.school.wechatgroup.entity.AppUser;
import com.school.wechatgroup.repository.AppUserRepository;
import com.school.wechatgroup.repository.PasswordHistoryRepository;
import com.school.wechatgroup.repository.RememberMeTokenRepository;
import com.school.wechatgroup.service.impl.AuthServiceImpl;
import com.school.wechatgroup.util.PasswordValidator;
import com.school.wechatgroup.vo.LoginResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthService Mockito 单元测试
 * 使用 @Mock 模拟 Repository 层，验证业务逻辑
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AppUserRepository userRepository;
    @Mock private RememberMeTokenRepository tokenRepository;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private AuditService auditService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setup() {
        AuthProperties props = new AuthProperties();
        props.setLockThreshold(5);
        props.setLockDurationMinutes(15);
        props.setPasswordExpiryDays(90);
        props.setPasswordHistorySize(5);

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        authService = new AuthServiceImpl(userRepository, tokenRepository,
                passwordHistoryRepository, encoder, props, auditService, null);
    }

    @Test
    void login_userNotFound_throwsException() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThrows(Exception.class,
                () -> authService.login("nobody", "pass", "127.0.0.1"));
    }

    @Test
    void login_correctPassword_returnsResult() {
        AppUser user = createUser("admin", "password123");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        LoginResultVO result = authService.login("admin", "password123", "127.0.0.1");

        assertNotNull(result);
        assertEquals("admin", result.getUserId());
        verify(userRepository).save(any(AppUser.class));
    }

    @Test
    void login_wrongPassword_incrementsFailureCount() {
        AppUser user = createUser("admin", "correct");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThrows(Exception.class,
                () -> authService.login("admin", "wrong", "127.0.0.1"));

        assertTrue(user.getFailedAttempts() >= 1);
    }

    @Test
    void login_after5Failures_locksAccount() {
        AppUser user = createUser("admin", "correct");
        user.setFailedAttempts(5);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThrows(Exception.class,
                () -> authService.login("admin", "correct", "127.0.0.1"));
    }

    @Test
    void changePassword_correctOldPassword_succeeds() {
        AppUser user = createUser("admin", "old");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        authService.changePassword("admin", "old", "NewPass@2024!");

        assertFalse(user.getMustChangePassword());
        verify(userRepository).save(user);
    }

    private AppUser createUser(String username, String rawPassword) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(new BCryptPasswordEncoder().encode(rawPassword));
        user.setNickname("Test");
        user.setEnabled(true);
        user.setRole("ADMIN");
        user.setLoginCount(0);
        return user;
    }
}
