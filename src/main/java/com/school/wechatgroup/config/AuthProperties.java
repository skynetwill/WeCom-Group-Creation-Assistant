package com.school.wechatgroup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private boolean enabled = false;
    private String defaultUsername = "admin";
    private String defaultPassword = "admin123";
    private int lockThreshold = 5;
    private int lockDurationMinutes = 15;
    private int maxSessions = 5;
    private int rememberMeValidityDays = 14;
    private int passwordExpiryDays = 90;
    private int passwordHistorySize = 5;
    private boolean mfaEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultUsername() {
        return defaultUsername;
    }

    public void setDefaultUsername(String defaultUsername) {
        this.defaultUsername = defaultUsername;
    }

    public String getDefaultPassword() {
        return defaultPassword;
    }

    public void setDefaultPassword(String defaultPassword) {
        this.defaultPassword = defaultPassword;
    }

    public int getLockThreshold() {
        return lockThreshold;
    }

    public void setLockThreshold(int lockThreshold) {
        this.lockThreshold = lockThreshold;
    }

    public int getLockDurationMinutes() {
        return lockDurationMinutes;
    }

    public void setLockDurationMinutes(int lockDurationMinutes) {
        this.lockDurationMinutes = lockDurationMinutes;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(int maxSessions) {
        this.maxSessions = maxSessions;
    }

    public int getRememberMeValidityDays() {
        return rememberMeValidityDays;
    }

    public void setRememberMeValidityDays(int rememberMeValidityDays) {
        this.rememberMeValidityDays = rememberMeValidityDays;
    }

    public int getPasswordExpiryDays() {
        return passwordExpiryDays;
    }

    public void setPasswordExpiryDays(int passwordExpiryDays) {
        this.passwordExpiryDays = passwordExpiryDays;
    }

    public int getPasswordHistorySize() {
        return passwordHistorySize;
    }

    public void setPasswordHistorySize(int passwordHistorySize) {
        this.passwordHistorySize = passwordHistorySize;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }
}
