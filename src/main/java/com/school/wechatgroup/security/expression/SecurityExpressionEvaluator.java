package com.school.wechatgroup.security.expression;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.Map;

/**
 * SpEL 简化表达式求值器（对标 Spring Security MethodSecurityExpressionHandler）
 * 使用 JavaScript 引擎模拟 SpEL 表达式求值，支持 hasRole/hasAuthority/isAuthenticated 等
 */
public class SecurityExpressionEvaluator {

    private static final ScriptEngine engine;

    static {
        ScriptEngineManager manager = new ScriptEngineManager();
        engine = manager.getEngineByName("JavaScript");
    }

    /**
     * 求值 SpEL 表达式
     * @param expression 如: "hasRole('ADMIN')" 或 "isAuthenticated()"
     * @param ops 安全表达式操作对象
     * @param paramMap 方法参数映射（#paramName → value）
     * @return 表达式结果
     */
    public static boolean evaluate(String expression, SecurityExpressionOperations ops,
                                    Map<String, Object> paramMap) {
        if (engine == null) {
            // 无 JS 引擎时做简单手动解析
            return evaluateSimple(expression, ops);
        }

        try {
            // 注入变量到引擎
            engine.eval("var hasRole = function(r) { return hasRol(r); };");
            engine.eval("var hasAuthority = function(a) { return hasAut(a); };");

            // 转化为简单的函数调用
            String evalExpr = expression
                    .replace("hasRole(", "hasRol(\"")
                    .replace(")", "\")")
                    .replace("hasAuthority(", "hasAut(\"")
                    .replace("isAuthenticated()", ops.isAuthenticated() ? "true" : "false")
                    .replace("isAnonymous()", ops.isAnonymous() ? "true" : "false")
                    .replace("permitAll", "true")
                    .replace("denyAll", "false");

            // 对于方法参数引用 #paramName，从 paramMap 中取值
            if (paramMap != null) {
                for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
                    evalExpr = evalExpr.replace("#" + entry.getKey(),
                            "'" + String.valueOf(entry.getValue()) + "'");
                }
            }

            Object result = engine.eval(evalExpr);
            return Boolean.TRUE.equals(result);

        } catch (ScriptException e) {
            // JS 引擎失败，回退到简单解析
            return evaluateSimple(expression, ops);
        }
    }

    /**
     * 简单手动解析（不依赖 JS 引擎）
     */
    private static boolean evaluateSimple(String expression, SecurityExpressionOperations ops) {
        // hasRole('ADMIN')
        if (expression.startsWith("hasRole(")) {
            String role = extractArg(expression, "hasRole");
            return ops.hasRole(role);
        }
        // hasAuthority('ROLE_ADMIN')
        if (expression.startsWith("hasAuthority(")) {
            String auth = extractArg(expression, "hasAuthority");
            return ops.hasAuthority(auth);
        }
        // hasAnyRole('ADMIN', 'USER')
        if (expression.startsWith("hasAnyRole(")) {
            String[] roles = extractArgs(expression, "hasAnyRole");
            return ops.hasAnyRole(roles);
        }
        // isAuthenticated()
        if ("isAuthenticated()".equals(expression)) {
            return ops.isAuthenticated();
        }
        // isAnonymous()
        if ("isAnonymous()".equals(expression)) {
            return ops.isAnonymous();
        }
        // permitAll
        if ("permitAll".equals(expression)) {
            return true;
        }
        // denyAll
        if ("denyAll".equals(expression)) {
            return false;
        }
        // isCurrentUser('username')
        if (expression.startsWith("isCurrentUser(")) {
            return ops.isCurrentUser(extractArg(expression, "isCurrentUser"));
        }

        return false;
    }

    private static String extractArg(String expr, String funcName) {
        int start = funcName.length() + 1; // skip "funcName("
        int end = expr.lastIndexOf(')');
        String arg = expr.substring(start, end);
        // 去除引号
        return arg.replaceAll("^['\"]|['\"]$", "");
    }

    private static String[] extractArgs(String expr, String funcName) {
        int start = funcName.length() + 1;
        int end = expr.lastIndexOf(')');
        String args = expr.substring(start, end);
        return java.util.Arrays.stream(args.split(","))
                .map(s -> s.trim().replaceAll("^['\"]|['\"]$", ""))
                .toArray(String[]::new);
    }
}
