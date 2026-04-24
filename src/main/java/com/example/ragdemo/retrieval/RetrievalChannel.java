package com.example.ragdemo.retrieval;

import com.example.ragdemo.model.domain.RetrievalResult;

import java.util.List;

/**
 * 召回通道抽象接口 — 每路数据源实现此接口
 * <p>
 * {@link DefaultRetrievalService} 通过 {@code List<RetrievalChannel>} 自动收集所有已注册通道，
 * 新增通道只需注册一个新的 {@code @Component}，无需修改主流程。
 * </p>
 */
public interface RetrievalChannel {

    /**
     * 通道名称，用于日志和指标标识（如 "dense"、"sparse"、"web"）
     */
    String channelName();

    /**
     * 执行召回，返回候选结果列表（按相关性降序）
     *
     * @param query 预处理后的查询文本
     * @param topK  本通道返回的最大候选数
     * @return 候选结果列表
     */
    List<RetrievalResult> retrieve(String query, int topK);
}
