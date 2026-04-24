package com.example.ragdemo.retrieval;

import com.example.ragdemo.model.domain.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 截断重排器 — MVP 阶段使用
 * <p>
 * 直接按融合分数截取 TopN，不调用任何外部模型。
 * V1 阶段替换为 Cross-Encoder（DashScope Reranker API）。
 * </p>
 */
@Slf4j
@Component
public class TruncationReranker implements Reranker {

    @Override
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topN) {
        log.debug("截断重排: candidates={}, topN={}", candidates.size(), topN);
        return candidates.stream().limit(topN).toList();
    }
}
