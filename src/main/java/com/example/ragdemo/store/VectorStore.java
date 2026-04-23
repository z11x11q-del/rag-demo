package com.example.ragdemo.store;

import com.example.ragdemo.model.domain.RetrievalResult;

import java.util.List;
import java.util.Map;

/**
 * 稠密向量索引存储接口
 */
public interface VectorStore {

    /**
     * 写入向量（upsert 语义：存在则更新，不存在则插入）
     *
     * @param id       向量 ID（与 chunkId 一一对应）
     * @param vector   稠密向量
     * @param metadata 附加元数据（用于标量过滤）
     */
    void upsert(String id, float[] vector, Map<String, Object> metadata);

    /**
     * 批量写入向量
     *
     * @param ids       向量 ID 列表
     * @param vectors   向量列表
     * @param metadatas 元数据列表
     */
    void upsertBatch(List<String> ids, List<float[]> vectors, List<Map<String, Object>> metadatas);

    /**
     * 向量相似度检索
     *
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @return 检索结果（按相似度降序）
     */
    List<RetrievalResult> search(float[] queryVector, int topK);

    /**
     * 删除指定 ID 的向量
     *
     * @param ids 向量 ID 列表
     */
    void delete(List<String> ids);
}
