package com.literag.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.literag.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiHttp {

    private final ObjectMapper mapper;

    public static String join(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith(path)) {
            return base;
        }
        return base + path;
    }

    public JsonNode post(String url, Map<String, String> headers, Object body, int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(Math.max(5, timeoutSeconds) * 1000);
        RestClient client = RestClient.builder().requestFactory(factory).build();
        try {
            String res = client.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (headers != null) {
                            headers.forEach(h::set);
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (res == null || res.isBlank()) {
                throw new BizException(502, "模型服务返回空响应");
            }
            return mapper.readTree(res);
        } catch (RestClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            if (detail != null && detail.length() > 400) {
                detail = detail.substring(0, 400);
            }
            throw new BizException(502, "模型服务返回 " + e.getStatusCode().value() + "：" + detail);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(503, "调用模型服务失败（" + url + "）：" + e.getMessage());
        }
    }
}
