package com.school.wechatgroup.domain.user;

import com.school.wechatgroup.entity.AppUser;
import com.school.wechatgroup.shared.AggregateRoot;

/**
 * DDD 聚合根 — 用户
 *
 * 聚合：一组高内聚的对象，通过聚合根统一访问
 * 规则：外部不能直接修改 Password/LoginHistory，必须通过 User 聚合根
 */
public class UserAggregate implements AggregateRoot<Long> {

    private final AppUser user;

    public UserAggregate(AppUser user) {
        this.user = user;
    }

    /** 领域行为：登录成功 */
    public void recordLogin(String ip) {
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginTime(java.time.LocalDateTime.now());
        user.setLastLoginIp(ip);
        user.setLoginCount(user.getLoginCount() + 1);
    }

    /** 领域行为：登录失败 */
    public void recordLoginFailure(int lockThreshold) {
        user.setFailedAttempts(user.getFailedAttempts() + 1);
        if (user.getFailedAttempts() >= lockThreshold) {
            user.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(15));
        }
    }

    /** 领域行为：修改密码 */
    public void changePassword(String newEncodedPassword) {
        user.setPassword(newEncodedPassword);
        user.setMustChangePassword(false);
        user.setPasswordLastModified(java.time.LocalDateTime.now());
    }

    /** 领域行为：锁定检查 */
    public boolean isLocked() {
        return user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(java.time.LocalDateTime.now());
    }

    /** 领域行为：是否被禁用 */
    public boolean isDisabled() {
        return !Boolean.TRUE.equals(user.getEnabled());
    }

    @Override
    public Long getId() {
        return user.getId();
    }

    public AppUser getEntity() { return user; }
    public String getUsername() { return user.getUsername(); }
    public String getRole() { return user.getRole(); }
    public String getNickname() { return user.getNickname(); }
    public boolean isMustChangePassword() { return Boolean.TRUE.equals(user.getMustChangePassword()); }
}
