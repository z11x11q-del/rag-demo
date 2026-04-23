package com.example.ragdemo.retrieval;

/**
 * Query 预处理接口 — 清洗、改写、意图识别
 */
public interface QueryPreprocessor {

    /**
     * 对原始查询进行预处理
     *
     * @param rawQuery 用户原始查询
     * @return 预处理后的查询
     */
    ProcessedQuery process(String rawQuery);

    /**
     * 预处理结果
     */
    record ProcessedQuery(
            /** 清洗/改写后的查询文本 */
            String rewrittenQuery,
            /** 识别到的意图（qa / summary / comparison 等） */
            String intent
    ) {}
}
