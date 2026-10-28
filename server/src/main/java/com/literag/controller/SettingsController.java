package com.literag.controller;

import com.literag.common.ApiResponse;
import com.literag.service.SettingsService;
import com.literag.vector.VectorStoreManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settings;
    private final VectorStoreManager vectorStoreManager;
    private final DataSource dataSource;

    @GetMapping("/settings")
    public ApiResponse<Map<String, Object>> get() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("settings", settings.all());
        m.put("vector", vectorStoreManager.status());
        return ApiResponse.ok(m);
    }

    @PutMapping("/settings")
    public ApiResponse<Map<String, Object>> update(@RequestBody Map<String, String> values) {
        settings.update(values);
        return get();
    }

    @GetMapping("/system/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vector", vectorStoreManager.status());
        m.put("database", databaseInfo());
        m.put("mcp", mcpInfo());
        m.put("settings", settings.all());
        return ApiResponse.ok(m);
    }

    /** 当前元数据库信息：类型、版本、连接地址（SQLite 附带文件大小），便于确认数据落在哪里 */
    private Map<String, Object> databaseInfo() {
        Map<String, Object> db = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            String product = md.getDatabaseProductName();
            String url = md.getURL();
            db.put("product", product);
            db.put("version", md.getDatabaseProductVersion());
            db.put("driver", md.getDriverName());
            db.put("url", mask(url));
            if (product != null && product.toLowerCase().contains("sqlite")) {
                db.put("kind", "SQLITE");
                db.put("file", url.replaceFirst("^jdbc:sqlite:", "").split("\\?")[0]);
                db.put("size", fileSize(url));
            } else {
                db.put("kind", product != null && product.toLowerCase().contains("mysql") ? "MYSQL" : "OTHER");
            }
            db.put("available", true);
        } catch (Exception e) {
            db.put("available", false);
            db.put("error", e.getMessage());
        }
        return db;
    }

    private long fileSize(String url) {
        try {
            String p = url.replaceFirst("^jdbc:sqlite:", "").split("\\?")[0];
            Path path = Paths.get(p);
            if (!Files.exists(path)) {
                return -1;
            }
            long total = Files.size(path);
            // WAL 模式下未 checkpoint 的数据在 -wal 文件里，一并计入更贴近真实占用
            Path wal = Paths.get(p + "-wal");
            if (Files.exists(wal)) {
                total += Files.size(wal);
            }
            return total;
        } catch (Exception e) {
            return -1;
        }
    }

    private String mask(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("(?i)(password=)[^;&]*", "$1***");
    }

    public Map<String, Object> mcpInfo() {
        Map<String, Object> mcp = new LinkedHashMap<>();
        mcp.put("name", "literag-knowledge");
        mcp.put("streamableHttp", "/mcp");
        mcp.put("sse", "/mcp/sse");
        mcp.put("message", "/mcp/message");
        mcp.put("requireApiKey", !settings.get(SettingsService.MCP_API_KEY).isBlank());
        mcp.put("tools", List.of(
                Map.of("name", "search_knowledge", "description", "在知识库中做语义检索，返回最相关的文本片段与相似度"),
                Map.of("name", "ask_knowledge", "description", "基于知识库做检索增强问答，返回答案与引用来源"),
                Map.of("name", "list_knowledge_bases", "description", "列出全部已开放的知识库"),
                Map.of("name", "list_documents", "description", "列出指定知识库下的文档"),
                Map.of("name", "get_document", "description", "获取文档全文或指定片段范围")
        ));
        return mcp;
    }
}
