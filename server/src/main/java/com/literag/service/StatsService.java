package com.literag.service;

import com.literag.dto.DocumentDto;
import com.literag.entity.Document;
import com.literag.repository.DocChunkRepository;
import com.literag.repository.DocumentRepository;
import com.literag.repository.KnowledgeBaseRepository;
import com.literag.repository.ModelConfigRepository;
import com.literag.vector.VectorStoreManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final KnowledgeBaseRepository kbRepository;
    private final DocumentRepository documentRepository;
    private final DocChunkRepository chunkRepository;
    private final ModelConfigRepository modelRepository;
    private final VectorStoreManager vectorStoreManager;
    private final KnowledgeBaseService kbService;
    private final DocumentService documentService;
    private final KbAccessService accessService;

    /** 概览只统计当前用户有权查看的知识库，避免通过计数与最近文档反推受限内容 */
    public Map<String, Object> overview() {
        Map<String, Object> map = new LinkedHashMap<>();
        Set<Long> visible = accessService.visibleKbIds();
        List<Long> ids = visible == null ? null : new ArrayList<>(visible);

        if (ids == null) {
            map.put("kbCount", kbRepository.count());
            map.put("docCount", documentRepository.count());
            map.put("readyCount", documentRepository.countByStatus(Document.STATUS_READY));
            map.put("processingCount", documentRepository.countByStatus(Document.STATUS_PENDING)
                    + documentRepository.countByStatus(Document.STATUS_PARSING)
                    + documentRepository.countByStatus(Document.STATUS_EMBEDDING));
            map.put("failedCount", documentRepository.countByStatus(Document.STATUS_FAILED));
            map.put("chunkCount", chunkRepository.count());
            map.put("recentDocuments", recentDocuments(documentRepository.findTop10ByOrderByCreatedAtDesc()));
        } else {
            map.put("kbCount", ids.size());
            if (ids.isEmpty()) {
                map.put("docCount", 0L);
                map.put("readyCount", 0L);
                map.put("processingCount", 0L);
                map.put("failedCount", 0L);
                map.put("chunkCount", 0L);
                map.put("recentDocuments", List.of());
            } else {
                map.put("docCount", documentRepository.countByKbIdIn(ids));
                map.put("readyCount", documentRepository.countByKbIdInAndStatus(ids, Document.STATUS_READY));
                map.put("processingCount",
                        documentRepository.countByKbIdInAndStatus(ids, Document.STATUS_PENDING)
                                + documentRepository.countByKbIdInAndStatus(ids, Document.STATUS_PARSING)
                                + documentRepository.countByKbIdInAndStatus(ids, Document.STATUS_EMBEDDING));
                map.put("failedCount", documentRepository.countByKbIdInAndStatus(ids, Document.STATUS_FAILED));
                map.put("chunkCount", chunkRepository.countByKbIdIn(ids));
                map.put("recentDocuments", recentDocuments(
                        documentRepository.findTop10ByKbIdInOrderByCreatedAtDesc(ids)));
            }
        }

        map.put("modelCount", modelRepository.count());
        map.put("vector", vectorStoreManager.status());
        map.put("knowledgeBases", kbService.list());
        return map;
    }

    private List<DocumentDto> recentDocuments(List<Document> docs) {
        return docs.stream().map(d -> documentService.toDto(d, false)).toList();
    }
}
