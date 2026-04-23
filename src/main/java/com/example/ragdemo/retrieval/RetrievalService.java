package com.example.ragdemo.retrieval;

import com.example.ragdemo.model.domain.RetrievalResult;

import java.util.List;

/**
 * 检索服务接口 — 多路召回 + 融合 + 重排 + 上下文构造
 */
public interface RetrievalService {

    /**
     * 执行多路召回 + 重排，返回最终检索结果
     *
     * @param query 用户查询
     * @param topK  向量召回数量
     * @param topN  重排后保留数量
     * @return 重排后的检索结果列表
     */
    List<RetrievalResult> retrieve(String query, int topK, int topN);

    /**
     * 构建上下文（给 LLM 使用）
     *
     * @param results 检索结果
     * @return 格式化后的上下文
     */
    String buildContext(List<RetrievalResult> results);
}
