package com.tap.backend.rag;

import com.tap.backend.domain.rag.DocChunkEntity;
import com.tap.backend.repo.DocChunkRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class LuceneBm25Service {

    private static final Logger log = LoggerFactory.getLogger(LuceneBm25Service.class);

    private final DocChunkRepository docChunkRepo;
    private Directory directory;
    private Analyzer analyzer;
    private IndexWriter writer;
    private SearcherManager searcherManager;

    public LuceneBm25Service(DocChunkRepository docChunkRepo) {
        this.docChunkRepo = docChunkRepo;
    }

    public record Bm25Hit(long chunkId, long parentId, long courseSpaceId,
                           long docId, String chapterPath, String pageRange, float score) {}

    @PostConstruct
    void init() {
        try {
            directory = new ByteBuffersDirectory();
            analyzer = new SmartChineseAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            writer = new IndexWriter(directory, config);

            List<DocChunkEntity> children = docChunkRepo.findAllByChunkType("child");
            for (DocChunkEntity chunk : children) {
                addDocument(chunk);
            }
            writer.commit();

            searcherManager = new SearcherManager(writer, true, true, null);
            log.info("[BM25] Index built with {} child chunks", children.size());
        } catch (Exception e) {
            log.error("[BM25] Failed to build index, degrading to vector-only mode", e);
        }
    }

    public void addChunks(List<DocChunkEntity> newChunks) {
        if (writer == null) return;
        try {
            for (DocChunkEntity chunk : newChunks) {
                addDocument(chunk);
            }
            writer.commit();
            searcherManager.maybeRefresh();
            log.debug("[BM25] Added {} chunks to index", newChunks.size());
        } catch (IOException e) {
            log.error("[BM25] Failed to add chunks to index", e);
        }
    }

    public List<Bm25Hit> search(long courseSpaceId, String query, int topK) {
        if (searcherManager == null) return Collections.emptyList();
        IndexSearcher searcher = null;
        try {
            searcher = searcherManager.acquire();
            BooleanQuery.Builder boolBuilder = new BooleanQuery.Builder();

            boolBuilder.add(LongPoint.newExactQuery("course_space_id_point", courseSpaceId),
                    BooleanClause.Occur.FILTER);

            org.apache.lucene.queryparser.classic.QueryParser parser =
                    new org.apache.lucene.queryparser.classic.QueryParser("content", analyzer);
            Query textQuery = parser.parse(org.apache.lucene.queryparser.classic.QueryParser.escape(query));
            boolBuilder.add(textQuery, BooleanClause.Occur.MUST);

            TopDocs topDocs = searcher.search(boolBuilder.build(), topK);
            List<Bm25Hit> hits = new ArrayList<>();

            float maxScore = topDocs.scoreDocs.length > 0 ? topDocs.scoreDocs[0].score : 1.0f;
            if (maxScore <= 0) maxScore = 1.0f;

            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(sd.doc);
                hits.add(new Bm25Hit(
                        Long.parseLong(doc.get("chunk_id")),
                        Long.parseLong(doc.get("parent_id")),
                        Long.parseLong(doc.get("course_space_id")),
                        Long.parseLong(doc.get("doc_id")),
                        doc.get("chapter_path"),
                        doc.get("page_range"),
                        sd.score / maxScore
                ));
            }
            return hits;
        } catch (Exception e) {
            log.error("[BM25] Search failed", e);
            return Collections.emptyList();
        } finally {
            if (searcher != null) {
                try { searcherManager.release(searcher); } catch (IOException ignored) {}
            }
        }
    }

    public boolean isAvailable() {
        return searcherManager != null;
    }

    private void addDocument(DocChunkEntity chunk) throws IOException {
        Document doc = new Document();
        doc.add(new StringField("chunk_id", String.valueOf(chunk.getId()), Field.Store.YES));
        doc.add(new StringField("parent_id", String.valueOf(chunk.getParentId() != null ? chunk.getParentId() : 0), Field.Store.YES));
        // courseSpaceId may be null on newly-persisted entities (read-only JPA column)
        Long csId = chunk.getCourseSpaceId();
        if (csId == null && chunk.getCourseSpace() != null) csId = chunk.getCourseSpace().getId();
        Long docId = chunk.getDocumentId();
        if (docId == null && chunk.getDocument() != null) docId = chunk.getDocument().getId();
        doc.add(new StringField("course_space_id", String.valueOf(csId), Field.Store.YES));
        doc.add(new LongPoint("course_space_id_point", csId != null ? csId : 0));
        doc.add(new StringField("doc_id", String.valueOf(docId), Field.Store.YES));
        doc.add(new StringField("chapter_path", chunk.getChapterPath() != null ? chunk.getChapterPath() : "", Field.Store.YES));
        doc.add(new StringField("page_range", chunk.getPageRange() != null ? chunk.getPageRange() : "", Field.Store.YES));
        doc.add(new TextField("content", chunk.getContent(), Field.Store.NO));
        writer.addDocument(doc);
    }

    @PreDestroy
    void close() {
        try {
            if (searcherManager != null) searcherManager.close();
            if (writer != null) writer.close();
            if (directory != null) directory.close();
        } catch (IOException e) {
            log.error("[BM25] Error closing resources", e);
        }
    }
}
