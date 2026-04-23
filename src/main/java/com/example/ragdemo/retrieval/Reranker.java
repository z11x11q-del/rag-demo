package com.example.ragdemo.retrieval;

import com.example.ragdemo.model.domain.RetrievalResult;

import java.util.List;

/**
 * 重排接口 — 对召回候选集进行精排
 */
public interface Reranker {

    /**
     * 对候选结果进行重排序
     *
     * @param query      用户查询
     * @param candidates 召回候选列表
     * @param topN       保留 TopN 结果
     * @return 重排后的结果（按相关性降序，截取 TopN）
     */
    List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topN);
}
