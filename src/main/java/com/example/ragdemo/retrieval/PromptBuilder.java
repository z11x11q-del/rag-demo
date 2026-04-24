package com.example.ragdemo.retrieval;

/**
 * Prompt 构建接口 — 将系统指令、上下文、用户查询拆分为独立角色消息
 */
public interface PromptBuilder {

    /**
     * System Prompt 与 User Message 的结构化载体
     *
     * @param systemPrompt 系统角色指令（角色定义、约束规则）
     * @param userMessage  用户消息（参考文档 + 用户问题）
     */
    record Prompt(String systemPrompt, String userMessage) {}

    /**
     * 构建结构化 Prompt，分离 System / User 两个角色
     *
     * @param context 检索上下文（ContextBuilder 输出）
     * @param query   用户查询（改写后）
     * @param intent  用户意图（qa / summary / comparison 等）
     * @return 包含 systemPrompt 和 userMessage 的 Prompt 对象
     */
    Prompt build(String context, String query, String intent);
}
