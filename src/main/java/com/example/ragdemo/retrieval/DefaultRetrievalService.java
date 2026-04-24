package com.example.ragdemo.retrieval;

import com.example.ragdemo.model.domain.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 检索服务默认实现 — 多路并行召回 + RRF 融合 + 重排
 * <p>
 * 通过 {@code List<RetrievalChannel>} 自动收集所有已注册通道（Dense / Sparse / ...），
 * 新增通道只需注册一个新的 {@code @Component}，无需修改此类。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DefaultRetrievalService implements RetrievalService {

    private final List<RetrievalChannel> channels;
    private final FusionStrategy fusionStrategy;
    private final Reranker reranker;
    private final ContextBuilder contextBuilder;

    @Override
    public List<RetrievalResult> retrieve(String query, int topK, int topN) {
        log.debug("多路召回开始: topK={}, topN={}, channels={}",
                topK, topN, channels.stream().map(RetrievalChannel::channelName).toList());

        // 1. 逐通道召回（通道失败不影响其他通道）
        Map<String, List<RetrievalResult>> channelResults = new LinkedHashMap<>();
        for (RetrievalChannel channel : channels) {
            try {
                List<RetrievalResult> results = channel.retrieve(query, topK);
                if (!results.isEmpty()) {
                    channelResults.put(channel.channelName(), results);
                    log.debug("通道 [{}] 召回 {} 条", channel.channelName(), results.size());
                } else {
                    log.debug("通道 [{}] 召回结果为空", channel.channelName());
                }
            } catch (Exception e) {
                log.warn("通道 [{}] 召回异常，已跳过: {}", channel.channelName(), e.getMessage());
            }
        }

        if (channelResults.isEmpty()) {
            log.warn("所有召回通道均无结果，返回空列表");
            return Collections.emptyList();
        }

        // 2. 融合：单通道时跳过 RRF，直接使用该通道结果
        List<RetrievalResult> fused;
        if (channelResults.size() == 1) {
            fused = channelResults.values().iterator().next();
            log.debug("单通道模式，跳过 RRF 融合");
        } else {
            fused = fusionStrategy.fuse(channelResults);
            log.debug("RRF 融合后候选数: {}", fused.size());
        }

        // 3. 重排 + 截取 TopN
        List<RetrievalResult> ranked = reranker.rerank(query, fused, topN);
        log.debug("重排后结果数: {}", ranked.size());
        return ranked;
    }

    @Override
    public String buildContext(List<RetrievalResult> results) {
        return contextBuilder.build(results);
    }
}
