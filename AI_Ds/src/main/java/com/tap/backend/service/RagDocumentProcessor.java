package com.tap.backend.service;

import com.tap.backend.domain.document.DocumentEntity;
import com.tap.backend.domain.rag.CourseSpaceDocumentEntity;
import com.tap.backend.domain.rag.CourseSpaceEntity;
import com.tap.backend.domain.rag.DocChunkEntity;
import com.tap.backend.rag.LuceneBm25Service;
import com.tap.backend.repo.CourseSpaceDocumentRepository;
import com.tap.backend.repo.CourseSpaceRepository;
import com.tap.backend.repo.DocChunkRepository;
import com.tap.backend.repo.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Java 端文档处理器 — 当 Python worker 不可用时的降级方案。
 * 只做文本分块 + MySQL 存储 + Lucene BM25 索引，不依赖 Milvus/DashScope。
 */
@Service
public class RagDocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(RagDocumentProcessor.class);
    private static final int PARENT_CHUNK_SIZE = 1500;  // chars
    private static final int PARENT_OVERLAP = 100;
    private static final int CHILD_CHUNK_SIZE = 350;
    private static final int CHILD_OVERLAP = 30;
    private static final int MIN_VISIBLE_CHARS = 120;

    private final CourseSpaceDocumentRepository csDocRepo;
    private final DocChunkRepository docChunkRepo;
    private final DocumentRepository documentRepo;
    private final CourseSpaceRepository courseSpaceRepo;
    private final LuceneBm25Service luceneBm25;

    public RagDocumentProcessor(CourseSpaceDocumentRepository csDocRepo,
                                 DocChunkRepository docChunkRepo,
                                 DocumentRepository documentRepo,
                                 CourseSpaceRepository courseSpaceRepo,
                                 LuceneBm25Service luceneBm25) {
        this.csDocRepo = csDocRepo;
        this.docChunkRepo = docChunkRepo;
        this.documentRepo = documentRepo;
        this.courseSpaceRepo = courseSpaceRepo;
        this.luceneBm25 = luceneBm25;
    }

    @Async("fileExecutor")
    @Transactional
    public void processAsync(Long courseSpaceDocId) {
        try {
            processInternal(courseSpaceDocId);
        } catch (Exception e) {
            log.error("[RAG-Java] async processing failed for csd={}: {}", courseSpaceDocId, e.getMessage());
        }
    }

    @Transactional
    public void process(Long courseSpaceDocId) {
        processInternal(courseSpaceDocId);
    }

    private void processInternal(Long courseSpaceDocId) {
        CourseSpaceDocumentEntity csDoc = csDocRepo.findById(courseSpaceDocId).orElse(null);
        if (csDoc == null) {
            log.error("[RAG-Java] CourseSpaceDocument {} not found", courseSpaceDocId);
            return;
        }

        csDoc.setStatus("PROCESSING");
        csDocRepo.save(csDoc);

        try {
            DocumentEntity doc = documentRepo.findById(csDoc.getDocumentId()).orElse(null);
            if (doc == null) {
                fail(csDoc, "Document not found");
                return;
            }

            String text = doc.getExtractedText();
            if (text == null || text.isBlank()) {
                fail(csDoc, "No extracted text available");
                return;
            }
            text = normalizeExtractedText(text);
            if (!isUsableExtractedText(text)) {
                fail(csDoc, "Extracted text quality is too low. Please upload a text-based PDF/DOCX/TXT, or run OCR before importing.");
                return;
            }

            // Two-level chunking
            CourseSpaceEntity courseSpace = courseSpaceRepo.findById(csDoc.getCourseSpaceId()).orElse(null);
            if (courseSpace == null) {
                fail(csDoc, "Course space not found");
                return;
            }
            clearExistingChunks(courseSpace.getId(), doc.getId());

            List<DocChunkEntity> allChildren = new ArrayList<>();
            List<String> parentTexts = splitText(text, PARENT_CHUNK_SIZE, PARENT_OVERLAP);
            int totalChunks = 0;

            for (int pi = 0; pi < parentTexts.size(); pi++) {
                String pText = parentTexts.get(pi);
                String chapter = detectChapter(pText);

                DocChunkEntity parent = new DocChunkEntity();
                parent.setDocument(doc);
                parent.setCourseSpace(courseSpace);
                parent.setChunkType("parent");
                parent.setChunkIndex(pi);
                parent.setContent(pText);
                parent.setChapterPath(chapter);
                parent.setTokenCount(pText.length() / 2);
                parent = docChunkRepo.save(parent);

                List<String> childTexts = splitText(pText, CHILD_CHUNK_SIZE, CHILD_OVERLAP);
                for (int ci = 0; ci < childTexts.size(); ci++) {
                    String cText = childTexts.get(ci);
                    DocChunkEntity child = new DocChunkEntity();
                    child.setDocument(doc);
                    child.setCourseSpace(courseSpace);
                    child.setChunkType("child");
                    child.setParent(parent);
                    child.setChunkIndex(ci);
                    child.setContent(cText);
                    child.setChapterPath(chapter);
                    child.setTokenCount(cText.length() / 2);
                    child = docChunkRepo.save(child);
                    allChildren.add(child);
                    totalChunks++;
                }
            }

            // Update Lucene BM25 index — reload from DB to get populated read-only columns
            if (!allChildren.isEmpty()) {
                try {
                    List<Long> childIds = allChildren.stream().map(DocChunkEntity::getId).toList();
                    List<DocChunkEntity> reloaded = docChunkRepo.findAllByIdIn(childIds);
                    luceneBm25.addChunks(reloaded);
                } catch (Exception e) {
                    log.warn("[RAG-Java] BM25 index update failed: {}", e.getMessage());
                }
            }

            csDoc.setStatus("READY");
            csDoc.setChunkCount(totalChunks);
            csDocRepo.save(csDoc);

            log.info("[RAG-Java] Processed csd={}: {} parents, {} children",
                    courseSpaceDocId, parentTexts.size(), totalChunks);

        } catch (Exception e) {
            fail(csDoc, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void fail(CourseSpaceDocumentEntity csDoc, String error) {
        csDoc.setStatus("FAILED");
        csDoc.setErrorMessage(error != null ? error.substring(0, Math.min(error.length(), 500)) : "unknown");
        csDocRepo.save(csDoc);
        log.error("[RAG-Java] FAILED csd={}: {}", csDoc.getId(), error);
    }

    private void clearExistingChunks(Long courseSpaceId, Long documentId) {
        List<DocChunkEntity> children =
                docChunkRepo.findAllByCourseSpaceIdAndDocumentIdAndChunkType(courseSpaceId, documentId, "child");
        if (!children.isEmpty()) {
            docChunkRepo.deleteAllInBatch(children);
        }
        List<DocChunkEntity> parents =
                docChunkRepo.findAllByCourseSpaceIdAndDocumentIdAndChunkType(courseSpaceId, documentId, "parent");
        if (!parents.isEmpty()) {
            docChunkRepo.deleteAllInBatch(parents);
        }
        if (!children.isEmpty() || !parents.isEmpty()) {
            log.info("[RAG-Java] cleared existing chunks for courseSpaceId={}, documentId={}", courseSpaceId, documentId);
        }
    }

    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        // Split by paragraphs first
        String[] paragraphs = text.split("\n\n+");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (current.length() + para.length() > chunkSize && current.length() > 0) {
                chunks.add(current.toString().trim());
                // Keep overlap
                String overlapText = current.substring(Math.max(0, current.length() - overlap));
                current = new StringBuilder(overlapText);
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(para);
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        // If no paragraph splits worked, do character-level splitting
        if (chunks.isEmpty() && text.length() > chunkSize) {
            for (int i = 0; i < text.length(); i += chunkSize - overlap) {
                chunks.add(text.substring(i, Math.min(i + chunkSize, text.length())));
            }
        } else if (chunks.isEmpty()) {
            chunks.add(text.trim());
        }

        return chunks;
    }

    private String detectChapter(String text) {
        String[] lines = text.split("\n", 4);
        for (String line : lines) {
            String s = line.trim();
            if (s.startsWith("#")) return s.replaceFirst("^#+\\s*", "");
            if (s.matches("^第[一二三四五六七八九十百千\\d]+[章节篇].*")) return s;
            if (s.matches("^\\d+[\\.\\s].*") && s.length() < 80) return s;
            if (s.matches("^Chapter\\s+\\d+.*")) return s;
        }
        return "";
    }

    private String normalizeExtractedText(String text) {
        if (text == null) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n").trim();
        return normalized;
    }

    private boolean isUsableExtractedText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String visible = text.replaceAll("\\s+", "");
        if (visible.length() < MIN_VISIBLE_CHARS) {
            return false;
        }

        String[] lines = text.split("\n");
        long nonEmptyLineCount = Arrays.stream(lines)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .count();
        if (nonEmptyLineCount == 0) {
            return false;
        }
        long informativeLineCount = Arrays.stream(lines)
                .map(String::trim)
                .filter(this::isInformativeLine)
                .count();
        long textualCharCount = text.codePoints().filter(this::isTextLikeCodePoint).count();
        double density = visible.isEmpty() ? 0.0 : (double) textualCharCount / visible.length();
        double informativeRatio = (double) informativeLineCount / nonEmptyLineCount;
        return density >= 0.45 && informativeLineCount >= 2 && informativeRatio >= 0.35;
    }

    private boolean isInformativeLine(String line) {
        if (line == null) {
            return false;
        }
        String s = line.trim();
        if (s.length() < 8) {
            return false;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if ((lower.startsWith("[") && lower.endsWith("]"))
                || (s.contains("=") && s.length() < 40)
                || s.matches("^(封面页|书名页|版权页|前言|目录|目次|索引)$")) {
            return false;
        }
        long textLikeChars = s.codePoints().filter(this::isTextLikeCodePoint).count();
        return textLikeChars >= 8;
    }

    private boolean isTextLikeCodePoint(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return Character.isLetterOrDigit(cp) || script == Character.UnicodeScript.HAN;
    }
}
