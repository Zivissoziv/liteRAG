package com.literag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

public class Views {

    @Data
    @Builder
    public static class ChunkHit {
        private String vectorId;
        private Long docId;
        private String docName;
        private Long kbId;
        private String kbName;
        private Integer seq;
        private String title;
        private String content;
        private double score;
    }

    @Data
    @Builder
    public static class SearchResult {
        private String query;
        private String backend;
        private long tookMs;
        private int total;
        private List<ChunkHit> hits;
    }

    @Data
    @Builder
    public static class AskResult {
        private String answer;
        private String model;
        private String backend;
        private long tookMs;
        private boolean llmUsed;
        private String promptPreview;
        private List<ChunkHit> citations;
    }

    @Data
    @Builder
    public static class ChunkView {
        private Long id;
        private String vectorId;
        private Integer seq;
        private Integer charCount;
        private Integer dim;
        private String content;
    }

    @Data
    @Builder
    public static class ModelView {
        private Long id;
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
        private Long usedByKbCount;
    }
}
