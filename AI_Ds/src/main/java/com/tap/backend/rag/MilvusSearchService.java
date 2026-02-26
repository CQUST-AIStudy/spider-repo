package com.tap.backend.rag;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class MilvusSearchService {

    private static final Logger log = LoggerFactory.getLogger(MilvusSearchService.class);
    private static final List<String> OUTPUT_FIELDS = List.of(
            "chunk_id", "parent_id", "course_space_id", "doc_id", "chapter_path", "page_range"
    );

    private final RagProperties props;
    private volatile MilvusServiceClient client;

    public MilvusSearchService(RagProperties props) {
        this.props = props;
    }

    public record SearchHit(
            long chunkId, long parentId, long courseSpaceId, long docId,
            String chapterPath, String pageRange, float score
    ) {}

    /**
     * 在 Milvus 中按 course_space_id 过滤，检索最相似的 child 向量。
     * 如果 Milvus 不可用，返回空列表（降级到 BM25-only 模式）。
     */
    public List<SearchHit> search(long courseSpaceId, List<Float> queryVector, int topK) {
        MilvusServiceClient c;
        try {
            c = getClient();
        } catch (Exception e) {
            log.warn("[Milvus] unavailable, degrading to BM25-only: {}", e.getMessage());
            return Collections.emptyList();
        }
        String collection = props.milvus().collection();

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(collection)
                .withMetricType(MetricType.COSINE)
                .withTopK(topK)
                .withVectors(Collections.singletonList(queryVector))
                .withVectorFieldName("vector")
                .withOutFields(OUTPUT_FIELDS)
                .withExpr("course_space_id == " + courseSpaceId)
                .withParams("{\"nprobe\": 16}")
                .build();

        R<SearchResults> resp = c.search(searchParam);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            log.error("[Milvus] search failed: {}", resp.getMessage());
            throw new RuntimeException("Milvus search failed: " + resp.getMessage());
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(resp.getData().getResults());
        List<SearchHit> hits = new ArrayList<>();

        // 只有一个 query vector，所以取 index 0
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
        for (int i = 0; i < scores.size(); i++) {
            SearchResultsWrapper.IDScore idScore = scores.get(i);
            long chunkId = idScore.getLongID();
            float score = idScore.getScore();

            // 从 output fields 中提取元数据
            long parentId = (Long) wrapper.getFieldData("parent_id", 0).get(i);
            long csId = (Long) wrapper.getFieldData("course_space_id", 0).get(i);
            long docId = (Long) wrapper.getFieldData("doc_id", 0).get(i);
            String chapterPath = (String) wrapper.getFieldData("chapter_path", 0).get(i);
            String pageRange = (String) wrapper.getFieldData("page_range", 0).get(i);

            hits.add(new SearchHit(chunkId, parentId, csId, docId, chapterPath, pageRange, score));
        }

        log.debug("[Milvus] search returned {} hits for courseSpaceId={}", hits.size(), courseSpaceId);
        return hits;
    }

    private MilvusServiceClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    ConnectParam connectParam = ConnectParam.newBuilder()
                            .withHost(props.milvus().host())
                            .withPort(props.milvus().port())
                            .build();
                    client = new MilvusServiceClient(connectParam);
                    log.info("[Milvus] connected to {}:{}", props.milvus().host(), props.milvus().port());
                }
            }
        }
        return client;
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }
}
