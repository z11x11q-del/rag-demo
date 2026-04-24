package com.example.ragdemo.retrieval;

import com.example.ragdemo.config.RetrievalProperties;
import com.example.ragdemo.model.domain.RetrievalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * RRF（Reciprocal Rank Fusion）融合策略实现
 * <p>
 * 仅依赖排名位置，不依赖原始分数，天然适合异构通道（余弦相似度 / BM25 / 搜索排名分量纲不同）。
 * </p>
 * <p>
 * 公式：RRF_score(d) = Σ  1 / (k + rank_i(d))
 * </p>
 */
@RequiredArgsConstructor
@Component
public class RRFFusionStrategy implements FusionStrategy {

    private final RetrievalProperties retrievalProperties;

    @Override
    public List<RetrievalResult> fuse(Map<String, List<RetrievalResult>> channelResults) {
        int k = retrievalProperties.getRrfK();
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, RetrievalResult> resultMap = new LinkedHashMap<>();

        for (List<RetrievalResult> results : channelResults.values()) {
            for (int rank = 0; rank < results.size(); rank++) {
                RetrievalResult r = results.get(rank);
                // 同一文档在多个通道中出现时，分数累加
                rrfScores.merge(r.getChunkId(), 1.0 / (k + rank + 1), Double::sum);
                // 保留首次出现的完整结果对象（含 content / metadata 等字段）
                resultMap.putIfAbsent(r.getChunkId(), r);
            }
        }

        // 按 RRF 分数降序排列，并将 RRF 分数写回 score 字段
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> {
                    RetrievalResult r = resultMap.get(e.getKey());
                    r.setScore(e.getValue());
                    return r;
                })
                .toList();
    }
}
