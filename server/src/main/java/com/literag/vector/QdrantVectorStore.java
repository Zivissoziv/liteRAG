package com.literag.vector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Qdrant 实现。collection 的创建延迟到首次 upsert（Qdrant 必须声明向量维度，
 * 由第一批记录的实际维度决定），查询/统计对不存在的 collection 返回空结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantVectorStore implements VectorStore {

    private final QdrantClient qdrant;

    @Override
    public String backend() {
        return "qdrant";
    }

    @Override
    public boolean available() {
        return qdrant.available();
    }

    @Override
    public String ensureCollection(String collectionName) {
        return collectionName;
    }

    @Override
    public void upsert(String collectionName, List<VectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        if (!qdrant.collectionExists(collectionName)) {
            int dim = records.get(0).getEmbedding().length;
            qdrant.createCollection(collectionName, dim);
            log.info("已在 Qdrant 创建 collection {}（维度 {}）", collectionName, dim);
        }
        qdrant.upsert(collectionName, records);
    }

    @Override
    public void deleteByDoc(String collectionName, Long docId) {
        try {
            qdrant.deleteByDoc(collectionName, docId);
        } catch (Exception e) {
            log.warn("Qdrant 按文档删除失败 {}: {}", collectionName, e.getMessage());
        }
    }

    @Override
    public void deleteByIds(String collectionName, List<String> vectorIds) {
        try {
            qdrant.deleteIds(collectionName, vectorIds);
        } catch (Exception e) {
            log.warn("Qdrant 按 id 删除失败 {}: {}", collectionName, e.getMessage());
        }
    }

    @Override
    public void dropCollection(String collectionName) {
        try {
            qdrant.dropCollection(collectionName);
        } catch (Exception e) {
            log.warn("删除 Qdrant collection 失败 {}: {}", collectionName, e.getMessage());
        }
    }

    @Override
    public List<SearchHit> search(String collectionName, float[] queryVector, int topK) {
        return qdrant.search(collectionName, queryVector, topK);
    }

    @Override
    public long count(String collectionName) {
        try {
            return qdrant.count(collectionName);
        } catch (Exception e) {
            return 0;
        }
    }
}
