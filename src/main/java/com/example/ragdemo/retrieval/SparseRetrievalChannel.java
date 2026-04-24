package com.example.ragdemo.retrieval;

import com.example.ragdemo.model.domain.RetrievalResult;
import com.example.ragdemo.store.BM25Store;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 稀疏 BM25 召回通道 — 基于关键词倒排索引
 * <p>
 * 通过 {@code rag.retrieval.sparse-enabled=false} 可在配置中关闭此通道。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "rag.retrieval.sparse-enabled", havingValue = "true", matchIfMissing = true)
public class SparseRetrievalChannel implements RetrievalChannel {

    private final BM25Store bm25Store;

    @Override
    public String channelName() {
        return "sparse";
    }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) {
        log.debug("Sparse 召回: topK={}", topK);
        return bm25Store.search(query, topK);
    }
}
