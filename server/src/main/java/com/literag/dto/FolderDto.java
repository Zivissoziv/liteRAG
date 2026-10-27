package com.literag.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class FolderDto {
    private Long id;
    private Long kbId;
    private Long parentId;
    private String name;
    /** 完整路径，例如「手册 / 接口」 */
    private String path;
    private Integer depth;
    /** 直接归属该目录的文档数 */
    private Integer docCount;
    /** 含子目录的文档总数，便于侧栏展示 */
    private Integer totalCount;
    private LocalDateTime createdAt;

    @Builder.Default
    private List<FolderDto> children = new ArrayList<>();
}
