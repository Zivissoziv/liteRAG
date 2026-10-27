package com.literag.service;

import com.literag.common.BizException;
import com.literag.dto.KnowledgeBaseDto;
import com.literag.dto.Requests;
import com.literag.entity.Document;
import com.literag.entity.KnowledgeBase;
import com.literag.entity.ModelConfig;
import com.literag.repository.DocChunkRepository;
import com.literag.repository.DocFolderRepository;
import com.literag.repository.DocumentRepository;
import com.literag.repository.KnowledgeBaseRepository;
import com.literag.repository.ModelConfigRepository;
import com.literag.vector.VectorStore;
import com.literag.vector.VectorStoreManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;
    private final ModelConfigRepository modelRepository;
    private final DocumentRepository documentRepository;
    private final DocChunkRepository chunkRepository;
    private final DocFolderRepository folderRepository;
    private final VectorStoreManager vectorStoreManager;
    private final SettingsService settings;
    private final StorageService storage;
    private final KbAccessService accessService;

    public List<KnowledgeBaseDto> list() {
        List<KnowledgeBase> list = accessService.filter(repository.findAllByOrderByUpdatedAtDesc());
        Map<Long, String> modelNames = new HashMap<>();
        modelRepository.findAll().forEach(m -> modelNames.put(m.getId(), m.getName()));
        String backend = vectorStoreManager.activeBackend();
        return list.stream().map(kb -> toDto(kb, modelNames, backend)).toList();
    }

    public KnowledgeBase require(Long id) {
        return repository.findById(id).orElseThrow(() -> new BizException(404, "知识库不存在: " + id));
    }

    public KnowledgeBaseDto detail(Long id) {
        KnowledgeBase kb = require(id);
        accessService.assertCanView(id);
        return toDto(kb);
    }

    public KnowledgeBaseDto toDto(KnowledgeBase kb) {
        Map<Long, String> names = new HashMap<>();
        modelRepository.findAll().forEach(m -> names.put(m.getId(), m.getName()));
        return toDto(kb, names, vectorStoreManager.activeBackend());
    }

    private KnowledgeBaseDto toDto(KnowledgeBase kb, Map<Long, String> modelNames, String backend) {
        boolean restricted = !KbAccessService.isPublic(kb);
        List<String> roles = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        if (restricted && kb.getId() != null) {
            Map<String, Object> access = accessService.describe(kb.getId());
            ((List<?>) access.getOrDefault("roles", List.of())).forEach(o -> roles.add(String.valueOf(o)));
            ((List<?>) access.getOrDefault("userIds", List.of()))
                    .forEach(o -> userIds.add(((Number) o).longValue()));
        }
        return KnowledgeBaseDto.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .embeddingModelId(kb.getEmbeddingModelId())
                .embeddingModelName(kb.getEmbeddingModelId() == null ? null : modelNames.get(kb.getEmbeddingModelId()))
                .collectionName(kb.getCollectionName())
                .chunkSize(kb.getChunkSize())
                .chunkOverlap(kb.getChunkOverlap())
                .docCount(kb.getDocCount())
                .chunkCount(kb.getChunkCount())
                .mcpExposed(kb.getMcpExposed())
                .color(kb.getColor())
                .vectorBackend(backend)
                .visibility(restricted ? KbAccessService.VIS_RESTRICTED : KbAccessService.VIS_PUBLIC)
                .accessRoles(roles)
                .accessUserIds(userIds)
                .createdAt(kb.getCreatedAt())
                .updatedAt(kb.getUpdatedAt())
                .build();
    }

    @Transactional
    public KnowledgeBaseDto create(Requests.KbReq req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException("知识库名称不能为空");
        }
        String name = req.getName().trim();
        if (repository.existsByName(name)) {
            throw new BizException("知识库名称已存在: " + name);
        }
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(name);
        kb.setDescription(req.getDescription());
        kb.setEmbeddingModelId(resolveModelId(req.getEmbeddingModelId()));
        kb.setChunkSize(normalizeChunkSize(req.getChunkSize()));
        kb.setChunkOverlap(normalizeOverlap(req.getChunkOverlap(), kb.getChunkSize()));
        kb.setMcpExposed(req.getMcpExposed() == null || req.getMcpExposed());
        kb.setColor(req.getColor() == null || req.getColor().isBlank() ? "#4f6ef7" : req.getColor());
        kb.setDocCount(0);
        kb.setChunkCount(0);
        repository.save(kb);
        kb.setCollectionName("kb_" + kb.getId());
        repository.save(kb);
        // 策略依赖自增 id，因此在首次保存之后再写入授权明细
        accessService.apply(kb, req);
        repository.save(kb);

        try {
            VectorStore store = vectorStoreManager.active();
            store.ensureCollection(kb.getCollectionName());
        } catch (Exception e) {
            log.warn("初始化向量集合失败（不影响创建）: {}", e.getMessage());
        }
        return toDto(kb);
    }

    @Transactional
    public KnowledgeBaseDto update(Long id, Requests.KbReq req) {
        KnowledgeBase kb = require(id);
        accessService.assertCanView(id);
        if (req.getName() != null && !req.getName().isBlank() && !req.getName().trim().equals(kb.getName())) {
            if (repository.existsByName(req.getName().trim())) {
                throw new BizException("知识库名称已存在");
            }
            kb.setName(req.getName().trim());
        }
        if (req.getDescription() != null) {
            kb.setDescription(req.getDescription());
        }
        if (req.getEmbeddingModelId() != null) {
            kb.setEmbeddingModelId(resolveModelId(req.getEmbeddingModelId()));
        }
        if (req.getChunkSize() != null) {
            kb.setChunkSize(normalizeChunkSize(req.getChunkSize()));
        }
        if (req.getChunkOverlap() != null) {
            kb.setChunkOverlap(normalizeOverlap(req.getChunkOverlap(), kb.getChunkSize()));
        }
        if (req.getMcpExposed() != null) {
            kb.setMcpExposed(req.getMcpExposed());
        }
        if (req.getColor() != null && !req.getColor().isBlank()) {
            kb.setColor(req.getColor());
        }
        accessService.apply(kb, req);
        repository.save(kb);
        return toDto(kb);
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeBase kb = require(id);
        accessService.assertCanView(id);
        List<Document> docs = documentRepository.findByKbIdOrderByCreatedAtDesc(id);
        docs.forEach(d -> storage.delete(d.getFilePath()));
        chunkRepository.deleteByKbId(id);
        documentRepository.deleteByKbId(id);
        folderRepository.deleteByKbId(id);
        accessService.deleteByKb(id);
        repository.delete(kb);
        try {
            vectorStoreManager.active().dropCollection(kb.getCollectionName());
        } catch (Exception e) {
            log.warn("删除向量集合失败 {}: {}", kb.getCollectionName(), e.getMessage());
        }
    }

    @Transactional
    public void refreshCounts(Long kbId) {
        repository.findById(kbId).ifPresent(kb -> {
            kb.setDocCount((int) documentRepository.countByKbId(kbId));
            kb.setChunkCount((int) chunkRepository.countByKbId(kbId));
            repository.save(kb);
        });
    }

    private Long resolveModelId(Long requestId) {
        if (requestId != null) {
            ModelConfig cfg = modelRepository.findById(requestId)
                    .orElseThrow(() -> new BizException("指定的 Embedding 模型不存在"));
            if (!ModelConfig.TYPE_EMBEDDING.equals(cfg.getType())) {
                throw new BizException("所选模型不是 Embedding 类型");
            }
            return requestId;
        }
        return modelRepository.findFirstByTypeAndIsDefaultTrueAndEnabledTrue(ModelConfig.TYPE_EMBEDDING)
                .map(ModelConfig::getId)
                .orElseGet(() -> modelRepository.findByTypeAndEnabledTrueOrderByIsDefaultDescIdAsc(ModelConfig.TYPE_EMBEDDING)
                        .stream().findFirst().map(ModelConfig::getId).orElse(null));
    }

    private int normalizeChunkSize(Integer size) {
        int def = settings.getInt(SettingsService.CHUNK_SIZE, 800);
        int v = size == null ? def : size;
        return Math.max(200, Math.min(v, 4000));
    }

    private int normalizeOverlap(Integer overlap, Integer chunkSize) {
        int def = settings.getInt(SettingsService.CHUNK_OVERLAP, 120);
        int v = overlap == null ? def : overlap;
        return Math.max(0, Math.min(v, Math.max(0, chunkSize / 2)));
    }
}
