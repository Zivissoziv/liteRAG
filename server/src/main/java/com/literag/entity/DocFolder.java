package com.literag.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库内的目录节点，支持多级嵌套；文档通过 folderId 归属到某个目录，为空表示根目录。
 */
@Data
@Entity
@Table(name = "doc_folder", indexes = {
        @Index(name = "idx_folder_kb", columnList = "kbId"),
        @Index(name = "idx_folder_parent", columnList = "parentId")
})
public class DocFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long kbId;

    /** 上级目录，为空表示根目录下的一级目录 */
    private Long parentId;

    @Column(length = 120)
    private String name;

    private Integer sortOrder = 0;

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
