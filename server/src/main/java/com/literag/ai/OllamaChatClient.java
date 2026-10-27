package com.literag.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.literag.common.BizException;
import com.literag.entity.ModelConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OllamaChatClient implements ChatClient {

    private final AiHttp http;

    @Override
    public String provider() {
        return ModelConfig.PROVIDER_OLLAMA;
    }

    @Override
    public String chat(ModelConfig cfg, String system, String user, double temperature) {
        String baseUrl = cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()
                ? "http://localhost:11434" : cfg.getBaseUrl().trim();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModelName());
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        ));
        body.put("options", Map.of("temperature", temperature));

        JsonNode res = http.post(AiHttp.join(baseUrl, "/api/chat"), Map.of(), body,
                cfg.getTimeout() == null ? 180 : cfg.getTimeout());
        JsonNode content = res.path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new BizException(502, "Ollama 未返回内容：" + res);
        }
        return content.asText();
    }
}
