package com.literag.controller;

import com.literag.common.ApiResponse;
import com.literag.dto.Requests;
import com.literag.dto.UserDto;
import com.literag.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户与角色管理，仅 ADMIN 可访问（由 AuthInterceptor 统一拦截 /api/users/**）。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping
    public ApiResponse<List<UserDto>> list() {
        return ApiResponse.ok(authService.listUsers());
    }

    @GetMapping("/roles")
    public ApiResponse<List<Map<String, Object>>> roles() {
        return ApiResponse.ok(authService.roles());
    }

    @PostMapping
    public ApiResponse<UserDto> create(@RequestBody Requests.UserReq req) {
        return ApiResponse.ok(authService.createUser(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserDto> update(@PathVariable Long id, @RequestBody Requests.UserReq req) {
        return ApiResponse.ok(authService.updateUser(id, req));
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody Requests.ResetPasswordReq req) {
        authService.resetPassword(id, req.getNewPassword());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        authService.deleteUser(id);
        return ApiResponse.ok();
    }
}
