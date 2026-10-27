package com.literag.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户。角色只有三档，权限点由 {@link com.literag.security.Roles} 按角色展开。
 */
@Data
@Entity
@Table(name = "sys_user", indexes = {
        @Index(name = "idx_user_name", columnList = "username", unique = true)
})
public class SysUser {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_EDITOR = "EDITOR";
    public static final String ROLE_VIEWER = "VIEWER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64, nullable = false, unique = true)
    private String username;

    /** PBKDF2 哈希，永不出现在任何接口响应中 */
    @JsonIgnore
    @Column(length = 200, nullable = false)
    private String password;

    @Column(length = 80)
    private String displayName;

    /** ADMIN | EDITOR | VIEWER */
    @Column(length = 16, nullable = false)
    private String role = ROLE_VIEWER;

    private Boolean enabled = true;

    /** 首次登录后是否必须修改密码 */
    private Boolean mustChangePassword = false;

    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }
}
