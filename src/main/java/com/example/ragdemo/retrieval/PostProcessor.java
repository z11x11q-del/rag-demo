package com.example.ragdemo.retrieval;

import com.example.ragdemo.model.domain.RetrievalResult;
import com.example.ragdemo.model.dto.RagQueryResponse;

import java.util.List;

/**
 * 后处理接口 — 对 LLM 输出进行引用补充、格式化、敏感校验等，
 * 并组装最终的 {@link RagQueryResponse}
 */
public interface PostProcessor {

    /**
     * 对 LLM 生成的原始回答进行后处理，组装完整响应
     *
     * @param rawAnswer  LLM 原始输出
     * @param references 检索引用来源
     * @return 包含回答和引用来源的完整响应
     */
    RagQueryResponse process(String rawAnswer, List<RetrievalResult> references);
}
