package com.literag.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录令牌。存数据库而不是签名 token，好处是登出、改密、禁用账号可以立刻失效。
 */
@Data
@Entity
@Table(name = "auth_token", indexes = {
        @Index(name = "idx_token_user", columnList = "userId")
})
public class AuthToken {

    /** 随机生成的令牌串，直接作为主键 */
    @Id
    @Column(length = 64)
    private String token;

    @Column(nullable = false)
    private Long userId;

    /** 生成令牌时用户所属角色，用于判定令牌是否因降权而失效 */
    @Column(length = 16)
    private String role;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;
}
