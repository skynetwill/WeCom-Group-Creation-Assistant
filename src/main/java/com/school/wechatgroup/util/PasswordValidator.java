package com.school.wechatgroup.util;

public final class PasswordValidator {

    private PasswordValidator() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度至少6个字符");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter) {
            throw new IllegalArgumentException("密码必须包含至少一个字母");
        }
        if (!hasDigit) {
            throw new IllegalArgumentException("密码必须包含至少一个数字");
        }
    }

    public static boolean isValid(String password) {
        try {
            validate(password);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
