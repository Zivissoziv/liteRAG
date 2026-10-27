package com.literag.service;

import com.literag.ai.EmbeddingService;
import com.literag.common.BizException;
import com.literag.dto.Views;
import com.literag.entity.Document;
import com.literag.entity.KnowledgeBase;
import com.literag.entity.ModelConfig;
import com.literag.repository.DocumentRepository;
import com.literag.repository.KnowledgeBaseRepository;
import com.literag.vector.SearchHit;
import com.literag.vector.VectorStore;
import com.literag.vector.VectorStoreManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeBaseService kbService;
    private final ModelConfigService modelConfigService;
    private final EmbeddingService embeddingService;
    private final VectorStoreManager vectorStoreManager;
    private final DocumentRepository documentRepository;
    private final SettingsService settings;
    private final KbAccessService accessService;

    /** 只在当前用户可见的知识库里检索，越权库与不存在的库给出相同提示 */
    public List<KnowledgeBase> resolveKbs(List<Long> kbIds) {
        List<KnowledgeBase> all = accessService.filter(kbRepository.findAllByOrderByUpdatedAtDesc());
        if (kbIds == null || kbIds.isEmpty()) {
            if (all.isEmpty()) {
                throw new BizException(404, "没有可检索的知识库");
            }
            return all;
        }
        Set<Long> wanted = new LinkedHashSet<>(kbIds);
        List<KnowledgeBase> picked = all.stream().filter(kb -> wanted.contains(kb.getId())).toList();
        if (picked.isEmpty()) {
            throw new BizException(404, "指定的知识库不存在或没有访问权限");
        }
        return picked;
    }

    public Views.SearchResult search(List<Long> kbIds, String query, Integer topK, Double minScore, Long docId) {
        long start = System.currentTimeMillis();
        if (query == null || query.isBlank()) {
            throw new BizException("查询内容不能为空");
        }
        int k = topK == null || topK <= 0 ? settings.getInt(SettingsService.DEFAULT_TOP_K, 6) : Math.min(topK, 50);
        double min = minScore == null ? settings.getDouble(SettingsService.MIN_SCORE, 0) : minScore;

        List<KnowledgeBase> kbs = resolveKbs(kbIds);
        VectorStore store = vectorStoreManager.active();
        List<SearchHit> hits = new ArrayList<>();
        Map<Long, String> modelNames = new HashMap<>();
        Map<Long, float[]> queryVectors = new HashMap<>();

        for (KnowledgeBase kb : kbs) {
            if (kb.getCollectionName() == null || kb.getCollectionName().isBlank()) {
                continue;
            }
            try {
                ModelConfig model = modelConfigService.requireEmbeddingFor(kb);
                modelNames.put(kb.getId(), model.getName());
                float[] qv = queryVectors.computeIfAbsent(model.getId(),
                        id -> embeddingService.embed(model, List.of(query)).get(0));
                List<SearchHit> kbHits = store.search(kb.getCollectionName(), qv, Math.max(k * 3, 15));
                for (SearchHit hit : kbHits) {
                    if (hit.getScore() < min) {
                        continue;
                    }
                    if (docId != null && !docId.equals(hit.getDocId())) {
                        continue;
                    }
                    hit.setKbId(kb.getId());
                    hits.add(hit);
                }
            } catch (Exception e) {
                log.warn("知识库 {} 检索失败: {}", kb.getName(), e.getMessage());
            }
        }

        Map<String, SearchHit> dedup = new LinkedHashMap<>();
        hits.forEach(h -> dedup.putIfAbsent(h.getVectorId(), h));
        List<SearchHit> sorted = new ArrayList<>(dedup.values());
        sorted.sort(Comparator.comparingDouble(SearchHit::getScore).reversed());
        if (sorted.size() > k) {
            sorted = new ArrayList<>(sorted.subList(0, k));
        }

        List<Views.ChunkHit> views = toViews(sorted, kbs, modelNames);
        return Views.SearchResult.builder()
                .query(query)
                .backend(store.backend())
                .tookMs(System.currentTimeMillis() - start)
                .total(views.size())
                .hits(views)
                .build();
    }

    public List<Views.ChunkHit> toViews(List<SearchHit> hits, List<KnowledgeBase> kbs, Map<Long, String> modelNames) {
        Map<Long, Document> docs = new HashMap<>();
        List<Long> docIds = hits.stream().map(SearchHit::getDocId).filter(java.util.Objects::nonNull).distinct().toList();
        if (!docIds.isEmpty()) {
            documentRepository.findAllById(docIds).forEach(d -> docs.put(d.getId(), d));
        }
        Map<Long, String> kbNames = new HashMap<>();
        kbs.forEach(kb -> kbNames.put(kb.getId(), kb.getName()));

        List<Views.ChunkHit> views = new ArrayList<>(hits.size());
        for (SearchHit hit : hits) {
            Document doc = docs.get(hit.getDocId());
            views.add(Views.ChunkHit.builder()
                    .vectorId(hit.getVectorId())
                    .docId(hit.getDocId())
                    .docName(doc == null ? hit.getTitle() : doc.getName())
                    .kbId(hit.getKbId())
                    .kbName(kbNames.get(hit.getKbId()))
                    .seq(hit.getSeq())
                    .title(hit.getTitle())
                    .content(hit.getContent())
                    .score(Math.round(hit.getScore() * 10000) / 10000.0)
                    .build());
        }
        return views;
    }
}
