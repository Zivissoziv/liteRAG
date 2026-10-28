package com.literag.vector;

import java.util.List;

/**
 * 向量存储抽象。默认由 Qdrant 实现，Qdrant 不可用时回退到本地实现。
 */
public interface VectorStore {

    /** qdrant | local */
    String backend();

    boolean available();

    /** 创建（或获取）collection，返回 collection 标识 */
    String ensureCollection(String collectionName);

    void upsert(String collectionName, List<VectorRecord> records);

    void deleteByDoc(String collectionName, Long docId);

    void deleteByIds(String collectionName, List<String> vectorIds);

    void dropCollection(String collectionName);

    List<SearchHit> search(String collectionName, float[] queryVector, int topK);

    long count(String collectionName);
}
