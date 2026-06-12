package com.school.wechatgroup.config;

import com.school.wechatgroup.exception.AccessDeniedException;
import com.school.wechatgroup.security.annotation.PostAuthorize;
import com.school.wechatgroup.security.annotation.PostFilter;
import com.school.wechatgroup.security.annotation.PreAuthorize;
import com.school.wechatgroup.security.annotation.PreFilter;
import com.school.wechatgroup.security.expression.SecurityExpressionEvaluator;
import com.school.wechatgroup.security.expression.SecurityExpressionOperations;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * 方法级安全 AOP 切面（对标 Spring Security MethodSecurityInterceptor）
 *
 * 支持:
 * - @PreAuthorize("hasRole('ADMIN')")        — 方法执行前校验
 * - @PostAuthorize("returnObject.owner == authentication.name") — 方法执行后校验
 * - @PreFilter("filterObject.owner == authentication.name")    — 参数过滤
 * - @PostFilter("filterObject.visible == true")                — 返回值过滤
 */
@Aspect
@Component
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class MethodSecurityAspect {

    private static final Logger log = LoggerFactory.getLogger(MethodSecurityAspect.class);

    @Around("@annotation(preAuth)")
    public Object preAuthorize(ProceedingJoinPoint joinPoint, PreAuthorize preAuth) throws Throwable {
        String expression = preAuth.value();
        Map<String, Object> paramMap = extractParameters(joinPoint);
        SecurityExpressionOperations ops = SecurityExpressionOperations.create(null, null);

        boolean granted = SecurityExpressionEvaluator.evaluate(expression, ops, paramMap);
        if (!granted) {
            log.warn("@PreAuthorize 校验失败: {} on {}", expression,
                    joinPoint.getSignature().toShortString());
            throw new AccessDeniedException("权限不足");
        }

        return joinPoint.proceed();
    }

    @Around("@annotation(postAuth)")
    public Object postAuthorize(ProceedingJoinPoint joinPoint, PostAuthorize postAuth) throws Throwable {
        Object result = joinPoint.proceed();

        String expression = postAuth.value();
        SecurityExpressionOperations ops = SecurityExpressionOperations.create(null, result);

        boolean granted = SecurityExpressionEvaluator.evaluate(expression, ops, null);
        if (!granted) {
            log.warn("@PostAuthorize 校验失败: {} on {}",
                    expression, joinPoint.getSignature().toShortString());
            throw new AccessDeniedException("权限不足");
        }

        return result;
    }

    @Around("@annotation(preFilter)")
    public Object preFilter(ProceedingJoinPoint joinPoint, PreFilter preFilter) throws Throwable {
        String expression = preFilter.value();
        Object[] args = joinPoint.getArgs();
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Parameter[] params = sig.getMethod().getParameters();

        // 对 Collection 参数进行过滤
        String targetParam = preFilter.filterTarget();
        for (int i = 0; i < params.length; i++) {
            String paramName = targetParam.isEmpty() ? params[i].getName() : targetParam;
            if (args[i] instanceof Collection) {
                Collection<?> col = (Collection<?>) args[i];
                List<Object> filtered = new ArrayList<>();
                for (Object item : col) {
                    SecurityExpressionOperations ops = SecurityExpressionOperations.create(item, null);
                    if (SecurityExpressionEvaluator.evaluate(expression, ops, null)) {
                        filtered.add(item);
                    }
                }
                args[i] = filtered;
            }
        }

        return joinPoint.proceed(args);
    }

    @Around("@annotation(postFilter)")
    public Object postFilter(ProceedingJoinPoint joinPoint, PostFilter postFilter) throws Throwable {
        Object result = joinPoint.proceed();

        if (result instanceof Collection) {
            String expression = postFilter.value();
            Collection<?> col = (Collection<?>) result;
            List<Object> filtered = new ArrayList<>();
            for (Object item : col) {
                SecurityExpressionOperations ops = SecurityExpressionOperations.create(item, null);
                if (SecurityExpressionEvaluator.evaluate(expression, ops, null)) {
                    filtered.add(item);
                }
            }
            return filtered;
        }

        return result;
    }

    private Map<String, Object> extractParameters(ProceedingJoinPoint joinPoint) {
        Map<String, Object> paramMap = new HashMap<>();
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();
        Parameter[] params = sig.getMethod().getParameters();
        for (int i = 0; i < params.length; i++) {
            paramMap.put(params[i].getName(), args[i]);
        }
        return paramMap;
    }
}
