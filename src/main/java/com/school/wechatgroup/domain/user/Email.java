package com.school.wechatgroup.domain.user;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * DDD 值对象 — Email
 *
 * 值对象 vs 实体：
 *   实体：有唯一标识（ID），可变（User）
 *   值对象：无 ID，不可变，通过属性值判断相等（Email, Money, Address）
 */
public final class Email {

    private static final Pattern PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final String value;

    public Email(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("无效的邮箱格式: " + value);
        }
        this.value = value;
    }

    public String getValue() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Email email)) return false;
        return value.equals(email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
