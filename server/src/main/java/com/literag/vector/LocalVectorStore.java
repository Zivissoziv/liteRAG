package com.literag.vector;

import com.literag.entity.DocChunk;
import com.literag.repository.DocChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 本地向量库：向量以 BLOB 形式随分块一起落库，检索时做精确余弦扫描。
 * 用于 ChromaDB 未部署时的零依赖运行，也方便离线演示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalVectorStore implements VectorStore {

    private final DocChunkRepository chunkRepo;

    @Override
    public String backend() {
        return "local";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String ensureCollection(String collectionName) {
        return collectionName;
    }

    @Override
    public void upsert(String collectionName, List<VectorRecord> records) {
        // 向量随 DocChunk 一起写入数据库，此处无需额外动作
    }

    @Override
    @Transactional
    public void deleteByDoc(String collectionName, Long docId) {
        chunkRepo.deleteByDocId(docId);
    }

    @Override
    @Transactional
    public void deleteByIds(String collectionName, List<String> vectorIds) {
        if (vectorIds != null && !vectorIds.isEmpty()) {
            chunkRepo.deleteByVectorIdIn(vectorIds);
        }
    }

    @Override
    @Transactional
    public void dropCollection(String collectionName) {
        Long kbId = parseKbId(collectionName);
        if (kbId != null) {
            chunkRepo.deleteByKbId(kbId);
        }
    }

    @Override
    public List<SearchHit> search(String collectionName, float[] queryVector, int topK) {
        Long kbId = parseKbId(collectionName);
        if (kbId == null || queryVector == null) {
            return List.of();
        }
        List<DocChunk> chunks = chunkRepo.findIndexedByKbIds(List.of(kbId));
        List<SearchHit> hits = new ArrayList<>();
        for (DocChunk c : chunks) {
            float[] vec = VectorUtils.toFloats(c.getEmbedding());
            if (vec == null || vec.length != queryVector.length) {
                continue;
            }
            double score = VectorUtils.cosine(queryVector, vec);
            if (score <= 0) {
                continue;
            }
            hits.add(SearchHit.builder()
                    .vectorId(c.getVectorId())
                    .docId(c.getDocId())
                    .kbId(c.getKbId())
                    .seq(c.getSeq())
                    .title(c.getDocTitle())
                    .content(c.getContent())
                    .score(Math.min(1, score))
                    .backend("local")
                    .build());
        }
        hits.sort(Comparator.comparingDouble(SearchHit::getScore).reversed());
        return hits.size() > topK ? new ArrayList<>(hits.subList(0, topK)) : hits;
    }

    @Override
    public long count(String collectionName) {
        Long kbId = parseKbId(collectionName);
        return kbId == null ? 0 : chunkRepo.countByKbId(kbId);
    }

    private Long parseKbId(String collectionName) {
        try {
            return Long.parseLong(collectionName.replace("kb_", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
