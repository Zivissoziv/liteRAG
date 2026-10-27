package com.literag.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.literag.common.BizException;
import com.literag.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ChromaDB 轻量 REST 客户端，兼容 v1（0.4.x）与 v2（0.5+/1.x）两套 API。
 * 通过 /heartbeat 自动探测版本，无需引入 Chroma 官方 SDK。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChromaClient {

    private final SettingsService settings;
    private final ObjectMapper mapper;

    private volatile RestClient client;
    private volatile String clientKey;
    private volatile String detectedVersion;

    // ------------------------------------------------------------------ http

    private RestClient client() {
        String url = trimTrailingSlash(settings.get(SettingsService.CHROMA_URL));
        int timeout = settings.getInt("chromaTimeout", 20) * 1000;
        String key = url + "|" + timeout;
        if (client == null || !key.equals(clientKey)) {
            synchronized (this) {
                if (client == null || !key.equals(clientKey)) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(Math.min(timeout, 8000));
                    factory.setReadTimeout(timeout);
                    client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
                    clientKey = key;
                    detectedVersion = null;
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

    /** auto | v1 | v2 */
    public String version() {
        String fixed = settings.get(SettingsService.CHROMA_API_VERSION);
        if ("v1".equalsIgnoreCase(fixed) || "v2".equalsIgnoreCase(fixed)) {
            return fixed.toLowerCase();
        }
        client(); // 确保 baseUrl 变更后重新探测
        if (detectedVersion != null) {
            return detectedVersion;
        }
        synchronized (this) {
            if (detectedVersion != null) {
                return detectedVersion;
            }
            String v = "v1";
            try {
                String body = client().get().uri("/api/v2/heartbeat").retrieve().body(String.class);
                if (body != null && !body.isBlank()) {
                    v = "v2";
                }
            } catch (Exception e) {
                log.info("ChromaDB 未响应 /api/v2/heartbeat，回退 v1 API");
            }
            detectedVersion = v;
            return v;
        }
    }

    public boolean available() {
        try {
            String v = version();
            String body = client().get().uri("/api/" + v + "/heartbeat").retrieve().body(String.class);
            return body != null;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", trimTrailingSlash(settings.get(SettingsService.CHROMA_URL)));
        boolean ok = available();
        m.put("available", ok);
        m.put("version", ok ? version() : null);
        m.put("tenant", settings.get(SettingsService.CHROMA_TENANT));
        m.put("database", settings.get(SettingsService.CHROMA_DATABASE));
        return m;
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
            throw new BizException(502, "ChromaDB 返回错误 " + e.getStatusCode().value() + ": "
                    + brief(e.getResponseBodyAsString()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(503, "无法连接 ChromaDB（" + settings.get(SettingsService.CHROMA_URL) + "）: " + e.getMessage());
        }
    }

    private JsonNode getJson(String path) {
        try {
            String res = client().get().uri(path).retrieve().body(String.class);
            return parse(res);
        } catch (RestClientResponseException e) {
            throw new BizException(502, "ChromaDB 返回错误 " + e.getStatusCode().value() + ": "
                    + brief(e.getResponseBodyAsString()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(503, "无法连接 ChromaDB: " + e.getMessage());
        }
    }

    private void deletePath(String path) {
        try {
            client().delete().uri(path).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            throw new BizException(502, "ChromaDB 返回错误 " + e.getStatusCode().value() + ": "
                    + brief(e.getResponseBodyAsString()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(503, "无法连接 ChromaDB: " + e.getMessage());
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

    private String collectionsPath() {
        if ("v2".equals(version())) {
            return "/api/v2/tenants/" + settings.get(SettingsService.CHROMA_TENANT)
                    + "/databases/" + settings.get(SettingsService.CHROMA_DATABASE) + "/collections";
        }
        return "/api/v1/collections";
    }

    private String collectionPath(String collectionId) {
        return collectionsPath() + "/" + collectionId;
    }

    // ------------------------------------------------------------- operations

    public String ensureCollection(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("get_or_create", true);
        body.put("metadata", Map.of("hnsw:space", "cosine"));
        JsonNode res = postJson(collectionsPath(), body);
        String id = res.path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new BizException(502, "创建 ChromaDB collection 失败: " + res);
        }
        return id;
    }

    public void add(String collectionId, List<VectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>(records.size());
        List<float[]> embeddings = new ArrayList<>(records.size());
        List<String> documents = new ArrayList<>(records.size());
        List<Map<String, Object>> metadatas = new ArrayList<>(records.size());
        for (VectorRecord r : records) {
            ids.add(r.getId());
            embeddings.add(r.getEmbedding());
            documents.add(r.getContent() == null ? "" : r.getContent());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("doc_id", r.getDocId());
            meta.put("kb_id", r.getKbId());
            meta.put("seq", r.getSeq());
            meta.put("title", r.getTitle() == null ? "" : r.getTitle());
            metadatas.add(meta);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", ids);
        body.put("embeddings", embeddings);
        body.put("documents", documents);
        body.put("metadatas", metadatas);
        postJson(collectionPath(collectionId) + "/add", body);
    }

    public void deleteWhereDoc(String collectionId, Long docId) {
        Map<String, Object> body = Map.of("where", Map.of("doc_id", docId));
        postJson(collectionPath(collectionId) + "/delete", body);
    }

    public void deleteIds(String collectionId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        postJson(collectionPath(collectionId) + "/delete", Map.of("ids", ids));
    }

    public long count(String collectionId) {
        JsonNode node = getJson(collectionPath(collectionId) + "/count");
        if (node.isNumber()) {
            return node.asLong();
        }
        if (node.isObject() && node.has("count")) {
            return node.get("count").asLong();
        }
        try {
            return Long.parseLong(node.asText("0").trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public List<SearchHit> query(String collectionId, float[] vector, int topK) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query_embeddings", List.of(vector));
        body.put("n_results", Math.max(1, topK));
        body.put("include", List.of("documents", "metadatas", "distances"));
        JsonNode res = postJson(collectionPath(collectionId) + "/query", body);

        List<SearchHit> hits = new ArrayList<>();
        JsonNode ids = res.path("ids");
        if (!ids.isArray() || ids.isEmpty()) {
            return hits;
        }
        JsonNode idRow = ids.get(0);
        JsonNode docRow = res.path("documents").path(0);
        JsonNode metaRow = res.path("metadatas").path(0);
        JsonNode distRow = res.path("distances").path(0);
        for (int i = 0; i < idRow.size(); i++) {
            JsonNode meta = metaRow.isArray() && metaRow.size() > i ? metaRow.get(i) : null;
            double distance = distRow.isArray() && distRow.size() > i ? distRow.get(i).asDouble() : 1;
            double score = Math.max(0, Math.min(1, 1 - distance));
            hits.add(SearchHit.builder()
                    .vectorId(idRow.get(i).asText())
                    .docId(meta != null && meta.hasNonNull("doc_id") ? meta.get("doc_id").asLong() : null)
                    .kbId(meta != null && meta.hasNonNull("kb_id") ? meta.get("kb_id").asLong() : null)
                    .seq(meta != null && meta.hasNonNull("seq") ? meta.get("seq").asInt() : 0)
                    .title(meta != null && meta.hasNonNull("title") ? meta.get("title").asText() : "")
                    .content(docRow.isArray() && docRow.size() > i ? docRow.get(i).asText("") : "")
                    .score(score)
                    .backend("chroma")
                    .build());
        }
        return hits;
    }

    public void dropCollection(String collectionId) {
        deletePath(collectionPath(collectionId));
    }
}
