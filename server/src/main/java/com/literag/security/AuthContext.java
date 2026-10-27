package com.literag.security;

import com.literag.entity.SysUser;

/**
 * 当前请求的登录用户，由 {@link AuthInterceptor} 写入并在请求结束时清理。
 */
public final class AuthContext {

    public record CurrentUser(Long id, String username, String displayName, String role, String token) {

        public boolean has(String permission) {
            return Roles.has(role, permission);
        }

        public String roleLabel() {
            return Roles.label(role);
        }
    }

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("当前请求没有登录用户");
        }
        return user;
    }

    public static Long userId() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.id();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static CurrentUser of(SysUser user, String token) {
        return new CurrentUser(user.getId(), user.getUsername(),
                user.getDisplayName() == null || user.getDisplayName().isBlank()
                        ? user.getUsername() : user.getDisplayName(),
                user.getRole(), token);
    }
}
