package com.example.ragdemo.service;

import com.example.ragdemo.llm.LlmClient;
import com.example.ragdemo.model.domain.RetrievalResult;
import com.example.ragdemo.model.dto.RagQueryRequest;
import com.example.ragdemo.model.dto.RagQueryResponse;
import com.example.ragdemo.retrieval.PostProcessor;
import com.example.ragdemo.retrieval.PromptBuilder;
import com.example.ragdemo.retrieval.QueryPreprocessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 问答门面服务 — 编排完整的在线检索流水线
 */
@Service
@RequiredArgsConstructor
public class RagService {

    private final QueryPreprocessor queryPreprocessor;
    private final RetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final PostProcessor postProcessor;

    /**
     * 执行完整的 RAG 问答流程
     *
     * @param request 用户查询请求
     * @return 问答响应（包含回答和引用来源）
     */
    public RagQueryResponse answer(RagQueryRequest request) {
        // TODO: 实现完整 RAG 链路
        // 1. Query 预处理（清洗 / 改写 / 意图识别）
        // 2. 多路召回 + 重排
        // 3. 上下文构造
        // 4. Prompt 组装
        // 5. LLM 生成
        // 6. 后处理（引用补充 / 格式化 / 校验）
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
