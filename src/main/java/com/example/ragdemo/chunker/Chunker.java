package com.example.ragdemo.chunker;

import com.example.ragdemo.model.domain.StructuredDocument;
import com.example.ragdemo.model.domain.TextChunk;

import java.util.List;

/**
 * Chunk 切分接口 — 将文档切分为适合向量化的文本块
 */
public interface Chunker {

    /**
     * 对结构化文档进行切分
     *
     * @param document 结构化文档
     * @return 切分后的文本块列表
     */
    List<TextChunk> split(StructuredDocument document);
}
