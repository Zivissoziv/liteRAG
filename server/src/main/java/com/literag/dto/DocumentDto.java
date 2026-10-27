package com.literag.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentDto {
    private Long id;
    private Long kbId;
    private String kbName;
    private Long folderId;
    /** 目录完整路径，根目录文档为空 */
    private String folderPath;
    private String name;
    private String title;
    private String sourceType;
    private String fileExt;
    private Long fileSize;
    private String category;
    private String tags;
    private String status;
    private String chunkStrategy;
    private String errorMsg;
    private Integer charCount;
    private Integer chunkCount;
    private Integer ver;
    private String createdBy;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
