package com.literag.controller;

import com.literag.common.ApiResponse;
import com.literag.service.SettingsService;
import com.literag.vector.VectorStoreManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settings;
    private final VectorStoreManager vectorStoreManager;

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
        m.put("mcp", mcpInfo());
        m.put("settings", settings.all());
        return ApiResponse.ok(m);
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
