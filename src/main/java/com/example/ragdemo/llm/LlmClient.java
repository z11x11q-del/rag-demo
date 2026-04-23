package com.example.ragdemo.llm;

/**
 * LLM 客户端接口 — 统一 chat/completion 调用方式，屏蔽云端/本地差异
 */
public interface LlmClient {

    /**
     * 同步调用 LLM 生成回答
     *
     * @param prompt 完整 Prompt
     * @return LLM 生成的回答文本
     */
    String chat(String prompt);

    /**
     * 流式调用 LLM（SSE），返回流式迭代器
     * <p>MVP 阶段可只实现同步版本，流式版本后续扩展</p>
     *
     * @param prompt 完整 Prompt
     * @return 回答片段的迭代器
     */
    Iterable<String> chatStream(String prompt);

    /**
     * 返回当前使用的模型名称
     */
    String modelName();
}
