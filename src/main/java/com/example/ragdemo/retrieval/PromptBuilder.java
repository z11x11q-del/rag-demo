package com.example.ragdemo.retrieval;

/**
 * Prompt 构建接口 — 组装系统指令 + 上下文 + 用户查询
 */
public interface PromptBuilder {

    /**
     * 构建最终发送给 LLM 的 Prompt
     *
     * @param context 检索上下文
     * @param query   用户查询
     * @param intent  用户意图（qa / summary / comparison 等）
     * @return 完整 Prompt 文本
     */
    String build(String context, String query, String intent);
}
