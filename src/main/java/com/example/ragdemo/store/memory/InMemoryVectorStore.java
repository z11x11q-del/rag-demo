package com.example.ragdemo.store.memory;

import com.example.ragdemo.model.domain.RetrievalResult;
import com.example.ragdemo.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现的向量存储 — MVP 阶段使用，后续替换为 Milvus / Qdrant / pgvector
 */
@Component
@ConditionalOnMissingBean(VectorStore.class)
public class InMemoryVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    private final Map<String, float[]> vectors = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> metadatas = new ConcurrentHashMap<>();

    @Override
    public void upsert(String id, float[] vector, Map<String, Object> metadata) {
        vectors.put(id, vector);
        if (metadata != null) {
            metadatas.put(id, metadata);
        }
        log.debug("Upserted vector: {}", id);
    }

    @Override
    public void upsertBatch(List<String> ids, List<float[]> vectorList, List<Map<String, Object>> metadataList) {
        for (int i = 0; i < ids.size(); i++) {
            upsert(ids.get(i), vectorList.get(i), metadataList != null ? metadataList.get(i) : null);
        }
    }

    @Override
    public List<RetrievalResult> search(float[] queryVector, int topK) {
        // TODO: 实现余弦相似度计算
        log.warn("InMemoryVectorStore.search is a stub, returning empty results");
        return Collections.emptyList();
    }

    @Override
    public void delete(List<String> ids) {
        ids.forEach(id -> {
            vectors.remove(id);
            metadatas.remove(id);
        });
        log.debug("Deleted {} vectors", ids.size());
    }
}
