package com.literag.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "kb")
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    /** 该知识库使用的 embedding 模型 */
    private Long embeddingModelId;

    /** 向量库中的 collection 名称 */
    @Column(length = 120)
    private String collectionName;

    private Integer chunkSize = 800;

    private Integer chunkOverlap = 120;

    private Integer docCount = 0;

    private Integer chunkCount = 0;

    /** 是否通过 MCP 对外暴露 */
    private Boolean mcpExposed = true;

    /** 可见性：PUBLIC 所有登录用户可见；RESTRICTED 仅 kb_access 中授权的主体可见 */
    @Column(length = 16)
    private String visibility = "PUBLIC";

    @Column(length = 32)
    private String color = "#4f6ef7";

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
