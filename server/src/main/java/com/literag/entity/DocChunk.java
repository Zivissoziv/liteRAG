package com.literag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "doc_chunk", indexes = {
        @Index(name = "idx_chunk_kb", columnList = "kbId"),
        @Index(name = "idx_chunk_doc", columnList = "docId"),
        @Index(name = "uk_chunk_vec", columnList = "vectorId", unique = true)
})
public class DocChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 作为向量库中的主键 */
    @Column(length = 64)
    private String vectorId;

    private Long kbId;

    private Long docId;

    @Column(length = 300)
    private String docTitle;

    private Integer seq;

    /** 片段正文。用 LONGVARCHAR 而不是 @Lob，避免 H2 / MySQL / SQLite 之间 Clob 句柄差异 */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String content;

    private Integer charCount;

    /** 本地向量模式下保存原始向量（Qdrant 模式下同样落库，便于切换后端后免重新解析） */
    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    private byte[] embedding;

    private Integer dim;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
