package com.literag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "doc", indexes = {
        @Index(name = "idx_doc_kb", columnList = "kbId"),
        @Index(name = "idx_doc_folder", columnList = "folderId"),
        @Index(name = "idx_doc_status", columnList = "status")
})
public class Document {

    public static final String TYPE_UPLOAD = "UPLOAD";
    public static final String TYPE_WIKI = "WIKI";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PARSING = "PARSING";
    public static final String STATUS_EMBEDDING = "EMBEDDING";
    public static final String STATUS_READY = "READY";

    public static final String STRATEGY_AUTO = "AUTO";
    public static final String STRATEGY_FIXED = "FIXED";
    public static final String STRATEGY_HEADING = "HEADING";
    public static final String STRATEGY_TABLE = "TABLE";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long kbId;

    /** 所属目录，为空表示根目录（未分类） */
    private Long folderId;

    /** 原始文件名 / wiki 页面标识 */
    @Column(length = 300)
    private String name;

    @Column(length = 300)
    private String title;

    /** UPLOAD | WIKI */
    @Column(length = 16)
    private String sourceType = TYPE_UPLOAD;

    @Column(length = 16)
    private String fileExt;

    @Column(length = 600)
    private String filePath;

    private Long fileSize = 0L;

    @Column(length = 80)
    private String category;

    @Column(length = 300)
    private String tags;

    @Column(length = 20)
    private String status = STATUS_PENDING;

    /** 分块策略：AUTO | FIXED | HEADING | TABLE */
    @Column(length = 16)
    private String chunkStrategy = STRATEGY_AUTO;

    @Column(length = 2000)
    private String errorMsg;

    private Integer charCount = 0;

    private Integer chunkCount = 0;

    /** 解析后的纯文本；wiki 页面即 markdown 原文 */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String content;

    @Column(length = 64)
    private String createdBy = "admin";

    /** 文档版本号，内容编辑时递增 */
    private Integer ver = 1;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
