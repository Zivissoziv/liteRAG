package com.literag.ai;

import com.literag.common.BizException;
import com.literag.entity.ModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    private final Map<String, EmbeddingClient> clients = new LinkedHashMap<>();
    private final Map<String, ChatClient> chatClients = new LinkedHashMap<>();

    public EmbeddingService(List<EmbeddingClient> embeddingClients, List<ChatClient> chatClientList) {
        embeddingClients.forEach(c -> clients.put(c.provider(), c));
        chatClientList.forEach(c -> chatClients.put(c.provider(), c));
    }

    public EmbeddingClient resolve(String provider) {
        String key = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        EmbeddingClient c = clients.get(key);
        if (c == null) {
            // openai-compatible / custom / dashscope / siliconflow 等一律走 OpenAI 协议
            c = clients.get(ModelConfig.PROVIDER_OPENAI);
        }
        if (c == null) {
            throw new BizException("未找到可用的 embedding 客户端: " + provider);
        }
        return c;
    }

    public ChatClient resolveChat(String provider) {
        String key = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        ChatClient c = chatClients.get(key);
        if (c == null) {
            c = chatClients.get(ModelConfig.PROVIDER_OPENAI);
        }
        if (c == null) {
            throw new BizException("未找到可用的大模型客户端: " + provider);
        }
        return c;
    }

    /** 批量向量化，按 maxBatchSize 拆包，失败重试一次。 */
    public List<float[]> embed(ModelConfig cfg, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        EmbeddingClient client = resolve(cfg.getProvider());
        int batchSize = cfg.getBatchSize() == null || cfg.getBatchSize() <= 0 ? 16 : Math.min(cfg.getBatchSize(), 128);
        List<float[]> out = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + batchSize));
            List<float[]> vectors = null;
            RuntimeException last = null;
            for (int attempt = 0; attempt < 2 && vectors == null; attempt++) {
                try {
                    vectors = client.embed(batch, cfg);
                } catch (RuntimeException e) {
                    last = e;
                    log.warn("embedding 批次失败（第 {} 次尝试）: {}", attempt + 1, e.getMessage());
                }
            }
            if (vectors == null) {
                throw last == null ? new BizException("向量化失败") : last;
            }
            if (vectors.size() != batch.size()) {
                throw new BizException(502, "向量数量与文本数量不一致：期望 " + batch.size() + "，实际 " + vectors.size());
            }
            out.addAll(vectors);
        }
        int dim = out.get(0).length;
        if (cfg.getDimension() != null && cfg.getDimension() > 0 && cfg.getDimension() != dim) {
            throw new BizException("模型实际维度 " + dim + " 与配置维度 " + cfg.getDimension() + " 不一致，请修正模型配置");
        }
        for (float[] v : out) {
            if (v.length != dim) {
                throw new BizException("同一批次向量维度不一致：" + dim + " vs " + v.length);
            }
        }
        return out;
    }

    /** 探测模型维度（用于模型配置页"测试连接"） */
    public Map<String, Object> probe(ModelConfig cfg) {
        long start = System.currentTimeMillis();
        List<float[]> vectors = embed(cfg, List.of("LiteRAG embedding dimension probe", "知识库向量维度探测"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", vectors.get(0).length);
        result.put("vectors", vectors.size());
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("preview", java.util.Arrays.toString(
                java.util.Arrays.copyOf(vectors.get(0), Math.min(6, vectors.get(0).length))));
        return result;
    }
}
