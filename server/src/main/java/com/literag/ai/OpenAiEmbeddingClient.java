package com.literag.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.literag.common.BizException;
import com.literag.entity.ModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 所有 OpenAI 兼容接口的 embedding 客户端（OpenAI / 硅基流动 / 通义 compatible-mode / 智谱 / vLLM / one-api 等）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final AiHttp http;
    private final ObjectMapper mapper;

    @Override
    public String provider() {
        return ModelConfig.PROVIDER_OPENAI;
    }

    @Override
    public List<float[]> embed(List<String> texts, ModelConfig cfg) {
        String baseUrl = cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()
                ? "https://api.openai.com/v1" : cfg.getBaseUrl().trim();
        String url = AiHttp.join(baseUrl, "/embeddings");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModelName());
        body.put("input", texts);
        body.put("encoding_format", "float");
        mergeExtra(body, cfg.getExtraParams());

        Map<String, String> headers = new LinkedHashMap<>();
        if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
            headers.put("Authorization", "Bearer " + cfg.getApiKey().trim());
        }

        JsonNode res = http.post(url, headers, body, cfg.getTimeout() == null ? 60 : cfg.getTimeout());
        JsonNode data = res.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new BizException(502, "Embedding 接口未返回向量：" + res);
        }
        List<JsonNode> items = new ArrayList<>();
        data.forEach(items::add);
        items.sort(Comparator.comparingInt(n -> n.path("index").asInt(0)));

        List<float[]> vectors = new ArrayList<>(items.size());
        for (JsonNode item : items) {
            JsonNode emb = item.path("embedding");
            if (!emb.isArray() || emb.isEmpty()) {
                throw new BizException(502, "Embedding 返回格式异常");
            }
            float[] vec = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) {
                vec[i] = (float) emb.get(i).asDouble();
            }
            vectors.add(vec);
        }
        return vectors;
    }

    private void mergeExtra(Map<String, Object> body, String extraParams) {
        if (extraParams == null || extraParams.isBlank()) {
            return;
        }
        try {
            JsonNode node = mapper.readTree(extraParams);
            node.fields().forEachRemaining(e -> body.put(e.getKey(), mapper.convertValue(e.getValue(), Object.class)));
        } catch (Exception e) {
            log.warn("附加参数解析失败，已忽略: {}", e.getMessage());
        }
    }
}
