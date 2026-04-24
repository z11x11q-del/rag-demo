package com.example.ragdemo.retrieval;

import com.example.ragdemo.model.domain.RetrievalResult;

import java.util.List;
import java.util.Map;

/**
 * 多路召回结果融合策略接口
 * <p>
 * 接收各通道的召回结果，输出合并去重后的单一有序候选列表，
 * 供后续 {@link Reranker} 精排。
 * </p>
 */
public interface FusionStrategy {

    /**
     * 将多个通道的召回结果融合为单一排序列表
     *
     * @param channelResults key=通道名, value=该通道的召回结果（已按相关性降序排列）
     * @return 融合后按融合分数降序排列的候选列表
     */
    List<RetrievalResult> fuse(Map<String, List<RetrievalResult>> channelResults);
}
