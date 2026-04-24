package com.example.ragdemo.retrieval;

import com.example.ragdemo.embedding.EmbeddingClient;
import com.example.ragdemo.model.domain.RetrievalResult;
import com.example.ragdemo.store.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 稠密向量召回通道 — 基于 Embedding + VectorStore
 * <p>
 * 通过 {@code rag.retrieval.dense-enabled=false} 可在配置中关闭此通道。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "rag.retrieval.dense-enabled", havingValue = "true", matchIfMissing = true)
public class DenseRetrievalChannel implements RetrievalChannel {

    private final VectorStore vectorStore;
    private final EmbeddingClient embeddingClient;

    @Override
    public String channelName() {
        return "dense";
    }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) {
        float[] queryVector = embeddingClient.embed(query);
        log.debug("Dense 召回: topK={}", topK);
        return vectorStore.search(queryVector, topK);
    }
}
