package com.literag.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.literag.common.BizException;
import com.literag.entity.ModelConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiChatClient implements ChatClient {

    private final AiHttp http;
    private final ObjectMapper mapper;

    @Override
    public String provider() {
        return ModelConfig.PROVIDER_OPENAI;
    }

    @Override
    public String chat(ModelConfig cfg, String system, String user, double temperature) {
        String baseUrl = cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()
                ? "https://api.openai.com/v1" : cfg.getBaseUrl().trim();
        String url = AiHttp.join(baseUrl, "/chat/completions");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModelName());
        body.put("temperature", temperature);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        ));
        if (cfg.getExtraParams() != null && !cfg.getExtraParams().isBlank()) {
            try {
                JsonNode extra = mapper.readTree(cfg.getExtraParams());
                extra.fields().forEachRemaining(e -> body.put(e.getKey(), mapper.convertValue(e.getValue(), Object.class)));
            } catch (Exception ignored) {
            }
        }

        Map<String, String> headers = new LinkedHashMap<>();
        if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
            headers.put("Authorization", "Bearer " + cfg.getApiKey().trim());
        }
        JsonNode res = http.post(url, headers, body, cfg.getTimeout() == null ? 120 : cfg.getTimeout());
        JsonNode content = res.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new BizException(502, "大模型未返回内容：" + res);
        }
        return content.asText();
    }
}
