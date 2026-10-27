package com.literag.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 受限知识库的授权记录。一条记录代表「某个角色」或「某个用户」可以查看该知识库。
 * 用字符串同时承载角色名与用户 id，避免为两种主体建两张表。
 */
@Data
@Entity
@Table(name = "kb_access", uniqueConstraints = @UniqueConstraint(
        name = "uk_kb_access", columnNames = {"kbId", "subjectType", "subjectValue"}),
        indexes = @Index(name = "idx_kb_access_kb", columnList = "kbId"))
public class KbAccess {

    public static final String SUBJECT_ROLE = "ROLE";
    public static final String SUBJECT_USER = "USER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long kbId;

    /** ROLE | USER */
    @Column(nullable = false, length = 8)
    private String subjectType;

    /** 角色名（ADMIN / EDITOR / VIEWER）或用户 id */
    @Column(nullable = false, length = 32)
    private String subjectValue;
}
