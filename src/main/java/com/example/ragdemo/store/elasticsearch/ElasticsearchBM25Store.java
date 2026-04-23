package com.example.ragdemo.store.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.example.ragdemo.config.ElasticsearchProperties;
import com.example.ragdemo.model.domain.RetrievalResult;
import com.example.ragdemo.store.BM25Store;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 基于 Elasticsearch 的 BM25 全文检索存储实现
 * <p>
 * 索引 Mapping：
 * <ul>
 *     <li>content (text, ik_max_word / standard) — 全文检索字段</li>
 * </ul>
 * 当 rag.elasticsearch.enabled=true 时激活，替代 InMemoryBM25Store。
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.elasticsearch.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ElasticsearchBM25Store implements BM25Store {

    private final ElasticsearchProperties esProperties;

    private RestClient restClient;
    private ElasticsearchClient client;

    // ========== 生命周期 ==========

    @PostConstruct
    public void init() {
        log.info("Initializing ElasticsearchBM25Store: uri={}, index={}",
                esProperties.getUri(), esProperties.getIndexName());

        URI uri = URI.create(esProperties.getUri());
        HttpHost host = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());

        RestClientBuilder builder = RestClient.builder(host)
                .setRequestConfigCallback(config -> config
                        .setConnectTimeout(esProperties.getConnectTimeoutMs())
                        .setSocketTimeout(esProperties.getSocketTimeoutMs()));

        // 认证配置
        if (esProperties.getUsername() != null && !esProperties.getUsername().isBlank()) {
            BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
            credsProv.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(esProperties.getUsername(), esProperties.getPassword()));
            builder.setHttpClientConfigCallback(httpBuilder ->
                    httpBuilder.setDefaultCredentialsProvider(credsProv));
        }

        this.restClient = builder.build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.client = new ElasticsearchClient(transport);

        ensureIndex();
        log.info("ElasticsearchBM25Store initialized successfully");
    }

    @PreDestroy
    public void destroy() {
        if (restClient != null) {
            try {
                restClient.close();
                log.info("Elasticsearch RestClient closed");
            } catch (IOException e) {
                log.warn("Error closing Elasticsearch RestClient: {}", e.getMessage());
            }
        }
    }

    // ========== BM25Store 接口实现 ==========

    @Override
    public void index(String id, String content) {
        try {
            client.index(i -> i
                    .index(esProperties.getIndexName())
                    .id(id)
                    .document(Map.of("content", content)));
            log.debug("Indexed BM25 doc: {}", id);
        } catch (IOException e) {
            log.error("Failed to index doc {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Elasticsearch index failed", e);
        }
    }

    @Override
    public void indexBatch(List<String> ids, List<String> contents) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (int i = 0; i < ids.size(); i++) {
                final String docId = ids.get(i);
                final String docContent = contents.get(i);
                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(esProperties.getIndexName())
                                .id(docId)
                                .document(Map.of("content", docContent))));
            }

            BulkResponse response = client.bulk(bulkBuilder.build());
            if (response.errors()) {
                List<String> errorIds = new ArrayList<>();
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        errorIds.add(item.id());
                        log.warn("Bulk index error for doc {}: {}", item.id(), item.error().reason());
                    }
                }
                log.error("Bulk index had {} errors out of {} docs", errorIds.size(), ids.size());
            } else {
                log.debug("Bulk indexed {} BM25 docs", ids.size());
            }
        } catch (IOException e) {
            log.error("Bulk index failed: {}", e.getMessage(), e);
            throw new RuntimeException("Elasticsearch bulk index failed", e);
        }
    }

    @Override
    public List<RetrievalResult> search(String query, int topK) {
        try {
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(esProperties.getIndexName())
                    .size(topK)
                    .query(q -> q
                            .match(m -> m
                                    .field("content")
                                    .query(query))));

            SearchResponse<Map> response = client.search(searchRequest, Map.class);

            List<RetrievalResult> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                RetrievalResult result = new RetrievalResult();
                result.setChunkId(hit.id());
                result.setScore(hit.score() != null ? hit.score() : 0.0);
                if (hit.source() != null) {
                    Object content = hit.source().get("content");
                    result.setContent(content != null ? content.toString() : "");
                }
                results.add(result);
            }

            log.debug("BM25 search for '{}' returned {} results", query, results.size());
            return results;
        } catch (IOException e) {
            log.error("BM25 search failed for query '{}': {}", query, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public void delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        try {
            DeleteByQueryRequest deleteReq = DeleteByQueryRequest.of(d -> d
                    .index(esProperties.getIndexName())
                    .query(q -> q
                            .ids(idsQuery -> idsQuery.values(ids))));

            client.deleteByQuery(deleteReq);
            log.debug("Deleted {} BM25 docs", ids.size());
        } catch (IOException e) {
            log.error("Failed to delete BM25 docs: {}", e.getMessage(), e);
            throw new RuntimeException("Elasticsearch delete failed", e);
        }
    }

    // ========== 内部方法 ==========

    /**
     * 确保 ES 索引已创建，不存在则自动创建
     */
    private void ensureIndex() {
        String indexName = esProperties.getIndexName();
        try {
            boolean exists = client.indices().exists(e -> e.index(indexName)).value();
            if (exists) {
                log.info("Elasticsearch index '{}' already exists", indexName);
                return;
            }

            log.info("Creating Elasticsearch index '{}'", indexName);

            CreateIndexRequest createReq = CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .settings(IndexSettings.of(s -> s
                            .numberOfShards(String.valueOf(esProperties.getNumberOfShards()))
                            .numberOfReplicas(String.valueOf(esProperties.getNumberOfReplicas()))))
                    .mappings(TypeMapping.of(m -> m
                            .properties("content", Property.of(p -> p
                                    .text(TextProperty.of(t -> t
                                            .analyzer("standard"))))))));

            client.indices().create(createReq);
            log.info("Elasticsearch index '{}' created successfully", indexName);
        } catch (IOException e) {
            log.error("Failed to ensure Elasticsearch index '{}': {}", indexName, e.getMessage(), e);
            throw new RuntimeException("Elasticsearch index creation failed", e);
        }
    }
}
