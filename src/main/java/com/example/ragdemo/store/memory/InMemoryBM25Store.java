package com.example.ragdemo.store.memory;

import com.example.ragdemo.model.domain.RetrievalResult;
import com.example.ragdemo.store.BM25Store;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现的 BM25 倒排索引 — 当 Elasticsearch 未启用时使用
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryBM25Store implements BM25Store {

    private final Map<String, String> indexedDocs = new ConcurrentHashMap<>();

    @Override
    public void index(String id, String content) {
        indexedDocs.put(id, content);
        log.debug("Indexed BM25 doc: {}", id);
    }

    @Override
    public void indexBatch(List<String> ids, List<String> contents) {
        for (int i = 0; i < ids.size(); i++) {
            index(ids.get(i), contents.get(i));
        }
    }

    @Override
    public List<RetrievalResult> search(String query, int topK) {
        // TODO: 实现 BM25 检索
        log.warn("InMemoryBM25Store.search is a stub, returning empty results");
        return Collections.emptyList();
    }

    @Override
    public void delete(List<String> ids) {
        ids.forEach(indexedDocs::remove);
        log.debug("Deleted {} BM25 docs", ids.size());
    }
}
