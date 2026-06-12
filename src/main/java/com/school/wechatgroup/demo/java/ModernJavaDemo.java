package com.school.wechatgroup.demo.java;

import java.util.Optional;

/**
 * 现代 Java 特性 (Java 8~17) — 面试高频
 *
 * 本项目实际使用:
 *   Record → 无，Java 17 新特性
 *   Optional → AppUserRepository.findByUsername()
 *   Lambda → 大量使用（.map(), .filter(), .forEach()）
 *   var → LoginInterceptor、AuthController
 *   switch 表达式 → 无
 *   sealed class → 无
 *   text block → WeChatGroupServiceImpl(多行 SQL/JSON)
 */

// Java 14+ Record — 不可变数据载体
// 自动生成: 构造器、getter、equals、hashCode、toString
record LoginEvent(String username, String ip, boolean success) {
    // 紧凑构造器 — 自定义校验
    LoginEvent {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
    }
}

// Java 17 Sealed Class — 限制子类
sealed interface AuthResult permits AuthSuccess, AuthFailure {}
record AuthSuccess(String token) implements AuthResult {}
record AuthFailure(String reason) implements AuthResult {}

public class ModernJavaDemo {

    public static void main(String[] args) {
        // 1. Record 使用
        var event = new LoginEvent("admin", "127.0.0.1", true);
        System.out.println("Record: " + event);

        // 2. var 类型推断
        var loginCount = 42;  // 编译时推断为 int
        System.out.println("var: " + loginCount);

        // 3. Optional 链式调用
        Optional<String> maybeName = Optional.of("admin");
        String display = maybeName
                .filter(n -> n.length() > 2)
                .map(String::toUpperCase)
                .orElse("匿名用户");
        System.out.println("Optional链: " + display);

        // 4. Switch 表达式
        String role = "ADMIN";
        String desc = switch (role) {
            case "ADMIN" -> "系统管理员";
            case "USER" -> "普通用户";
            default -> "未知角色";
        };
        System.out.println("Switch表达式: " + desc);

        // 5. Sealed class + instanceof 模式匹配 (Java 16+)
        AuthResult result = new AuthSuccess("token_abc");
        String msg;
        if (result instanceof AuthSuccess s) {
            msg = "登录成功: " + s.token();
        } else if (result instanceof AuthFailure f) {
            msg = "登录失败: " + f.reason();
        } else {
            msg = "未知";
        }
        System.out.println("Sealed+Switch: " + msg);

        // 6. Text Block — 多行字符串
        String json = """
                {
                    "username": "admin",
                    "role": "ADMIN",
                    "loginCount": 42
                }
                """;
        System.out.println("TextBlock: " + json.strip());
    }
}
