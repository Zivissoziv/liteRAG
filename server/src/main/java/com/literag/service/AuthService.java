package com.literag.service;

import com.literag.common.BizException;
import com.literag.dto.Requests;
import com.literag.dto.UserDto;
import com.literag.entity.AuthToken;
import com.literag.entity.SysUser;
import com.literag.repository.AuthTokenRepository;
import com.literag.repository.SysUserRepository;
import com.literag.security.AuthContext;
import com.literag.security.PasswordHasher;
import com.literag.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录、令牌与用户管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 令牌有效期 */
    private static final int TOKEN_DAYS = 7;
    /** 连续失败次数上限与锁定时长 */
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_SECONDS = 60;

    private final SysUserRepository userRepository;
    private final AuthTokenRepository tokenRepository;
    private final PasswordHasher hasher;

    private final SecureRandom random = new SecureRandom();
    /** 用户名到失败记录，仅用于缓解在线爆破，进程内即可 */
    private final Map<String, Failure> failures = new ConcurrentHashMap<>();

    private record Failure(int count, long lockedUntil) {
    }

    // ------------------------------------------------------------- 登录

    @Transactional
    public Map<String, Object> login(Requests.LoginReq req) {
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        String password = req.getPassword() == null ? "" : req.getPassword();
        if (username.isEmpty() || password.isEmpty()) {
            throw new BizException(400, "请输入账号和密码");
        }
        checkLocked(username);

        SysUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !hasher.matches(password, user.getPassword())) {
            recordFailure(username);
            throw new BizException(401, "账号或密码不正确");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new BizException(403, "账号已被停用，请联系管理员");
        }
        failures.remove(username);

        tokenRepository.deleteExpired(LocalDateTime.now());
        String token = newToken();
        AuthToken record = new AuthToken();
        record.setToken(token);
        record.setUserId(user.getId());
        record.setRole(user.getRole());
        record.setCreatedAt(LocalDateTime.now());
        record.setExpiresAt(LocalDateTime.now().plusDays(TOKEN_DAYS));
        tokenRepository.save(record);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        AuthContext.CurrentUser current = AuthContext.of(user, token);
        return Map.of(
                "token", token,
                "expiresAt", record.getExpiresAt(),
                "user", UserDto.from(user),
                "permissions", Roles.ordered(user.getRole())
        );
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            tokenRepository.deleteById(token);
        }
    }

    /** 校验令牌并返回当前用户；令牌过期、角色变更、账号停用都会立即失效 */
    @Transactional
    public AuthContext.CurrentUser resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        AuthToken record = tokenRepository.findById(token).orElse(null);
        if (record == null) {
            return null;
        }
        if (record.getExpiresAt() == null || record.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenRepository.deleteById(token);
            return null;
        }
        SysUser user = userRepository.findById(record.getUserId()).orElse(null);
        if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
            tokenRepository.deleteByUserId(record.getUserId());
            return null;
        }
        if (!user.getRole().equals(record.getRole())) {
            // 角色被调整，旧令牌必须重新登录，避免残留更高权限
            tokenRepository.deleteById(token);
            return null;
        }
        return AuthContext.of(user, token);
    }

    // ------------------------------------------------------------- 自助

    /** 修改自己的密码，成功后所有令牌失效（含当前），需要重新登录 */
    @Transactional
    public void changeOwnPassword(String currentPassword, String newPassword) {
        AuthContext.CurrentUser current = AuthContext.require();
        SysUser user = userRepository.findById(current.id())
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        if (!hasher.matches(currentPassword, user.getPassword())) {
            throw new BizException(400, "当前密码不正确");
        }
        validatePassword(newPassword);
        user.setPassword(hasher.hash(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
        tokenRepository.deleteByUserId(user.getId());
    }

    // ------------------------------------------------------------- 用户管理

    public List<UserDto> listUsers() {
        return userRepository.findAllByOrderByIdAsc().stream().map(UserDto::from).toList();
    }

    @Transactional
    public UserDto createUser(Requests.UserReq req) {
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        if (!username.matches("[A-Za-z0-9_.-]{3,32}")) {
            throw new BizException(400, "账号需为 3 到 32 位字母、数字、下划线、点或短横线");
        }
        if (userRepository.existsByUsername(username)) {
            throw new BizException(400, "账号已存在");
        }
        validatePassword(req.getPassword());
        String role = normalizeRole(req.getRole());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setDisplayName(blankToNull(req.getDisplayName()) == null ? username : req.getDisplayName().trim());
        user.setPassword(hasher.hash(req.getPassword()));
        user.setRole(role);
        user.setEnabled(req.getEnabled() == null || req.getEnabled());
        user.setMustChangePassword(req.getMustChangePassword() == null || req.getMustChangePassword());
        userRepository.save(user);
        log.info("新建用户 {} ({})", username, role);
        return UserDto.from(user);
    }

    @Transactional
    public UserDto updateUser(Long id, Requests.UserReq req) {
        SysUser user = requireUser(id);
        Long operatorId = AuthContext.userId();

        if (req.getDisplayName() != null) {
            user.setDisplayName(req.getDisplayName().trim());
        }
        if (req.getRole() != null) {
            String role = normalizeRole(req.getRole());
            if (!role.equals(user.getRole())) {
                guardLastAdmin(user, "不能移除最后一名启用状态的管理员");
                guardSelf(user, operatorId, "不能修改自己的角色");
                user.setRole(role);
                tokenRepository.deleteByUserId(user.getId());
            }
        }
        if (req.getEnabled() != null && !req.getEnabled().equals(user.getEnabled())) {
            if (!req.getEnabled()) {
                guardSelf(user, operatorId, "不能停用自己的账号");
                guardLastAdmin(user, "不能停用最后一名管理员");
            }
            user.setEnabled(req.getEnabled());
            if (!req.getEnabled()) {
                tokenRepository.deleteByUserId(user.getId());
            }
        }
        userRepository.save(user);
        return UserDto.from(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        validatePassword(newPassword);
        SysUser user = requireUser(id);
        user.setPassword(hasher.hash(newPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        tokenRepository.deleteByUserId(user.getId());
    }

    @Transactional
    public void deleteUser(Long id) {
        SysUser user = requireUser(id);
        guardSelf(user, AuthContext.userId(), "不能删除自己的账号");
        guardLastAdmin(user, "不能删除最后一名管理员");
        tokenRepository.deleteByUserId(id);
        userRepository.delete(user);
    }

    public SysUser requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new BizException(404, "用户不存在"));
    }

    /** 管理员下拉里可选的角色 */
    public List<Map<String, Object>> roles() {
        return Roles.describe();
    }

    // ------------------------------------------------------------- 内部

    private void guardSelf(SysUser target, Long operatorId, String message) {
        if (target.getId().equals(operatorId)) {
            throw new BizException(400, message);
        }
    }

    private void guardLastAdmin(SysUser target, String message) {
        if (SysUser.ROLE_ADMIN.equals(target.getRole())
                && Boolean.TRUE.equals(target.getEnabled())
                && userRepository.countByRoleAndEnabledTrue(SysUser.ROLE_ADMIN) <= 1) {
            throw new BizException(400, message);
        }
    }

    private String normalizeRole(String role) {
        String value = role == null ? "" : role.trim().toUpperCase();
        if (!Roles.isRole(value)) {
            throw new BizException(400, "角色只能是 ADMIN、EDITOR 或 VIEWER");
        }
        return value;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new BizException(400, "密码至少 6 位");
        }
        if (password.length() > 64) {
            throw new BizException(400, "密码不能超过 64 位");
        }
    }

    private void checkLocked(String username) {
        Failure f = failures.get(username);
        if (f != null && f.lockedUntil() > System.currentTimeMillis()) {
            long wait = Math.max(1, (f.lockedUntil() - System.currentTimeMillis()) / 1000);
            throw new BizException(429, "密码错误次数过多，请 " + wait + " 秒后再试");
        }
    }

    private void recordFailure(String username) {
        failures.compute(username, (k, old) -> {
            int count = old == null ? 1 : old.count() + 1;
            long lockedUntil = count >= MAX_FAILURES
                    ? System.currentTimeMillis() + LOCK_SECONDS * 1000
                    : 0;
            return new Failure(count, lockedUntil);
        });
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
