package com.literag.service;

import com.literag.config.AppProperties;
import com.literag.entity.AppSetting;
import com.literag.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行期可修改的系统设置，覆盖 application.yml 中的默认值。
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    public static final String QDRANT_URL = "qdrantUrl";
    public static final String QDRANT_API_KEY = "qdrantApiKey";
    public static final String QDRANT_TIMEOUT = "qdrantTimeout";
    public static final String VECTOR_PROVIDER = "vectorProvider";
    public static final String DEFAULT_TOP_K = "defaultTopK";
    public static final String MIN_SCORE = "minScore";
    public static final String MCP_API_KEY = "mcpApiKey";
    public static final String CHUNK_SIZE = "chunkSize";
    public static final String CHUNK_OVERLAP = "chunkOverlap";
    public static final String LLM_TEMPERATURE = "llmTemperature";
    public static final String MAX_CONTEXT_CHARS = "maxContextChars";

    private static final String MASK = "******";

    /** 已下线引擎（ChromaDB）的遗留键，读设置时直接忽略 */
    private static final java.util.Set<String> LEGACY_KEYS = java.util.Set.of(
            "chromaUrl", "chromaApiVersion", "chromaTenant", "chromaDatabase");

    private final AppSettingRepository repository;
    private final AppProperties props;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private Map<String, String> defaults() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(VECTOR_PROVIDER, props.getVector().getProvider());
        m.put(QDRANT_URL, props.getVector().getQdrant().getUrl());
        m.put(QDRANT_API_KEY, props.getVector().getQdrant().getApiKey());
        m.put(QDRANT_TIMEOUT, String.valueOf(props.getVector().getQdrant().getTimeout()));
        m.put(DEFAULT_TOP_K, "6");
        m.put(MIN_SCORE, "0");
        m.put(MCP_API_KEY, props.getMcp().getApiKey());
        m.put(CHUNK_SIZE, String.valueOf(props.getChunk().getSize()));
        m.put(CHUNK_OVERLAP, String.valueOf(props.getChunk().getOverlap()));
        m.put(LLM_TEMPERATURE, "0.2");
        m.put(MAX_CONTEXT_CHARS, "6000");
        return m;
    }

    public String get(String key) {
        return cache.computeIfAbsent(key, k -> normalize(k, repository.findById(k)
                .map(AppSetting::getSettingValue)
                .filter(v -> v != null && !v.isBlank())
                .orElseGet(() -> defaults().getOrDefault(k, ""))));
    }

    /** 老版本把 provider 存成 chroma，引擎下线后按 qdrant 处理 */
    private static String normalize(String key, String value) {
        if (VECTOR_PROVIDER.equals(key) && "chroma".equalsIgnoreCase(value == null ? "" : value.trim())) {
            return "qdrant";
        }
        return value;
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (Exception e) {
            return def;
        }
    }

    public double getDouble(String key, double def) {
        try {
            return Double.parseDouble(get(key).trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** 返回全部设置（密钥脱敏） */
    public Map<String, String> all() {
        Map<String, String> all = new LinkedHashMap<>(defaults());
        repository.findAll().stream()
                .filter(s -> !LEGACY_KEYS.contains(s.getSettingKey()))
                .forEach(s -> all.put(s.getSettingKey(), s.getSettingValue()));
        all.replaceAll(SettingsService::normalize);
        all.forEach((k, v) -> {
            if (isSecret(k) && v != null && !v.isBlank()) {
                all.put(k, MASK);
            }
        });
        return all;
    }

    @Transactional
    public void update(Map<String, String> values) {
        if (values == null) {
            return;
        }
        values.forEach((k, v) -> {
            if (v == null) {
                return;
            }
            if (isSecret(k) && MASK.equals(v)) {
                return; // 未修改
            }
            repository.save(new AppSetting(k, v));
            cache.remove(k);
        });
    }

    private boolean isSecret(String key) {
        return key.toLowerCase().contains("apikey");
    }
}
