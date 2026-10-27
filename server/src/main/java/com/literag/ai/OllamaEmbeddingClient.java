package com.literag.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.literag.common.BizException;
import com.literag.entity.ModelConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地 Ollama 服务的 embedding 客户端，默认 http://localhost:11434。
 */
@Component
@RequiredArgsConstructor
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final AiHttp http;

    @Override
    public String provider() {
        return ModelConfig.PROVIDER_OLLAMA;
    }

    @Override
    public List<float[]> embed(List<String> texts, ModelConfig cfg) {
        String baseUrl = cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()
                ? "http://localhost:11434" : cfg.getBaseUrl().trim();
        int timeout = cfg.getTimeout() == null ? 120 : cfg.getTimeout();

        // 新版 /api/embed 支持批量
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", cfg.getModelName());
            body.put("input", texts);
            JsonNode res = http.post(AiHttp.join(baseUrl, "/api/embed"), Map.of(), body, timeout);
            JsonNode arr = res.path("embeddings");
            if (arr.isArray() && !arr.isEmpty()) {
                return parseBatch(arr);
            }
        } catch (Exception e) {
            // 老版本 Ollama 只有单条接口，继续尝试
        }

        List<float[]> out = new ArrayList<>(texts.size());
        for (String text : texts) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", cfg.getModelName());
            body.put("prompt", text);
            JsonNode res = http.post(AiHttp.join(baseUrl, "/api/embeddings"), Map.of(), body, timeout);
            JsonNode emb = res.path("embedding");
            if (!emb.isArray() || emb.isEmpty()) {
                throw new BizException(502, "Ollama 未返回向量，请确认模型已拉取：" + cfg.getModelName());
            }
            out.add(parseOne(emb));
        }
        return out;
    }

    private List<float[]> parseBatch(JsonNode arr) {
        List<float[]> out = new ArrayList<>(arr.size());
        arr.forEach(node -> out.add(parseOne(node)));
        return out;
    }

    private float[] parseOne(JsonNode emb) {
        float[] vec = new float[emb.size()];
        for (int i = 0; i < emb.size(); i++) {
            vec[i] = (float) emb.get(i).asDouble();
        }
        return vec;
    }
}
