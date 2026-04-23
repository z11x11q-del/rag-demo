package com.example.ragdemo.retrieval;

import com.example.ragdemo.embedding.EmbeddingClient;
import com.example.ragdemo.model.domain.RetrievalResult;
import com.example.ragdemo.store.BM25Store;
import com.example.ragdemo.store.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检索服务默认实现 — 多路召回 + 融合 + 重排
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DefaultRetrievalService implements RetrievalService {

    private final VectorStore vectorStore;
    private final BM25Store bm25Store;
    private final EmbeddingClient embeddingClient;
    private final Reranker reranker;
    private final ContextBuilder contextBuilder;

    @Override
    public List<RetrievalResult> retrieve(String query, int topK, int topN) {
        // TODO: 实现多路召回 + RRF 融合 + 重排
        // 1. Dense Retrieval: 向量相似度 TopK
        // 2. Sparse Retrieval: BM25 TopK
        // 3. RRF 融合
        // 4. Rerank TopN
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public String buildContext(List<RetrievalResult> results) {
        return contextBuilder.build(results);
    }
}
