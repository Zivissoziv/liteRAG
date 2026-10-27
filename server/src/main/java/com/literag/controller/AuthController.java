package com.literag.controller;

import com.literag.common.ApiResponse;
import com.literag.dto.Requests;
import com.literag.dto.UserDto;
import com.literag.security.AuthContext;
import com.literag.security.AuthInterceptor;
import com.literag.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Requests.LoginReq req, HttpServletResponse response) {
        Map<String, Object> result = authService.login(req);
        // 同时下发 HttpOnly Cookie：浏览器直接发起的下载 / 预览无需手动带 token
        ResponseCookie cookie = ResponseCookie.from(AuthInterceptor.COOKIE_NAME, String.valueOf(result.get("token")))
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofDays(7))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ApiResponse.ok(result);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@CookieValue(value = AuthInterceptor.COOKIE_NAME, required = false) String cookieToken,
                                    @RequestHeader(value = "Authorization", required = false) String authorization,
                                    HttpServletResponse response) {
        String token = cookieToken;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7).trim();
        }
        authService.logout(token);
        ResponseCookie expired = ResponseCookie.from(AuthInterceptor.COOKIE_NAME, "")
                .httpOnly(true).path("/").sameSite("Lax").maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        AuthContext.CurrentUser user = AuthContext.require();
        UserDto dto = UserDto.from(authService.requireUser(user.id()));
        return ApiResponse.ok(Map.of(
                "user", dto,
                "permissions", dto.getPermissions()
        ));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody Requests.PasswordReq req) {
        authService.changeOwnPassword(req.getCurrentPassword(), req.getNewPassword());
        return ApiResponse.ok();
    }
}
