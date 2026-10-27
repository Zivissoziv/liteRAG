package com.literag.service;

import com.literag.ai.EmbeddingService;
import com.literag.common.BizException;
import com.literag.entity.DocChunk;
import com.literag.entity.Document;
import com.literag.entity.KnowledgeBase;
import com.literag.entity.ModelConfig;
import com.literag.parser.ParserRegistry;
import com.literag.repository.DocChunkRepository;
import com.literag.repository.DocumentRepository;
import com.literag.repository.KnowledgeBaseRepository;
import com.literag.vector.VectorRecord;
import com.literag.vector.VectorStore;
import com.literag.vector.VectorStoreManager;
import com.literag.vector.VectorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文档入库流水线：解析 → 分块 → 向量化 → 写入向量库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {

    private static final int MAX_CHUNKS = 20000;

    private final DocumentRepository docRepo;
    private final DocChunkRepository chunkRepo;
    private final KnowledgeBaseRepository kbRepo;
    private final ModelConfigService modelConfigService;
    private final EmbeddingService embeddingService;
    private final TextChunker chunker;
    private final ParserRegistry parserRegistry;
    private final VectorStoreManager vectorStoreManager;
    private final KnowledgeBaseService kbService;
    private final StorageService storage;

    /** 按文档的分块策略切分；AUTO 按文档类型路由：xlsx/csv→表格，md/pptx/wiki→标题，其余→固定长度 */
    private List<String> splitByStrategy(Document doc, String content, int size, int overlap) {
        String strategy = strategyOf(doc);
        return switch (strategy) {
            case Document.STRATEGY_HEADING -> chunker.splitByHeading(content, size, overlap);
            case Document.STRATEGY_TABLE -> {
                List<String> pieces = chunker.splitByTable(content, size);
                yield pieces != null ? pieces : chunker.split(content, size, overlap);
            }
            case Document.STRATEGY_FIXED -> chunker.split(content, size, overlap);
            default -> autoRoute(doc, content, size, overlap);
        };
    }

    /** 归一化文档的策略字段（历史数据为空时按 AUTO） */
    private String strategyOf(Document doc) {
        return DocumentService.normalizeStrategy(doc.getChunkStrategy());
    }

    private List<String> autoRoute(Document doc, String content, int size, int overlap) {
        String ext = doc.getFileExt() == null ? "" : doc.getFileExt().toLowerCase();
        if (Document.TYPE_WIKI.equals(doc.getSourceType()) || ext.equals("md") || ext.equals("markdown") || ext.equals("pptx")) {
            return chunker.splitByHeading(content, size, overlap);
        }
        if (ext.equals("xlsx") || ext.equals("xls") || ext.equals("csv")) {
            List<String> pieces = chunker.splitByTable(content, size);
            if (pieces != null) {
                return pieces;
            }
        }
        return chunker.split(content, size, overlap);
    }

    @Async("ingestExecutor")
    public void ingestAsync(Long docId) {
        try {
            ingest(docId);
        } catch (Exception e) {
            log.error("文档入库失败 docId={}", docId, e);
        }
    }

    public void ingest(Long docId) {
        Document doc = docRepo.findById(docId).orElseThrow(() -> new BizException(404, "文档不存在"));
        KnowledgeBase kb = kbRepo.findById(doc.getKbId())
                .orElseThrow(() -> new BizException(404, "知识库不存在"));
        if (kb.getCollectionName() == null || kb.getCollectionName().isBlank()) {
            kb.setCollectionName("kb_" + kb.getId());
            kbRepo.save(kb);
        }
        try {
            doc.setStatus(Document.STATUS_PARSING);
            doc.setErrorMsg(null);
            docRepo.save(doc);

            String content = doc.getContent();
            boolean needParse = !Document.TYPE_WIKI.equals(doc.getSourceType())
                    || content == null || content.isBlank();
            if (needParse) {
                Path file = storage.resolve(doc.getFilePath());
                content = parserRegistry.parse(file, doc.getFileExt());
                doc.setContent(content);
            }
            doc.setCharCount(content == null ? 0 : content.length());
            doc.setStatus(Document.STATUS_EMBEDDING);
            docRepo.save(doc);

            // 清理旧数据，保证重复入库幂等
            chunkRepo.deleteByDocId(docId);
            VectorStore store = vectorStoreManager.active();
            try {
                store.deleteByDoc(kb.getCollectionName(), docId);
            } catch (Exception e) {
                log.warn("清理旧向量失败: {}", e.getMessage());
            }

            ModelConfig model = modelConfigService.requireEmbeddingFor(kb);
            int chunkSize = kb.getChunkSize() == null ? 800 : kb.getChunkSize();
            int chunkOverlap = kb.getChunkOverlap() == null ? 120 : kb.getChunkOverlap();
            List<String> pieces = splitByStrategy(doc, content, chunkSize, chunkOverlap);
            if (pieces.isEmpty()) {
                throw new BizException("未能从文档中切分出有效内容");
            }
            if (pieces.size() > MAX_CHUNKS) {
                throw new BizException("分块数量 " + pieces.size() + " 超过上限 " + MAX_CHUNKS
                        + "，请拆分文档或调大分块长度");
            }

            int batchSize = model.getBatchSize() == null || model.getBatchSize() <= 0 ? 16 : Math.min(model.getBatchSize(), 64);
            int seq = 0;
            for (int start = 0; start < pieces.size(); start += batchSize) {
                List<String> sub = pieces.subList(start, Math.min(pieces.size(), start + batchSize));
                List<float[]> vectors = embeddingService.embed(model, sub);
                List<DocChunk> entities = new ArrayList<>(sub.size());
                List<VectorRecord> records = new ArrayList<>(sub.size());
                for (int i = 0; i < sub.size(); i++) {
                    float[] vec = vectors.get(i);
                    DocChunk chunk = new DocChunk();
                    chunk.setVectorId(UUID.randomUUID().toString());
                    chunk.setKbId(kb.getId());
                    chunk.setDocId(docId);
                    chunk.setDocTitle(doc.getTitle() == null ? doc.getName() : doc.getTitle());
                    chunk.setSeq(seq++);
                    chunk.setContent(sub.get(i));
                    chunk.setCharCount(sub.get(i).length());
                    chunk.setEmbedding(VectorUtils.toBytes(vec));
                    chunk.setDim(vec.length);
                    entities.add(chunk);

                    records.add(VectorRecord.builder()
                            .id(chunk.getVectorId())
                            .docId(docId)
                            .kbId(kb.getId())
                            .seq(chunk.getSeq())
                            .title(chunk.getDocTitle())
                            .content(chunk.getContent())
                            .embedding(vec)
                            .build());
                }
                chunkRepo.saveAll(entities);
                store.upsert(kb.getCollectionName(), records);
            }

            doc.setChunkCount(pieces.size());
            doc.setChunkStrategy(strategyOf(doc));
            doc.setStatus(Document.STATUS_READY);
            doc.setErrorMsg(null);
            docRepo.save(doc);
            kbService.refreshCounts(kb.getId());
            log.info("文档入库完成 docId={} name={} chunks={} model={}",
                    docId, doc.getName(), pieces.size(), model.getName());
        } catch (Exception e) {
            doc.setStatus(Document.STATUS_FAILED);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            doc.setErrorMsg(msg.length() > 1000 ? msg.substring(0, 1000) : msg);
            docRepo.save(doc);
            throw e instanceof BizException ? (BizException) e : new BizException("入库失败: " + msg);
        }
    }
}
