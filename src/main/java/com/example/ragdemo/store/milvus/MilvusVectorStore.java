package com.example.ragdemo.store.milvus;

import com.example.ragdemo.config.EmbeddingProperties;
import com.example.ragdemo.config.MilvusProperties;
import com.example.ragdemo.model.domain.RetrievalResult;
import com.example.ragdemo.store.VectorStore;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 Milvus 的向量存储实现
 * <p>
 * 集合 Schema：
 * <ul>
 *     <li>chunk_id (VarChar, PK) — 与 chunkId 一一对应</li>
 *     <li>vector (FloatVector) — 稠密向量</li>
 *     <li>metadata_json (VarChar) — JSON 序列化的元数据</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.milvus.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MilvusVectorStore implements VectorStore {

    private static final String FIELD_CHUNK_ID = "chunk_id";
    private static final String FIELD_VECTOR = "vector";
    private static final String FIELD_METADATA_JSON = "metadata_json";

    private final MilvusProperties milvusProperties;
    private final EmbeddingProperties embeddingProperties;
    private final Gson gson = new Gson();

    private MilvusClientV2 client;

    // ========== 生命周期 ==========

    @PostConstruct
    public void init() {
        log.info("Initializing MilvusVectorStore: uri={}, collection={}",
                milvusProperties.getUri(), milvusProperties.getCollectionName());

        ConnectConfig.ConnectConfigBuilder configBuilder = ConnectConfig.builder()
                .uri(milvusProperties.getUri())
                .dbName(milvusProperties.getDbName());

        if (milvusProperties.getUsername() != null && !milvusProperties.getUsername().isBlank()) {
            configBuilder.username(milvusProperties.getUsername());
            configBuilder.password(milvusProperties.getPassword());
        }

        this.client = new MilvusClientV2(configBuilder.build());
        ensureCollection();
        log.info("MilvusVectorStore initialized successfully");
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            try {
                client.close();
                log.info("Milvus client closed");
            } catch (Exception e) {
                log.warn("Error closing Milvus client: {}", e.getMessage());
            }
        }
    }

    // ========== VectorStore 接口实现 ==========

    @Override
    public void upsert(String id, float[] vector, Map<String, Object> metadata) {
        upsertBatch(List.of(id), List.of(vector), metadata != null ? List.of(metadata) : List.of(Map.of()));
    }

    @Override
    public void upsertBatch(List<String> ids, List<float[]> vectors, List<Map<String, Object>> metadatas) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        List<JsonObject> rows = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            JsonObject row = new JsonObject();
            row.addProperty(FIELD_CHUNK_ID, ids.get(i));
            row.add(FIELD_VECTOR, gson.toJsonTree(toFloatList(vectors.get(i))));

            Map<String, Object> meta = (metadatas != null && i < metadatas.size() && metadatas.get(i) != null)
                    ? metadatas.get(i) : Map.of();
            row.addProperty(FIELD_METADATA_JSON, gson.toJson(meta));
            rows.add(row);
        }

        UpsertReq upsertReq = UpsertReq.builder()
                .collectionName(milvusProperties.getCollectionName())
                .data(rows)
                .build();

        client.upsert(upsertReq);
        log.debug("Upserted {} vectors into Milvus collection '{}'", ids.size(), milvusProperties.getCollectionName());
    }

    @Override
    public List<RetrievalResult> search(float[] queryVector, int topK) {
        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("ef", milvusProperties.getSearchEf());

        SearchReq searchReq = SearchReq.builder()
                .collectionName(milvusProperties.getCollectionName())
                .data(Collections.singletonList(new io.milvus.v2.service.vector.request.data.FloatVec(queryVector)))
                .topK(topK)
                .outputFields(Arrays.asList(FIELD_CHUNK_ID, FIELD_METADATA_JSON))
                .searchParams(searchParams)
                .consistencyLevel(parseConsistencyLevel(milvusProperties.getConsistencyLevel()))
                .build();

        SearchResp searchResp = client.search(searchReq);
        List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();

        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        // 单向量搜索，取第一组结果
        return results.get(0).stream()
                .map(this::toRetrievalResult)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        // 使用 filter 表达式删除
        String filterExpr = FIELD_CHUNK_ID + " in [" +
                ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(", ")) + "]";

        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(milvusProperties.getCollectionName())
                .filter(filterExpr)
                .build();

        client.delete(deleteReq);
        log.debug("Deleted {} vectors from Milvus collection '{}'", ids.size(), milvusProperties.getCollectionName());
    }

    // ========== 内部方法 ==========

    /**
     * 确保 Milvus 集合已创建（含索引），不存在则自动创建
     */
    private void ensureCollection() {
        String collectionName = milvusProperties.getCollectionName();

        boolean exists = client.hasCollection(
                HasCollectionReq.builder().collectionName(collectionName).build());

        if (exists) {
            log.info("Milvus collection '{}' already exists", collectionName);
            return;
        }

        log.info("Creating Milvus collection '{}' with dimension={}", collectionName, embeddingProperties.getDimension());

        // 构建 Schema
        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CHUNK_ID)
                .dataType(DataType.VarChar)
                .maxLength(milvusProperties.getMaxIdLength())
                .isPrimaryKey(true)
                .autoID(false)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_VECTOR)
                .dataType(DataType.FloatVector)
                .dimension(embeddingProperties.getDimension())
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_METADATA_JSON)
                .dataType(DataType.VarChar)
                .maxLength(milvusProperties.getMaxMetadataLength())
                .build());

        // 构建索引
        IndexParam vectorIndex = IndexParam.builder()
                .fieldName(FIELD_VECTOR)
                .indexType(parseIndexType(milvusProperties.getIndexType()))
                .metricType(parseMetricType(milvusProperties.getMetricType()))
                .extraParams(buildIndexExtraParams())
                .build();

        CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(Collections.singletonList(vectorIndex))
                .build();

        client.createCollection(createReq);
        log.info("Milvus collection '{}' created successfully", collectionName);
    }

    private Map<String, Object> buildIndexExtraParams() {
        Map<String, Object> params = new HashMap<>();
        String indexType = milvusProperties.getIndexType().toUpperCase();
        if ("HNSW".equals(indexType)) {
            params.put("M", milvusProperties.getHnswM());
            params.put("efConstruction", milvusProperties.getHnswEfConstruction());
        } else if ("IVF_FLAT".equals(indexType) || "IVF_SQ8".equals(indexType)) {
            params.put("nlist", 128);
        }
        return params;
    }

    private RetrievalResult toRetrievalResult(SearchResp.SearchResult sr) {
        RetrievalResult result = new RetrievalResult();
        result.setChunkId(String.valueOf(sr.getId()));
        result.setScore((float) sr.getScore());

        // 从 metadata_json 中解析元数据
        Object metaObj = sr.getEntity().get(FIELD_METADATA_JSON);
        if (metaObj != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = gson.fromJson(String.valueOf(metaObj), Map.class);
                if (meta != null) {
                    result.setContent((String) meta.get("content"));
                    result.setFileName((String) meta.get("fileName"));
                    result.setTitlePath((String) meta.get("titlePath"));
                }
            } catch (Exception e) {
                log.warn("Failed to parse metadata for chunk {}: {}", sr.getId(), e.getMessage());
            }
        }

        return result;
    }

    /**
     * 将 float[] 转换为 List&lt;Float&gt;（Milvus SDK 序列化需要）
     */
    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add(v);
        }
        return list;
    }

    private ConsistencyLevel parseConsistencyLevel(String level) {
        return switch (level.toUpperCase()) {
            case "STRONG" -> ConsistencyLevel.STRONG;
            case "BOUNDED" -> ConsistencyLevel.BOUNDED;
            case "SESSION" -> ConsistencyLevel.SESSION;
            default -> ConsistencyLevel.EVENTUALLY;
        };
    }

    private IndexParam.IndexType parseIndexType(String type) {
        return switch (type.toUpperCase()) {
            case "IVF_FLAT" -> IndexParam.IndexType.IVF_FLAT;
            case "IVF_SQ8" -> IndexParam.IndexType.IVF_SQ8;
            case "FLAT" -> IndexParam.IndexType.FLAT;
            default -> IndexParam.IndexType.HNSW;
        };
    }

    private IndexParam.MetricType parseMetricType(String type) {
        return switch (type.toUpperCase()) {
            case "L2" -> IndexParam.MetricType.L2;
            case "IP" -> IndexParam.MetricType.IP;
            default -> IndexParam.MetricType.COSINE;
        };
    }
}
