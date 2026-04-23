package com.example.ragdemo.retrieval;

import com.example.ragdemo.model.domain.RetrievalResult;

import java.util.List;

/**
 * 上下文构造接口 — 将重排后的文档组织为 LLM 可消费的上下文
 */
public interface ContextBuilder {

    /**
     * 构建上下文文本
     *
     * @param results 重排后的检索结果
     * @return 格式化后的上下文字符串（包含编号、来源、内容）
     */
    String build(List<RetrievalResult> results);
}
