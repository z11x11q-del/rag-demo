package com.example.ragdemo.controller;

import com.example.ragdemo.model.dto.RagQueryRequest;
import com.example.ragdemo.model.dto.RagQueryResponse;
import com.example.ragdemo.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * RAG 问答 API — 对外统一问答入口
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    /**
     * 问答接口
     */
    @PostMapping("/query")
    public ResponseEntity<RagQueryResponse> query(@RequestBody RagQueryRequest request) {
        RagQueryResponse response = ragService.answer(request);
        return ResponseEntity.ok(response);
    }
}
