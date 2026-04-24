package com.example.ragdemo.retrieval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Prompt 构建默认实现 — 硬编码 QA 模板，将系统指令、上下文、用户问题拼装为完整 Prompt
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DefaultPromptBuilder implements PromptBuilder {

    private static final String QA_SYSTEM_PROMPT = """
            你是一个专业的知识库问答助手。请严格基于以下参考文档回答用户问题。

            规则：
            1. 只使用参考文档中的信息回答，不要编造内容
            2. 如果参考文档中没有相关信息，请明确告知"根据已有资料，暂时无法回答该问题"
            3. 在回答中引用来源，使用 [编号] 格式标注（如 [1]、[2]）
            4. 回答应简洁、准确、有条理
            """;

    @Override
    public Prompt build(String context, String query, String intent) {
        String userMessage = assembleUserMessage(context, query);
        log.debug("Prompt 构建完成: intent={}, userMessageLength={}", intent, userMessage.length());
        return new Prompt(QA_SYSTEM_PROMPT, userMessage);
    }

    private String assembleUserMessage(String context, String query) {
        return "## 参考文档\n\n" + context + "\n\n"
                + "## 用户问题\n\n" + query;
    }
}
