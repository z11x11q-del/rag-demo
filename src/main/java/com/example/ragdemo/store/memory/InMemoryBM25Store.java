package com.example.ragdemo.store.memory;

import com.example.ragdemo.model.domain.RetrievalResult;
import com.example.ragdemo.store.BM25Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现的 BM25 倒排索引 — MVP 阶段使用，后续替换为 ElasticSearch
 */
@Component
public class InMemoryBM25Store implements BM25Store {

    private static final Logger log = LoggerFactory.getLogger(InMemoryBM25Store.class);

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
