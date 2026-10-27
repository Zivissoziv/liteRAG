package com.literag.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.literag.common.ApiResponse;
import com.literag.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 认证与鉴权入口。规则刻意保持直白，避免引入完整的 Spring Security：
 *
 * <pre>
 * /api/auth/login          公开
 * /api/users/**            需要 user:manage
 * GET  /api/**             需要 content:read（已登录即可）
 * 写  /api/settings、/api/models  需要 config:manage
 * 其余写操作                需要 content:write
 * </pre>
 *
 * 令牌优先取 Authorization: Bearer，其次取登录时下发的 HttpOnly Cookie，
 * 这样浏览器直接发起的下载与预览（a[download]、iframe）无需额外处理。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String COOKIE_NAME = "literag_token";

    private final AuthService authService;
    private final ObjectMapper mapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        if ("/api/auth/login".equals(path)) {
            return true;
        }

        AuthContext.CurrentUser user = authService.resolve(readToken(request));
        if (user == null) {
            deny(response, 401, "请先登录");
            return false;
        }
        AuthContext.set(user);

        String required = requiredPermission(path, request.getMethod());
        if (required != null && !user.has(required)) {
            deny(response, 403, "当前账号没有该操作权限");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    /** 返回 null 表示只要登录即可 */
    private String requiredPermission(String path, String method) {
        if (path.startsWith("/api/auth/")) {
            return null;
        }
        if (path.startsWith("/api/users")) {
            return Roles.PERM_USER;
        }
        if (path.startsWith("/api/kb/access-options")) {
            return Roles.PERM_WRITE;
        }
        boolean read = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        if (read) {
            return Roles.PERM_READ;
        }
        // 检索与问答用 POST 传参，但语义是只读，只读账号也应当可用
        if (path.startsWith("/api/search") || path.startsWith("/api/chat")) {
            return Roles.PERM_READ;
        }
        if (path.startsWith("/api/settings") || path.startsWith("/api/models")) {
            return Roles.PERM_CONFIG;
        }
        return Roles.PERM_WRITE;
    }

    private String readToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        String direct = request.getHeader("X-Auth-Token");
        if (direct != null && !direct.isBlank()) {
            return direct.trim();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null
                        && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void deny(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(mapper.writeValueAsString(ApiResponse.fail(status, message)));
    }
}
