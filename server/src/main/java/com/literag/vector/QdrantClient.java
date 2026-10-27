package com.literag.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.literag.common.BizException;
import com.literag.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Qdrant 轻量 REST 客户端，仅依赖 6333 端口的 HTTP API，无需官方 SDK。
 *
 * 两点内部约定：
 * 1. Qdrant 点 id 只接受无符号整数或 UUID，系统的字符串 vectorId（如 "4-17"）
 *    通过 nameUUIDFromBytes 确定性映射为 UUID，原始 id 存 payload.vectorId；
 * 2. Qdrant 建 collection 必须声明向量维度，因此在首次 upsert 时按记录的实际维度建库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantClient {

    private final SettingsService settings;
    private final ObjectMapper mapper;

    private volatile RestClient client;
    private volatile String clientKey;

    // ------------------------------------------------------------------ http

    private RestClient client() {
        String url = trimTrailingSlash(settings.get(SettingsService.QDRANT_URL));
        int timeout = settings.getInt(SettingsService.QDRANT_TIMEOUT, 20) * 1000;
        String key = url + "|" + timeout;
        if (client == null || !key.equals(clientKey)) {
            synchronized (this) {
                if (client == null || !key.equals(clientKey)) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(Math.min(timeout, 8000));
                    factory.setReadTimeout(timeout);
                    RestClient.Builder builder = RestClient.builder().baseUrl(url).requestFactory(factory);
                    String apiKey = settings.get(SettingsService.QDRANT_API_KEY);
                    if (apiKey != null && !apiKey.isBlank()) {
                        builder.defaultHeader("api-key", apiKey.trim());
                    }
                    builder.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                    client = builder.build();
                    clientKey = key;
                }
            }
        }
        return client;
    }

    private static String trimTrailingSlash(String url) {
        String u = url == null ? "" : url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    public boolean available() {
        try {
            String body = client().get().uri("/healthz").retrieve().body(String.class);
            return body != null;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", trimTrailingSlash(settings.get(SettingsService.QDRANT_URL)));
        m.put("available", available());
        return m;
    }

    private JsonNode putJson(String path, Object body) {
        try {
            String res = client().put().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parse(res);
        } catch (RestClientResponseException e) {
            throw new BizException(502, "Qdrant 返回错误 " + e.getStatusCode().value() + ": "
                    + brief(e.getResponseBodyAsString()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(503, "无法连接 Qdrant（" + settings.get(SettingsService.QDRANT_URL) + "）: " + e.getMessage());
        }
    }

    private JsonNode postJson(String path, Object body) {
        try {
            String res = client().post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parse(res);
        } catch (RestClientResponseException e) {
            throw new BizException(502, "Qdrant 返回错误 " + e.getStatusCode().value() + ": "
                    + brief(e.getResponseBodyAsString()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(503, "无法连接 Qdrant: " + e.getMessage());
        }
    }

    private void deletePath(String path) {
        try {
            client().delete().uri(path).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            throw new BizException(502, "Qdrant 返回错误 " + e.getStatusCode().value() + ": "
                    + brief(e.getResponseBodyAsString()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(503, "无法连接 Qdrant: " + e.getMessage());
        }
    }

    private JsonNode parse(String res) {
        try {
            if (res == null || res.isBlank()) {
                return mapper.createObjectNode();
            }
            return mapper.readTree(res);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static String brief(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ");
        return one.length() > 300 ? one.substring(0, 300) : one;
    }

    private static String collectionPath(String name) {
        return "/collections/" + name;
    }

    // ------------------------------------------------------------- operations

    /** collection 是否存在（404 视为不存在，其余错误照常抛出） */
    public boolean collectionExists(String name) {
        try {
            client().get().uri(collectionPath(name)).retrieve().body(String.class);
            return true;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            throw new BizException(502, "Qdrant 返回错误 " + e.getStatusCode().value() + ": "
                    + brief(e.getResponseBodyAsString()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(503, "无法连接 Qdrant: " + e.getMessage());
        }
    }

    public void createCollection(String name, int dim) {
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", dim);
        vectors.put("distance", "Cosine");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vectors", vectors);
        try {
            putJson(collectionPath(name), body);
        } catch (BizException e) {
            // 并发建库时他人可能抢先创建，409 视为成功（维度以先建者为准）
            if (!String.valueOf(e.getMessage()).contains("409")) {
                throw e;
            }
            log.info("Qdrant collection {} 已由并发任务创建", name);
        }
    }

    public void upsert(String collectionName, List<VectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Map<String, Object>> points = new ArrayList<>(records.size());
        for (VectorRecord r : records) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("vectorId", r.getId());
            payload.put("docId", r.getDocId());
            payload.put("kbId", r.getKbId());
            payload.put("seq", r.getSeq());
            payload.put("title", r.getTitle() == null ? "" : r.getTitle());
            payload.put("content", r.getContent() == null ? "" : r.getContent());
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", pointId(r.getId()));
            point.put("vector", r.getEmbedding());
            point.put("payload", payload);
            points.add(point);
        }
        // wait=true 保证写后立即可查，与 Chroma 行为一致
        putJson(collectionPath(collectionName) + "/points?wait=true", Map.of("points", points));
    }

    public void deleteByDoc(String collectionName, Long docId) {
        Map<String, Object> cond = Map.of("key", "docId", "match", Map.of("value", docId));
        Map<String, Object> body = Map.of("filter", Map.of("must", List.of(cond)));
        postJson(collectionPath(collectionName) + "/points/delete", body);
    }

    public void deleteIds(String collectionName, List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        List<String> ids = vectorIds.stream().map(QdrantClient::pointId).toList();
        postJson(collectionPath(collectionName) + "/points/delete", Map.of("points", ids));
    }

    public long count(String collectionName) {
        if (!collectionExists(collectionName)) {
            return 0;
        }
        JsonNode res = postJson(collectionPath(collectionName) + "/points/count", Map.of("exact", true));
        return res.path("result").path("count").asLong(0);
    }

    public List<SearchHit> search(String collectionName, float[] vector, int topK) {
        if (!collectionExists(collectionName)) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", vector);
        body.put("limit", Math.max(1, topK));
        body.put("with_payload", true);
        JsonNode res = postJson(collectionPath(collectionName) + "/points/search", body);

        List<SearchHit> hits = new ArrayList<>();
        JsonNode arr = res.path("result");
        if (!arr.isArray()) {
            return hits;
        }
        for (JsonNode p : arr) {
            JsonNode payload = p.path("payload");
            // Cosine 模式下 Qdrant 返回的 score 即余弦相似度，越大越相关，与系统约定一致
            double score = Math.max(0, Math.min(1, p.path("score").asDouble()));
            hits.add(SearchHit.builder()
                    .vectorId(payload.path("vectorId").asText(""))
                    .docId(payload.hasNonNull("docId") ? payload.get("docId").asLong() : null)
                    .kbId(payload.hasNonNull("kbId") ? payload.get("kbId").asLong() : null)
                    .seq(payload.path("seq").asInt(0))
                    .title(payload.path("title").asText(""))
                    .content(payload.path("content").asText(""))
                    .score(score)
                    .backend("qdrant")
                    .build());
        }
        return hits;
    }

    public void dropCollection(String collectionName) {
        if (collectionExists(collectionName)) {
            deletePath(collectionPath(collectionName));
        }
    }

    /** 字符串 vectorId → 确定性 UUID（Qdrant 只接受无符号整数或 UUID 作点 id） */
    public static String pointId(String vectorId) {
        return UUID.nameUUIDFromBytes(String.valueOf(vectorId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
