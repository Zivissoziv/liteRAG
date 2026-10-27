package com.literag.controller;

import com.literag.common.ApiResponse;
import com.literag.dto.Requests;
import com.literag.dto.Views;
import com.literag.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelConfigService service;

    @GetMapping
    public ApiResponse<List<Views.ModelView>> list(@RequestParam(required = false) String type) {
        return ApiResponse.ok(service.list(type));
    }

    @PostMapping
    public ApiResponse<Views.ModelView> create(@RequestBody Requests.ModelReq req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<Views.ModelView> update(@PathVariable Long id, @RequestBody Requests.ModelReq req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/default")
    public ApiResponse<Void> setDefault(@PathVariable Long id) {
        service.setDefault(id);
        return ApiResponse.ok();
    }

    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> test(@RequestBody Requests.ProbeReq req) {
        return ApiResponse.ok(service.testEmbedding(req));
    }

    @PostMapping("/test-chat")
    public ApiResponse<Map<String, Object>> testChat(@RequestBody Requests.ProbeReq req) {
        return ApiResponse.ok(service.testChat(req));
    }

    @GetMapping("/presets")
    public ApiResponse<List<Map<String, Object>>> presets() {
        return ApiResponse.ok(presets0());
    }

    private List<Map<String, Object>> presets0() {
        return List.of(
                preset("硅基流动 BAAI/bge-m3", "EMBEDDING", "openai",
                        "https://api.siliconflow.cn/v1", "BAAI/bge-m3", 1024),
                preset("通义千问 text-embedding-v3", "EMBEDDING", "openai",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1", "text-embedding-v3", 1024),
                preset("OpenAI text-embedding-3-small", "EMBEDDING", "openai",
                        "https://api.openai.com/v1", "text-embedding-3-small", 1536),
                preset("智谱 embedding-3", "EMBEDDING", "openai",
                        "https://open.bigmodel.cn/api/paas/v4", "embedding-3", 2048),
                preset("Ollama bge-m3（本地）", "EMBEDDING", "ollama",
                        "http://localhost:11434", "bge-m3", 1024),
                preset("本地哈希（离线演示）", "EMBEDDING", "local", "", "local-hash-256", 256),
                preset("DeepSeek deepseek-chat", "CHAT", "openai",
                        "https://api.deepseek.com/v1", "deepseek-chat", 0),
                preset("通义千问 qwen-plus", "CHAT", "openai",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", 0),
                preset("OpenAI gpt-4o-mini", "CHAT", "openai",
                        "https://api.openai.com/v1", "gpt-4o-mini", 0),
                preset("Ollama qwen2.5（本地）", "CHAT", "ollama",
                        "http://localhost:11434", "qwen2.5", 0)
        );
    }

    private Map<String, Object> preset(String name, String type, String provider,
                                       String baseUrl, String model, int dim) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", type);
        m.put("provider", provider);
        m.put("baseUrl", baseUrl);
        m.put("modelName", model);
        m.put("dimension", dim);
        return m;
    }
}
