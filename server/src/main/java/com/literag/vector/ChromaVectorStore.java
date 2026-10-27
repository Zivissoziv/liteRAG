package com.literag.vector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChromaVectorStore implements VectorStore {

    private final ChromaClient chroma;
    private final Map<String, String> collectionIds = new ConcurrentHashMap<>();

    @Override
    public String backend() {
        return "chroma";
    }

    @Override
    public boolean available() {
        return chroma.available();
    }

    @Override
    public String ensureCollection(String collectionName) {
        return collectionIds.computeIfAbsent(collectionName, chroma::ensureCollection);
    }

    @Override
    public void upsert(String collectionName, List<VectorRecord> records) {
        chroma.add(ensureCollection(collectionName), records);
    }

    @Override
    public void deleteByDoc(String collectionName, Long docId) {
        chroma.deleteWhereDoc(ensureCollection(collectionName), docId);
    }

    @Override
    public void deleteByIds(String collectionName, List<String> vectorIds) {
        chroma.deleteIds(ensureCollection(collectionName), vectorIds);
    }

    @Override
    public void dropCollection(String collectionName) {
        String id = collectionIds.remove(collectionName);
        try {
            chroma.dropCollection(id != null ? id : chroma.ensureCollection(collectionName));
        } catch (Exception e) {
            log.warn("删除 ChromaDB collection 失败 {}: {}", collectionName, e.getMessage());
        }
    }

    @Override
    public List<SearchHit> search(String collectionName, float[] queryVector, int topK) {
        return chroma.query(ensureCollection(collectionName), queryVector, topK);
    }

    @Override
    public long count(String collectionName) {
        try {
            return chroma.count(ensureCollection(collectionName));
        } catch (Exception e) {
            return 0;
        }
    }
}
