package com.literag.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class KnowledgeBaseDto {
    private Long id;
    private String name;
    private String description;
    private Long embeddingModelId;
    private String embeddingModelName;
    private String collectionName;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer docCount;
    private Integer chunkCount;
    private Boolean mcpExposed;
    private String color;
    private String vectorBackend;
    /** PUBLIC | RESTRICTED */
    private String visibility;
    /** 受限时的授权角色与用户，便于编辑弹窗回填 */
    private List<String> accessRoles;
    private List<Long> accessUserIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
