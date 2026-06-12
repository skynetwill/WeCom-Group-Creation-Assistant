package com.school.wechatgroup.util;

public final class PasswordValidator {

    private PasswordValidator() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("密码长度至少8个字符");
        }
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        if (!hasUpper) throw new IllegalArgumentException("密码必须包含至少一个大写字母");
        if (!hasLower) throw new IllegalArgumentException("密码必须包含至少一个小写字母");
        if (!hasDigit) throw new IllegalArgumentException("密码必须包含至少一个数字");
        if (!hasSpecial) throw new IllegalArgumentException("密码必须包含至少一个特殊字符");
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
