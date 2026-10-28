package com.literag.vector;

import com.literag.common.BizException;
import com.literag.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 向量存储入口。引擎只有两种：Qdrant（外部服务，默认）与本地（进程内余弦扫描，零依赖兜底）。
 */
@Service
@RequiredArgsConstructor
public class VectorStoreManager {

    private final QdrantVectorStore qdrantStore;
    private final LocalVectorStore localStore;
    private final SettingsService settings;
    private final QdrantClient qdrantClient;

    private volatile long lastCheck = 0;
    private volatile boolean lastAvailable = false;

    private boolean qdrantAvailable(boolean forceFresh) {
        long now = System.currentTimeMillis();
        if (!forceFresh && now - lastCheck < 5000) {
            return lastAvailable;
        }
        boolean ok;
        try {
            ok = qdrantStore.available();
        } catch (Exception e) {
            ok = false;
        }
        lastAvailable = ok;
        lastCheck = now;
        return ok;
    }

    public VectorStore active() {
        String provider = settings.get(SettingsService.VECTOR_PROVIDER);
        if ("local".equalsIgnoreCase(provider)) {
            return localStore;
        }
        if ("qdrant".equalsIgnoreCase(provider)) {
            if (!qdrantAvailable(true)) {
                throw new BizException(503, "系统设置为使用 Qdrant，但当前无法连接："
                        + settings.get(SettingsService.QDRANT_URL) + "，请先启动 Qdrant 或切换为本地向量库");
            }
            return qdrantStore;
        }
        // auto：Qdrant 可用则用，否则回退本地
        return qdrantAvailable(false) ? qdrantStore : localStore;
    }

    public String activeBackend() {
        try {
            return active().backend();
        } catch (BizException e) {
            return "unavailable";
        }
    }

    public boolean isLocal() {
        return "local".equals(activeBackend());
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", settings.get(SettingsService.VECTOR_PROVIDER));
        m.put("active", activeBackend());
        m.put("qdrant", qdrantClient.health());
        m.put("chunkSize", settings.getInt(SettingsService.CHUNK_SIZE, 800));
        m.put("chunkOverlap", settings.getInt(SettingsService.CHUNK_OVERLAP, 120));
        return m;
    }
}
