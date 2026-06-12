package com.school.wechatgroup.demo.java;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Stream API — 面试必写白板题
 *
 * 本项目真实使用场景:
 *   WeChatGroupServiceImpl: 文件解析后去重排序
 *   GlobalExceptionHandler: 收集校验错误
 *   SecurityExpressionOperations: 权限流式匹配
 *
 * 10 个最常用操作:
 */
public class StreamApiDemo {

    record User(String name, String role, int loginCount) {}

    public static void main(String[] args) {
        List<User> users = Arrays.asList(
                new User("张三", "USER", 5),
                new User("李四", "ADMIN", 20),
                new User("王五", "USER", 3),
                new User("赵六", "USER", 8),
                new User("admin", "ADMIN", 30)
        );

        // 1. filter — 筛选
        List<User> admins = users.stream()
                .filter(u -> "ADMIN".equals(u.role()))
                .toList();
        System.out.println("1. 管理员: " + admins);

        // 2. map — 转换
        List<String> names = users.stream()
                .map(User::name)
                .toList();
        System.out.println("2. 用户名: " + names);

        // 3. sorted — 排序
        List<User> byLogin = users.stream()
                .sorted(Comparator.comparingInt(User::loginCount).reversed())
                .toList();
        System.out.println("3. 登录排行: " + byLogin);

        // 4. collect + groupingBy — 分组
        Map<String, List<User>> byRole = users.stream()
                .collect(Collectors.groupingBy(User::role));
        System.out.println("4. 按角色分组: " + byRole);

        // 5. mapToInt + sum — 聚合
        int totalLogins = users.stream()
                .mapToInt(User::loginCount).sum();
        System.out.println("5. 总登录次数: " + totalLogins);

        // 6. findFirst — 找第一个
        users.stream()
                .filter(u -> u.loginCount() > 10)
                .findFirst()
                .ifPresent(u -> System.out.println("6. 首个活跃用户: " + u));

        // 7. anyMatch — 是否存在
        boolean hasAdmin = users.stream()
                .anyMatch(u -> "ADMIN".equals(u.role()));
        System.out.println("7. 有管理员吗: " + hasAdmin);

        // 8. distinct — 去重
        long uniqueRoles = users.stream()
                .map(User::role).distinct().count();
        System.out.println("8. 角色种类: " + uniqueRoles);

        // 9. flatMap — 扁平化
        List<String> allChars = users.stream()
                .map(User::name)
                .flatMap(name -> Arrays.stream(name.split("")))
                .distinct()
                .toList();
        System.out.println("9. 名字里的字: " + allChars);

        // 10. reduce — 归约
        Optional<User> mostActive = users.stream()
                .reduce((a, b) -> a.loginCount() > b.loginCount() ? a : b);
        mostActive.ifPresent(u -> System.out.println("10. 最活跃: " + u));
    }
}
