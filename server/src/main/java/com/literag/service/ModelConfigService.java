package com.literag.service;

import com.literag.ai.EmbeddingService;
import com.literag.common.BizException;
import com.literag.dto.Requests;
import com.literag.dto.Views;
import com.literag.entity.KnowledgeBase;
import com.literag.entity.ModelConfig;
import com.literag.repository.KnowledgeBaseRepository;
import com.literag.repository.ModelConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private static final String MASK = "******";

    private final ModelConfigRepository repository;
    private final KnowledgeBaseRepository kbRepository;
    private final EmbeddingService embeddingService;

    public List<Views.ModelView> list(String type) {
        List<ModelConfig> configs = (type == null || type.isBlank())
                ? repository.findAll()
                : repository.findByTypeOrderByIsDefaultDescIdAsc(type.toUpperCase());
        return configs.stream().map(this::toView).toList();
    }

    public ModelConfig require(Long id) {
        return repository.findById(id).orElseThrow(() -> new BizException(404, "模型配置不存在: " + id));
    }

    @Transactional
    public Views.ModelView create(Requests.ModelReq req) {
        ModelConfig cfg = new ModelConfig();
        apply(cfg, req);
        if (Boolean.TRUE.equals(cfg.getIsDefault())) {
            clearDefault(cfg.getType());
        }
        repository.save(cfg);
        return toView(cfg);
    }

    @Transactional
    public Views.ModelView update(Long id, Requests.ModelReq req) {
        ModelConfig cfg = require(id);
        apply(cfg, req);
        if (Boolean.TRUE.equals(cfg.getIsDefault())) {
            clearDefault(cfg.getType());
        }
        repository.save(cfg);
        return toView(cfg);
    }

    @Transactional
    public void delete(Long id) {
        ModelConfig cfg = require(id);
        long used = kbRepository.countByEmbeddingModelId(id);
        if (used > 0) {
            throw new BizException("该模型被 " + used + " 个知识库使用，请先切换知识库的模型");
        }
        repository.delete(cfg);
    }

    @Transactional
    public void setDefault(Long id) {
        ModelConfig cfg = require(id);
        clearDefault(cfg.getType());
        cfg.setIsDefault(true);
        cfg.setEnabled(true);
        repository.save(cfg);
    }

    private void clearDefault(String type) {
        repository.findByTypeOrderByIsDefaultDescIdAsc(type).forEach(m -> {
            if (Boolean.TRUE.equals(m.getIsDefault())) {
                m.setIsDefault(false);
                repository.save(m);
            }
        });
    }

    private void apply(ModelConfig cfg, Requests.ModelReq req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException("模型名称不能为空");
        }
        cfg.setName(req.getName().trim());
        cfg.setType(req.getType() == null ? ModelConfig.TYPE_EMBEDDING : req.getType().toUpperCase());
        cfg.setProvider(req.getProvider() == null ? ModelConfig.PROVIDER_OPENAI : req.getProvider().toLowerCase());
        cfg.setBaseUrl(req.getBaseUrl());
        if (req.getApiKey() != null && !MASK.equals(req.getApiKey())) {
            cfg.setApiKey(req.getApiKey().trim());
        }
        cfg.setModelName(req.getModelName());
        if (req.getDimension() != null) {
            cfg.setDimension(req.getDimension());
        }
        if (req.getBatchSize() != null) {
            cfg.setBatchSize(req.getBatchSize());
        }
        if (req.getTimeout() != null) {
            cfg.setTimeout(req.getTimeout());
        }
        if (req.getExtraParams() != null) {
            cfg.setExtraParams(req.getExtraParams());
        }
        if (req.getEnabled() != null) {
            cfg.setEnabled(req.getEnabled());
        }
        if (req.getIsDefault() != null) {
            cfg.setIsDefault(req.getIsDefault());
        }
        if (req.getRemark() != null) {
            cfg.setRemark(req.getRemark());
        }
    }

    public Views.ModelView toView(ModelConfig cfg) {
        return Views.ModelView.builder()
                .id(cfg.getId())
                .name(cfg.getName())
                .type(cfg.getType())
                .provider(cfg.getProvider())
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey() == null || cfg.getApiKey().isBlank() ? "" : MASK)
                .modelName(cfg.getModelName())
                .dimension(cfg.getDimension())
                .batchSize(cfg.getBatchSize())
                .timeout(cfg.getTimeout())
                .extraParams(cfg.getExtraParams())
                .enabled(cfg.getEnabled())
                .isDefault(cfg.getIsDefault())
                .remark(cfg.getRemark())
                .usedByKbCount(kbRepository.countByEmbeddingModelId(cfg.getId()))
                .build();
    }

    /** 知识库使用的 embedding 模型：未指定则取默认模型 */
    public ModelConfig requireEmbeddingFor(KnowledgeBase kb) {
        if (kb.getEmbeddingModelId() != null) {
            return require(kb.getEmbeddingModelId());
        }
        return repository.findFirstByTypeAndIsDefaultTrueAndEnabledTrue(ModelConfig.TYPE_EMBEDDING)
                .orElseGet(() -> repository.findByTypeAndEnabledTrueOrderByIsDefaultDescIdAsc(ModelConfig.TYPE_EMBEDDING)
                        .stream().findFirst()
                        .orElseThrow(() -> new BizException("尚未配置可用的 Embedding 模型，请先在「模型配置」中新增")));
    }

    public ModelConfig resolveChat() {
        return repository.findFirstByTypeAndIsDefaultTrueAndEnabledTrue(ModelConfig.TYPE_CHAT)
                .orElseGet(() -> repository.findByTypeAndEnabledTrueOrderByIsDefaultDescIdAsc(ModelConfig.TYPE_CHAT)
                        .stream().findFirst().orElse(null));
    }

    private ModelConfig fromProbe(Requests.ProbeReq req) {
        ModelConfig cfg = new ModelConfig();
        if (req.getId() != null) {
            cfg = require(req.getId());
        }
        if (req.getProvider() != null) {
            cfg.setProvider(req.getProvider().toLowerCase());
        }
        if (req.getBaseUrl() != null) {
            cfg.setBaseUrl(req.getBaseUrl());
        }
        if (req.getApiKey() != null && !MASK.equals(req.getApiKey())) {
            cfg.setApiKey(req.getApiKey());
        }
        if (req.getModelName() != null) {
            cfg.setModelName(req.getModelName());
        }
        if (req.getBatchSize() != null) {
            cfg.setBatchSize(req.getBatchSize());
        }
        if (req.getTimeout() != null) {
            cfg.setTimeout(req.getTimeout());
        }
        if (req.getExtraParams() != null) {
            cfg.setExtraParams(req.getExtraParams());
        }
        return cfg;
    }

    public Map<String, Object> testEmbedding(Requests.ProbeReq req) {
        ModelConfig cfg = fromProbe(req);
        Map<String, Object> result = new LinkedHashMap<>(embeddingService.probe(cfg));
        result.put("provider", cfg.getProvider());
        result.put("model", cfg.getModelName());
        return result;
    }

    public Map<String, Object> testChat(Requests.ProbeReq req) {
        ModelConfig cfg = fromProbe(req);
        long start = System.currentTimeMillis();
        String answer = embeddingService.resolveChat(cfg.getProvider())
                .chat(cfg, "你是连通性测试助手，请用不超过 20 个字回答。", "回复：连接成功", 0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        result.put("costMs", System.currentTimeMillis() - start);
        return result;
    }
}
