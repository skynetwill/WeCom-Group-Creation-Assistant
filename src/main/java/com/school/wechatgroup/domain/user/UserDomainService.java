package com.school.wechatgroup.domain.user;

import com.school.wechatgroup.config.AuthProperties;
import com.school.wechatgroup.entity.AppUser;
import com.school.wechatgroup.repository.AppUserRepository;
import org.springframework.stereotype.Service;

/**
 * DDD 领域服务 — 用户认证逻辑
 *
 * 与 Application Service 的区别：
 *   Domain Service: 纯业务逻辑，不依赖基础设施
 *   Application Service: 编排流程，调用 Domain Service + 发送事件/消息
 */
@Service
public class UserDomainService {

    private final AppUserRepository userRepository;
    private final AuthProperties authProperties;

    public UserDomainService(AppUserRepository userRepository, AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.authProperties = authProperties;
    }

    /** 加载聚合根 */
    public UserAggregate loadUser(String username) {
        return userRepository.findByUsername(username)
                .map(UserAggregate::new)
                .orElse(null);
    }

    /** 保存聚合根 */
    public void save(UserAggregate aggregate) {
        userRepository.save(aggregate.getEntity());
    }
}
