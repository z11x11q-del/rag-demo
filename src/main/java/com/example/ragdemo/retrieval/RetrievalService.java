package com.example.ragdemo.retrieval;

import com.example.ragdemo.embedding.EmbeddingClient;
import com.example.ragdemo.model.domain.RetrievalResult;
import com.example.ragdemo.store.BM25Store;
import com.example.ragdemo.store.VectorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索服务 — 多路召回 + 融合 + 重排
 */
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final VectorStore vectorStore;
    private final BM25Store bm25Store;
    private final EmbeddingClient embeddingClient;
    private final Reranker reranker;
    private final ContextBuilder contextBuilder;

    /**
     * 执行多路召回 + 重排，返回最终检索结果
     *
     * @param query 用户查询
     * @param topK  向量召回数量
     * @param topN  重排后保留数量
     * @return 重排后的检索结果列表
     */
    public List<RetrievalResult> retrieve(String query, int topK, int topN) {
        // TODO: 实现多路召回 + RRF 融合 + 重排
        // 1. Dense Retrieval: 向量相似度 TopK
        // 2. Sparse Retrieval: BM25 TopK
        // 3. RRF 融合
        // 4. Rerank TopN
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * 构建上下文（给 LLM 使用）
     *
     * @param results 检索结果
     * @return 格式化后的上下文
     */
    public String buildContext(List<RetrievalResult> results) {
        return contextBuilder.build(results);
    }
}
