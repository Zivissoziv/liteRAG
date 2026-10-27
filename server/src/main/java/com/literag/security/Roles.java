package com.literag.security;

import com.literag.entity.SysUser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色与权限点。刻意保持成「少量固定角色」而不是可配置的权限表：
 * 系统面向小团队，三档角色足以覆盖，且不需要额外的权限管理界面。
 */
public final class Roles {

    /** 只读：浏览知识库、文档、检索、统计 */
    public static final String PERM_READ = "content:read";
    /** 写入：建库、上传、编辑、移动、删除文档与目录、重建索引 */
    public static final String PERM_WRITE = "content:write";
    /** 系统配置：模型、向量库、MCP 密钥、系统参数 */
    public static final String PERM_CONFIG = "config:manage";
    /** 用户与角色管理 */
    public static final String PERM_USER = "user:manage";

    private static final Map<String, Set<String>> MATRIX = new LinkedHashMap<>();

    /** 展示顺序：浏览 → 编辑 → 配置 → 用户管理 */
    private static final List<String> ORDER = List.of(PERM_READ, PERM_WRITE, PERM_CONFIG, PERM_USER);

    static {
        MATRIX.put(SysUser.ROLE_ADMIN, Set.of(PERM_READ, PERM_WRITE, PERM_CONFIG, PERM_USER));
        MATRIX.put(SysUser.ROLE_EDITOR, Set.of(PERM_READ, PERM_WRITE));
        MATRIX.put(SysUser.ROLE_VIEWER, Set.of(PERM_READ));
    }

    private Roles() {
    }

    public static boolean isRole(String role) {
        return role != null && MATRIX.containsKey(role);
    }

    public static Set<String> permissions(String role) {
        return MATRIX.getOrDefault(role, Set.of());
    }

    public static boolean has(String role, String permission) {
        return permissions(role).contains(permission);
    }

    /** 按固定的业务顺序返回权限点，避免字母序造成「浏览」排在最后 */
    public static List<String> ordered(String role) {
        return ORDER.stream().filter(permissions(role)::contains).toList();
    }

    /** 前端直接消费的角色清单 */
    public static List<Map<String, Object>> describe() {
        return MATRIX.entrySet().stream().map(e -> Map.<String, Object>of(
                "role", e.getKey(),
                "label", label(e.getKey()),
                "permissions", ordered(e.getKey())
        )).toList();
    }

    public static String label(String role) {
        return switch (role == null ? "" : role) {
            case SysUser.ROLE_ADMIN -> "管理员";
            case SysUser.ROLE_EDITOR -> "编辑者";
            case SysUser.ROLE_VIEWER -> "只读";
            default -> role;
        };
    }
}
