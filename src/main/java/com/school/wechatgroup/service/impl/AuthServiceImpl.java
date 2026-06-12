package com.school.wechatgroup.service.impl;

import com.school.wechatgroup.config.AuthProperties;
import com.school.wechatgroup.constant.SessionKeys;
import com.school.wechatgroup.entity.AppUser;
import com.school.wechatgroup.entity.PasswordHistory;
import com.school.wechatgroup.exception.BusinessException;
import com.school.wechatgroup.exception.CredentialsExpiredException;
import com.school.wechatgroup.exception.DisabledException;
import com.school.wechatgroup.exception.LockedException;
import com.school.wechatgroup.repository.AppUserRepository;
import com.school.wechatgroup.repository.PasswordHistoryRepository;
import com.school.wechatgroup.repository.RememberMeTokenRepository;
import com.school.wechatgroup.security.SecurityContextHolder;
import com.school.wechatgroup.security.impl.AppUserDetails;
import com.school.wechatgroup.service.AuditService;
import com.school.wechatgroup.service.TOTPService;
import com.school.wechatgroup.service.AuthService;
import com.school.wechatgroup.util.PasswordValidator;
import com.school.wechatgroup.vo.AuthStatusVO;
import com.school.wechatgroup.vo.LoginResultVO;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AppUserRepository userRepository;
    private final RememberMeTokenRepository rememberMeTokenRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;
    private final AuditService auditService;
    private final TOTPService totpService;

    public AuthServiceImpl(AppUserRepository userRepository,
                           RememberMeTokenRepository rememberMeTokenRepository,
                           PasswordHistoryRepository passwordHistoryRepository,
                           BCryptPasswordEncoder passwordEncoder,
                           AuthProperties authProperties,
                           AuditService auditService,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) TOTPService totpService) {
        this.userRepository = userRepository;
        this.rememberMeTokenRepository = rememberMeTokenRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
        this.auditService = auditService;
        this.totpService = totpService;
    }

    @PostConstruct
    public void initDefaultAdmin() {
        if (userRepository.count() == 0) {
            String password = authProperties.getDefaultPassword();
            // 密码为空时才生成随机密码；配置了具体密码（包括 admin123）则使用配置值
            if (password == null || password.isEmpty()) {
                byte[] randomBytes = new byte[12];
                new SecureRandom().nextBytes(randomBytes);
                password = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
                log.warn("==========================================");
                log.warn("未配置默认管理员密码，已生成随机密码: {}", password);
                log.warn("请复制上面的密码，或修改 app.auth.default-password");
                log.warn("==========================================");
            } else if ("admin123".equals(password)) {
                log.warn("安全提示: 正在使用默认密码 'admin123'，生产环境请务必修改！");
            }
            AppUser admin = new AppUser();
            admin.setUsername(authProperties.getDefaultUsername());
            admin.setPassword(passwordEncoder.encode(password));
            admin.setNickname("管理员");
            admin.setEnabled(true);
            admin.setMustChangePassword(true);
            admin.setRole("ADMIN");
            admin.setPasswordLastModified(LocalDateTime.now());
            userRepository.save(admin);
            log.info("首次启动，已创建默认管理员账号: {}", authProperties.getDefaultUsername());
        }
    }

    @Override
    public LoginResultVO login(String username, String password, String clientIp) {
        return doLogin(username, password, clientIp, false);
    }

    @Override
    public LoginResultVO login(String username, String password, String clientIp, boolean rememberMe) {
        return doLogin(username, password, clientIp, rememberMe);
    }

    private LoginResultVO doLogin(String username, String password, String clientIp, boolean rememberMe) {
        AppUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            auditService.logLoginFailure(username, clientIp, "用户不存在");
            throw new BusinessException("用户名或密码错误");
        }

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            auditService.logLoginFailure(username, clientIp, "账号已禁用");
            throw new DisabledException("账号已被禁用");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutes = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1;
            auditService.logLoginFailure(username, clientIp, "账号已锁定");
            throw new LockedException("账号已被锁定，请 " + minutes + " 分钟后重试");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= authProperties.getLockThreshold()) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(authProperties.getLockDurationMinutes()));
                auditService.logAccountLocked(username, clientIp,
                        "连续失败 " + attempts + " 次，锁定 " + authProperties.getLockDurationMinutes() + " 分钟");
            }
            userRepository.save(user);
            auditService.logLoginFailure(username, clientIp, "密码错误（第 " + attempts + " 次）");
            throw new BusinessException("用户名或密码错误");
        }

        // 密码过期检查：允许登录但强制要求修改密码
        boolean passwordExpired = false;
        if (user.getPasswordLastModified() != null && authProperties.getPasswordExpiryDays() > 0) {
            LocalDateTime expiryDate = user.getPasswordLastModified()
                    .plusDays(authProperties.getPasswordExpiryDays());
            if (LocalDateTime.now().isAfter(expiryDate)) {
                passwordExpired = true;
                log.info("用户 {} 密码已过期（最后修改: {}），需强制修改", username, user.getPasswordLastModified());
            }
        }

        // 登录成功：重置失败计数 + 审计
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        user.setLoginCount(user.getLoginCount() + 1);
        userRepository.save(user);

        auditService.logLoginSuccess(username, clientIp);
        log.info("用户登录成功: {}", user.getUsername());

        boolean needPwdChange = Boolean.TRUE.equals(user.getMustChangePassword()) || passwordExpired;

        return LoginResultVO.of(user.getUsername(), user.getNickname(), needPwdChange);
    }

    /**
     * MFA 验证步骤（登录后、创建 Session 前）
     */
    public boolean verifyMFA(String username, String code, String clientIp) {
        if (totpService == null) return true;

        AppUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getMfaEnabled())) {
            return true; // 未启用 MFA，跳过
        }

        if (user.getMfaSecret() == null) return true;

        boolean success = totpService.verify(user.getMfaSecret(), code);
        auditService.logMfaVerify(username, success, clientIp);
        return success;
    }

    /**
     * 生成 MFA TOTP 密钥
     */
    public String setupMFA(String username) {
        if (totpService == null) {
            throw new BusinessException("MFA 功能未启用");
        }
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (user.getMfaSecret() != null) {
            return user.getMfaSecret();
        }
        String secret = totpService.generateSecret();
        user.setMfaSecret(secret);
        userRepository.save(user);
        return secret;
    }

    public void enableMFA(String username, String code, String clientIp) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (user.getMfaSecret() == null || totpService == null) {
            throw new BusinessException("请先生成 MFA 密钥");
        }
        if (!totpService.verify(user.getMfaSecret(), code)) {
            auditService.logMfaVerify(username, false, clientIp);
            throw new BusinessException("MFA 验证码不正确");
        }
        user.setMfaEnabled(true);
        userRepository.save(user);
        auditService.logMfaVerify(username, true, clientIp);
    }

    @Override
    public void logout(HttpSession session) {
        String userId = (String) session.getAttribute(SessionKeys.LOGIN_USER_ID);
        session.invalidate();
        SecurityContextHolder.clearContext();
        log.info("用户登出: {}", userId);
    }

    @Override
    public AuthStatusVO getStatus(HttpSession session) {
        String userId = (String) session.getAttribute(SessionKeys.LOGIN_USER_ID);
        if (userId == null) {
            return AuthStatusVO.unauthenticated();
        }
        String nickname = (String) session.getAttribute(SessionKeys.LOGIN_NICKNAME);
        String role = (String) session.getAttribute(SessionKeys.LOGIN_ROLE);
        return AuthStatusVO.authenticated(userId, nickname, role);
    }

    @Override
    public void changePassword(String username, String oldPassword, String newPassword) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("旧密码不正确");
        }

        PasswordValidator.validate(newPassword);

        // 密码历史检查：不能重复使用最近 N 次密码
        if (authProperties.getPasswordHistorySize() > 0) {
            List<String> recentHashes = passwordHistoryRepository.findRecentPasswordHashes(username);
            int checkCount = Math.min(authProperties.getPasswordHistorySize(), recentHashes.size());
            for (String hash : recentHashes.subList(0, checkCount)) {
                if (passwordEncoder.matches(newPassword, hash)) {
                    throw new BusinessException(
                            "新密码不能与最近 " + authProperties.getPasswordHistorySize() + " 次密码相同");
                }
            }
        }

        // 保存旧密码到历史记录
        PasswordHistory history = new PasswordHistory();
        history.setUsername(username);
        history.setPasswordHash(user.getPassword());
        passwordHistoryRepository.save(history);

        // 只保留最近 N 条
        List<PasswordHistory> allHistory = passwordHistoryRepository.findByUsernameOrderByCreatedAtDesc(username);
        if (allHistory.size() > authProperties.getPasswordHistorySize()) {
            List<PasswordHistory> toDelete = allHistory.subList(
                    authProperties.getPasswordHistorySize(), allHistory.size());
            passwordHistoryRepository.deleteAll(toDelete);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setPasswordLastModified(LocalDateTime.now());
        userRepository.save(user);

        auditService.logPasswordChange(username, "system");
        log.info("用户 {} 修改密码成功", username);
    }

    @Override
    public Map<String, Object> getProfile(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        AppUserDetails details = new AppUserDetails(user);
        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", details.getUsername());
        profile.put("nickname", details.getNickname());
        profile.put("role", details.getRole());
        profile.put("authorities", details.getAuthorities());
        profile.put("lastLoginTime", details.getLastLoginTime());
        profile.put("loginCount", details.getLoginCount());
        profile.put("enabled", details.isEnabled());
        profile.put("accountNonLocked", details.isAccountNonLocked());
        profile.put("credentialsNonExpired", details.isCredentialsNonExpired());
        profile.put("mfaEnabled", Boolean.TRUE.equals(user.getMfaEnabled()));
        if (user.getPasswordLastModified() != null) {
            profile.put("passwordLastModified", user.getPasswordLastModified());
            if (authProperties.getPasswordExpiryDays() > 0) {
                LocalDateTime expiry = user.getPasswordLastModified()
                        .plusDays(authProperties.getPasswordExpiryDays());
                profile.put("passwordExpiry", expiry);
                profile.put("passwordExpired", LocalDateTime.now().isAfter(expiry));
            }
        }
        return profile;
    }
}
