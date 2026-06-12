package com.school.wechatgroup.security;

import com.school.wechatgroup.security.impl.SecurityContextImpl;

public final class SecurityContextHolder {

    private static final ThreadLocal<SecurityContext> CONTEXT_HOLDER = new ThreadLocal<>();

    private SecurityContextHolder() {
    }

    public static SecurityContext getContext() {
        SecurityContext ctx = CONTEXT_HOLDER.get();
        if (ctx == null) {
            ctx = new SecurityContextImpl();
            CONTEXT_HOLDER.set(ctx);
        }
        return ctx;
    }

    public static void setContext(SecurityContext context) {
        CONTEXT_HOLDER.set(context);
    }

    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }
}
