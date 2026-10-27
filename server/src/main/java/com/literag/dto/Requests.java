package com.literag.dto;

import lombok.Data;

import java.util.List;

public class Requests {

    @Data
    public static class KbReq {
        private String name;
        private String description;
        private Long embeddingModelId;
        private Integer chunkSize;
        private Integer chunkOverlap;
        private Boolean mcpExposed;
        private String color;
        /** PUBLIC | RESTRICTED */
        private String visibility;
        /** 受限时可见的角色 */
        private List<String> accessRoles;
        /** 受限时额外可见的用户 id */
        private List<Long> accessUserIds;
    }

    @Data
    public static class WikiReq {
        private String title;
        private String content;
        private String category;
        private String tags;
        /** 分块策略：AUTO | FIXED | HEADING | TABLE，空表示 AUTO */
        private String chunkStrategy;
        /** 所属目录，空或 0 表示根目录 */
        private Long folderId;
    }

    @Data
    public static class FolderReq {
        private String name;
        /** 上级目录，空表示一级目录 */
        private Long parentId;
    }

    @Data
    public static class MoveReq {
        /** 目标目录，空或 0 表示移出到根目录 */
        private Long folderId;
    }

    @Data
    public static class SearchReq {
        private String query;
        private List<Long> kbIds;
        private Integer topK;
        private Double minScore;
        private Long docId;
    }

    @Data
    public static class AskReq {
        private String question;
        private List<Long> kbIds;
        private Integer topK;
    }

    @Data
    public static class ModelReq {
        private String name;
        private String type;
        private String provider;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Integer dimension;
        private Integer batchSize;
        private Integer timeout;
        private String extraParams;
        private Boolean enabled;
        private Boolean isDefault;
        private String remark;
    }

    @Data
    public static class LoginReq {
        private String username;
        private String password;
    }

    @Data
    public static class PasswordReq {
        private String currentPassword;
        private String newPassword;
    }

    @Data
    public static class ResetPasswordReq {
        private String newPassword;
    }

    @Data
    public static class UserReq {
        private String username;
        private String password;
        private String displayName;
        private String role;
        private Boolean enabled;
        private Boolean mustChangePassword;
    }

    @Data
    public static class ProbeReq {
        private String provider;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Integer batchSize;
        private Integer timeout;
        private String extraParams;
        /** 已保存模型的 id，用于沿用已加密保存的密钥 */
        private Long id;
    }
}
