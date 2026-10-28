package com.literag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "model_config")
public class ModelConfig {

    public static final String TYPE_EMBEDDING = "EMBEDDING";
    public static final String TYPE_CHAT = "CHAT";

    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_OLLAMA = "ollama";
    public static final String PROVIDER_LOCAL = "local";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 展示名称 */
    @Column(nullable = false, length = 120)
    private String name;

    /** EMBEDDING | CHAT */
    @Column(length = 16)
    private String type = TYPE_EMBEDDING;

    /** openai（含所有 OpenAI 兼容网关）| ollama | local */
    @Column(length = 24)
    private String provider = PROVIDER_OPENAI;

    @Column(length = 500)
    private String baseUrl;

    @Column(length = 500)
    private String apiKey;

    /** 模型名，如 text-embedding-3-small / bge-m3 / qwen3-embedding */
    @Column(length = 200)
    private String modelName;

    /** 向量维度，0 表示自动探测 */
    private Integer dimension = 0;

    private Integer batchSize = 16;

    private Integer timeout = 60;

    /** 额外参数 JSON，例如 {"encoding_format":"float"} */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String extraParams;

    private Boolean enabled = true;

    private Boolean isDefault = false;

    @Column(length = 300)
    private String remark;

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
