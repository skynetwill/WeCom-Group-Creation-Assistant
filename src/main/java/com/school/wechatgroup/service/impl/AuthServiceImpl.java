package com.school.wechatgroup.service.impl;

import com.school.wechatgroup.config.AuthProperties;
import com.school.wechatgroup.constant.SessionKeys;
import com.school.wechatgroup.entity.AppUser;
import com.school.wechatgroup.exception.BusinessException;
import com.school.wechatgroup.repository.AppUserRepository;
import com.school.wechatgroup.service.AuthService;
import com.school.wechatgroup.vo.AuthStatusVO;
import com.school.wechatgroup.vo.LoginResultVO;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AppUserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    public AuthServiceImpl(AppUserRepository userRepository,
                           BCryptPasswordEncoder passwordEncoder,
                           AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
    }

    @PostConstruct
    public void initDefaultAdmin() {
        if (userRepository.count() == 0) {
            AppUser admin = new AppUser();
            admin.setUsername(authProperties.getDefaultUsername());
            admin.setPassword(passwordEncoder.encode(authProperties.getDefaultPassword()));
            admin.setNickname("管理员");
            admin.setEnabled(true);
            userRepository.save(admin);
            log.info("首次启动，已创建默认管理员账号: {}", authProperties.getDefaultUsername());
            if (authProperties.getDefaultPassword().equals("admin123")) {
                log.warn("==========================================");
                log.warn("安全警告: 正在使用默认管理员密码 'admin123'！");
                log.warn("请在生产环境修改 app.auth.default-password");
                log.warn("==========================================");
            }
        }
    }

    @Override
    public LoginResultVO login(String username, String password) {
        AppUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPassword()) || !user.getEnabled()) {
            if (user != null && passwordEncoder.matches(password, user.getPassword()) && !user.getEnabled()) {
                log.warn("已禁用账号尝试登录: {}", username);
            }
            throw new BusinessException("用户名或密码错误");
        }
        log.info("用户登录成功: {}", user.getUsername());
        return LoginResultVO.of(user.getUsername(), user.getNickname());
    }

    @Override
    public void logout(HttpSession session) {
        String userId = (String) session.getAttribute(SessionKeys.LOGIN_USER_ID);
        session.invalidate();
        log.info("用户登出: {}", userId);
    }

    @Override
    public AuthStatusVO getStatus(HttpSession session) {
        String userId = (String) session.getAttribute(SessionKeys.LOGIN_USER_ID);
        if (userId == null) {
            return AuthStatusVO.unauthenticated();
        }
        String nickname = (String) session.getAttribute(SessionKeys.LOGIN_NICKNAME);
        return AuthStatusVO.authenticated(userId, nickname);
    }
}
