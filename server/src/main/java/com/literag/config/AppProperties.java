package com.literag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 静态配置（application.yml）。可在运行期被系统设置覆盖的部分见 SettingsService。
 */
@Data
@ConfigurationProperties(prefix = "literag")
public class AppProperties {

    /** 原始文件存储目录 */
    private String storageDir = "./data/uploads";

    /** 前端构建产物目录（存在时由后端直接托管，便于单端口部署） */
    private String webDir = "../web/dist";

    private Vector vector = new Vector();
    private Chunk chunk = new Chunk();
    private Mcp mcp = new Mcp();
    private Cors cors = new Cors();

    @Data
    public static class Vector {
        /** auto | qdrant | local */
        private String provider = "qdrant";
        private Qdrant qdrant = new Qdrant();
    }

    @Data
    public static class Qdrant {
        private String url = "http://localhost:6333";
        private String apiKey = "";
        private int timeout = 20;
    }

    @Data
    public static class Chunk {
        private int size = 800;
        private int overlap = 120;
    }

    @Data
    public static class Mcp {
        private String serverName = "literag-knowledge";
        private String serverVersion = "1.0.0";
        private String apiKey = "";
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "*";
    }
}
