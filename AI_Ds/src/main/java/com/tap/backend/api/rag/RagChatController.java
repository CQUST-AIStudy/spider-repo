package com.tap.backend.api.rag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tap.backend.ai.AiProperties;
import com.tap.backend.domain.rag.CourseSpaceEntity;
import com.tap.backend.domain.rag.DocChunkAnnotationEntity;
import com.tap.backend.domain.rag.DocChunkEntity;
import com.tap.backend.domain.rag.QaLogEntity;
import com.tap.backend.security.PrincipalResolver;
import com.tap.backend.security.UserPrincipal;
import com.tap.backend.rag.*;
import com.tap.backend.repo.CourseSpaceDocumentRepository;
import com.tap.backend.repo.CourseSpaceRepository;
import com.tap.backend.repo.DocChunkRepository;
import com.tap.backend.repo.DocumentRepository;
import com.tap.backend.repo.QaLogRepository;
import com.tap.backend.service.CourseSpaceService;
import com.tap.backend.domain.document.DocumentEntity;
import com.tap.backend.domain.rag.CourseSpaceDocumentEntity;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RAG 问答 API — 双路检索 + 融合排序 + 证据压缩 + strict/open 模式。
 */
@RestController
@RequestMapping("/api/rag")
public class RagChatController {

    private static final Logger log = LoggerFactory.getLogger(RagChatController.class);

    private final DashScopeEmbeddingClient embeddingClient;
    private final MilvusSearchService milvusSearch;
    private final LuceneBm25Service luceneBm25;
    private final FusionRankService fusionRank;
    private final TopRerankService topRerank;
    private final EvidenceCompressService evidenceCompress;
    private final CoverageCalculator coverageCalc;
    private final ModeDecisionService modeDecision;
    private final DocChunkAnnotationService annotationService;
    private final IntentClassifyService intentClassify;
    private final WebFallbackService webFallback;
    private final RagProperties ragProps;
    private final AiProperties aiProps;
    private final QaLogRepository qaLogRepo;
    private final CourseSpaceRepository courseSpaceRepo;
    private final CourseSpaceDocumentRepository csDocRepo;
    private final DocChunkRepository docChunkRepo;
    private final DocumentRepository documentRepo;
    private final CourseSpaceService courseSpaceService;
    private final PrincipalResolver principalResolver;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public RagChatController(DashScopeEmbeddingClient embeddingClient,
                             MilvusSearchService milvusSearch,
                             LuceneBm25Service luceneBm25,
                             FusionRankService fusionRank,
                             TopRerankService topRerank,
                             EvidenceCompressService evidenceCompress,
                             CoverageCalculator coverageCalc,
                             ModeDecisionService modeDecision,
                             DocChunkAnnotationService annotationService,
                             IntentClassifyService intentClassify,
                             WebFallbackService webFallback,
                             RagProperties ragProps,
                             AiProperties aiProps,
                             QaLogRepository qaLogRepo,
                             CourseSpaceRepository courseSpaceRepo,
                             CourseSpaceDocumentRepository csDocRepo,
                             DocChunkRepository docChunkRepo,
                             DocumentRepository documentRepo,
                             CourseSpaceService courseSpaceService,
                             PrincipalResolver principalResolver) {
        this.embeddingClient = embeddingClient;
        this.milvusSearch = milvusSearch;
        this.luceneBm25 = luceneBm25;
        this.fusionRank = fusionRank;
        this.topRerank = topRerank;
        this.evidenceCompress = evidenceCompress;
        this.coverageCalc = coverageCalc;
        this.modeDecision = modeDecision;
        this.annotationService = annotationService;
        this.intentClassify = intentClassify;
        this.webFallback = webFallback;
        this.ragProps = ragProps;
        this.aiProps = aiProps;
        this.qaLogRepo = qaLogRepo;
        this.courseSpaceRepo = courseSpaceRepo;
        this.csDocRepo = csDocRepo;
        this.docChunkRepo = docChunkRepo;
        this.documentRepo = documentRepo;
        this.courseSpaceService = courseSpaceService;
        this.principalResolver = principalResolver;
    }

    record RagChatRequest(Long courseSpaceId, String query, String mode) {}

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chat(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestBody RagChatRequest request) {
        var resolved = principalResolver.resolve(principal);
        return chatForReadableSpace(request, resolved.userId(), false, null, null);
    }

    ResponseEntity<StreamingResponseBody> chatForReadableSpace(RagChatRequest request,
                                                               Long requesterUserId,
                                                               boolean allowPublicRead,
                                                               String studentId,
                                                               String studentNum) {
        log.info("[RAG] chat request: courseSpaceId={}, query={}, mode={}", request.courseSpaceId(), request.query(), request.mode());

        if (request.query() == null || request.query().isBlank()) {
            return ResponseEntity.ok().body(out -> {
                out.write("请输入有效的问题".getBytes(StandardCharsets.UTF_8));
                out.flush();
            });
        }
        if (request.courseSpaceId() == null) {
            return ResponseEntity.ok().body(out -> {
                out.write("请选择课程空间".getBytes(StandardCharsets.UTF_8));
                out.flush();
            });
        }

        StreamingResponseBody body = outputStream -> {
            StringBuilder fullAnswer = new StringBuilder();
            double coverageScore = 0.0;
            String effectiveMode = "strict";
            boolean usedWeb = false;
            String intentType = null;
            boolean integrityViolation = false;
            List<CitationInfo> citations = new ArrayList<>();

            try {
                CourseSpaceEntity cs = studentNum != null
                        ? courseSpaceService.requireReadableSpaceForStudent(request.courseSpaceId(), studentNum)
                        : courseSpaceService.requireReadableSpace(
                                request.courseSpaceId(), requesterUserId, allowPublicRead);
                if (cs == null) {
                    outputStream.write("课程空间不存在".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    return;
                }

                // 0. Intent classification
                IntentClassifyService.IntentResult intentResult = intentClassify.classify(request.query());
                intentType = intentResult.intentType();
                integrityViolation = intentResult.academicIntegrityViolation();

                // 1. Embed query (graceful — if DashScope fails, skip vector search)
                List<Float> queryVec = Collections.emptyList();
                try {
                    queryVec = embeddingClient.embedQuery(request.query());
                } catch (Exception embErr) {
                    log.warn("[RAG] Embedding failed, using BM25-only: {}", embErr.getMessage());
                }

                // 2. Dual-path retrieval: Milvus + Lucene BM25
                List<MilvusSearchService.SearchHit> vecHits = Collections.emptyList();
                if (!queryVec.isEmpty()) {
                    try {
                        vecHits = milvusSearch.search(
                                request.courseSpaceId(), queryVec, ragProps.retrieval().topK());
                    } catch (Exception milvusErr) {
                        log.warn("[RAG] Milvus search failed: {}", milvusErr.getMessage());
                    }
                }

                List<LuceneBm25Service.Bm25Hit> bm25Hits = Collections.emptyList();
                if (luceneBm25.isAvailable()) {
                    bm25Hits = luceneBm25.search(request.courseSpaceId(), request.query(), ragProps.retrieval().topK());
                }
                log.debug("[RAG] vecHits={}, bm25Hits={}", vecHits.size(), bm25Hits.size());

                // 3. Load annotations for this course space → build chunkId→annotationType map
                List<DocChunkAnnotationEntity> annotations = annotationService.listByCourseSpace(request.courseSpaceId());
                Map<Long, String> chunkAnnotations = annotations.stream()
                        .collect(Collectors.toMap(DocChunkAnnotationEntity::getChunkId,
                                DocChunkAnnotationEntity::getAnnotationType, (a, b) -> a));

                // 4. Build docType map from course_space_document
                List<CourseSpaceDocumentEntity> csDocs = csDocRepo.findAllByCourseSpaceId(request.courseSpaceId());
                Map<Long, String> docTypeMap = csDocs.stream()
                        .collect(Collectors.toMap(CourseSpaceDocumentEntity::getDocumentId,
                                d -> d.getDocType() != null ? d.getDocType() : "other", (a, b) -> a));

                // 5. Fusion ranking
                List<FusionRankService.RankedParent> ranked = fusionRank.rank(
                        vecHits, bm25Hits, chunkAnnotations, docTypeMap, ragProps.retrieval().topParent());
                log.debug("[RAG] fusionRanked={}", ranked.size());

                // 5b. If no ranked results from vector/BM25, try direct MySQL fallback
                if (ranked.isEmpty()) {
                    ranked = fallbackMySqlSearch(request.courseSpaceId(), request.query(), ragProps.retrieval().topParent());
                    log.debug("[RAG] mysqlFallback={}", ranked.size());
                }

                if (ranked.isEmpty()) {
                    outputStream.write("未找到相关课程资料，请尝试换个问法。".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    saveQaLog(request, cs, "", 0.0, "strict", false, null, Collections.emptyList(), studentId);
                    return;
                }

                // 6. Load parent content from MySQL
                List<Long> parentIds = ranked.stream().map(FusionRankService.RankedParent::parentId).toList();
                Map<Long, DocChunkEntity> parentChunks = docChunkRepo.findAllByIdIn(parentIds).stream()
                        .collect(Collectors.toMap(DocChunkEntity::getId, Function.identity()));

                // 6b. Top rerank for multi-route recall stabilization
                ranked = topRerank.rerank(request.query(), ranked, parentChunks, chunkAnnotations);
                parentIds = ranked.stream().map(FusionRankService.RankedParent::parentId).toList();
                Set<Long> rerankedParentIdSet = new HashSet<>(parentIds);
                parentChunks = parentChunks.entrySet().stream()
                        .filter(e -> rerankedParentIdSet.contains(e.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                final Map<Long, DocChunkEntity> parentChunksFinal = parentChunks;

                Map<Long, String> docNames = documentRepo.findAllById(
                        ranked.stream().map(FusionRankService.RankedParent::docId).collect(Collectors.toSet())
                ).stream().collect(Collectors.toMap(DocumentEntity::getId, DocumentEntity::getFilename));

                // 7. Evidence compression for each parent
                List<EvidenceBlock> evidenceBlocks = new ArrayList<>();
                for (FusionRankService.RankedParent rp : ranked) {
                    DocChunkEntity chunk = parentChunks.get(rp.parentId());
                    if (chunk == null) continue;
                    EvidenceCompressService.CompressedEvidence compressed = evidenceCompress.compress(
                            chunk.getContent(), request.query(),
                            rp.parentId(), rp.chapterPath(), rp.pageRange());
                    String docName = docNames.getOrDefault(rp.docId(), "未知文档");
                    String evidenceText = compressed.sentences().stream()
                            .map(EvidenceCompressService.ScoredSentence::text)
                            .collect(Collectors.joining("。"));
                    evidenceBlocks.add(new EvidenceBlock(rp.parentId(), rp.docId(), docName,
                            rp.chapterPath(), rp.pageRange(), rp.finalScore(), evidenceText, rp.docType()));
                }

                // 8. Coverage calculation
                double top1Score = ranked.get(0).finalScore();
                boolean hitFaq = ranked.stream().anyMatch(r -> "faq".equals(r.docType()));
                boolean hitAnnotation = !chunkAnnotations.isEmpty() &&
                        ranked.stream().anyMatch(r -> {
                            DocChunkEntity c = parentChunksFinal.get(r.parentId());
                            return c != null && chunkAnnotations.containsKey(c.getId());
                        });
                double querySupportRatio = computeQuerySupportRatio(request.query(), evidenceBlocks);
                coverageScore = coverageCalc.calculate(
                        top1Score, evidenceBlocks.size(), hitFaq, hitAnnotation, querySupportRatio);
                log.debug("[RAG] coverage={}, threshold={}", coverageScore, ragProps.coverage().threshold());

                // 9. Mode decision
                ModeDecisionService.ModeDecision decision = modeDecision.decide(
                        request.mode(), cs.getDefaultMode(),
                        Boolean.TRUE.equals(cs.getAllowWebSearch()),
                        coverageScore, ragProps.coverage().threshold());
                effectiveMode = decision.effectiveMode();

                // strict mode + low coverage → return warning
                if (decision.lowCoverageMessage() != null) {
                    outputStream.write(decision.lowCoverageMessage().getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    fullAnswer.append(decision.lowCoverageMessage());
                    saveQaLog(request, cs, fullAnswer.toString(), coverageScore, effectiveMode, false, intentType,
                            Collections.emptyList(), studentId);
                    return;
                }

                // 9b. Web fallback if needed
                List<WebFallbackService.WebResult> webResults = Collections.emptyList();
                if (decision.shouldFallbackToWeb()) {
                    webResults = webFallback.search(request.query(), intentType, 5);
                    usedWeb = !webResults.isEmpty();
                    for (WebFallbackService.WebResult wr : webResults) {
                        evidenceBlocks.add(new EvidenceBlock(0, 0, wr.title(),
                                wr.url(), "", wr.relevanceScore(), wr.snippet(), "web"));
                    }
                }

                // 10. Build prompt from compressed evidence (intent-aware + academic integrity)
                String systemPrompt = buildRagSystemPrompt(evidenceBlocks, request.query(), intentType, integrityViolation);

                // 11. Stream DeepSeek response
                JsonObject reqBody = new JsonObject();
                reqBody.addProperty("model", aiProps.openai().model());
                reqBody.addProperty("stream", true);

                JsonArray messages = new JsonArray();
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", systemPrompt);
                messages.add(sysMsg);
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", request.query());
                messages.add(userMsg);
                reqBody.add("messages", messages);

                okhttp3.RequestBody okBody = okhttp3.RequestBody.create(
                        reqBody.toString(), okhttp3.MediaType.parse("application/json; charset=utf-8"));
                Request okReq = new Request.Builder()
                        .url(aiProps.openai().baseUrl() + "/chat/completions")
                        .addHeader("Authorization", "Bearer " + aiProps.openai().apiKey())
                        .addHeader("Content-Type", "application/json")
                        .post(okBody).build();

                try (Response response = httpClient.newCall(okReq).execute()) {
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "unknown";
                        log.error("[RAG] DeepSeek API error: {} {}", response.code(), errBody);
                        outputStream.write(("AI服务暂时不可用，错误码: " + response.code()).getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                        return;
                    }
                    ResponseBody respBody = response.body();
                    if (respBody == null) return;
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(respBody.byteStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data: ")) continue;
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) break;
                            try {
                                JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();
                                JsonArray choices = chunk.getAsJsonArray("choices");
                                if (choices == null || choices.isEmpty()) continue;
                                JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
                                if (delta == null) continue;
                                if (delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull()) continue;
                                if (delta.has("content") && !delta.get("content").isJsonNull()) {
                                    String content = delta.get("content").getAsString();
                                    outputStream.write(content.getBytes(StandardCharsets.UTF_8));
                                    outputStream.flush();
                                    fullAnswer.append(content);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                // 12. Send citations as final SSE event
                citations = new ArrayList<>();
                for (int i = 0; i < evidenceBlocks.size(); i++) {
                    EvidenceBlock eb = evidenceBlocks.get(i);
                    String source = "web".equals(eb.docType) ? "web" : "course";
                    citations.add(new CitationInfo(i + 1, eb.docName, eb.chapterPath, eb.pageRange, eb.score, source));
                }
                String citationsJson = buildCitationsJson(citations);
                String citationEvent = "\n\n<!--CITATIONS:" + citationsJson + "-->";
                outputStream.write(citationEvent.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

            } catch (Exception e) {
                log.error("[RAG] error: {}", e.getMessage(), e);
                try {
                    outputStream.write("抱歉，AI助手暂时无法回答，请稍后再试。".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } catch (Exception ignored) {}
            } finally {
                CourseSpaceEntity cs = courseSpaceRepo.findById(request.courseSpaceId()).orElse(null);
                if (cs != null) {
                    saveQaLog(request, cs, fullAnswer.toString(), coverageScore, effectiveMode, usedWeb, intentType,
                            citations, studentId);
                }
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    // ── Feedback API ──

    record FeedbackRequest(Long qaLogId, Integer feedback) {}

    @PostMapping("/feedback")
    public ResponseEntity<?> submitFeedback(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody FeedbackRequest request) {
        var resolved = principalResolver.resolve(principal);
        if (request.qaLogId() == null || request.feedback() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "qaLogId and feedback are required"));
        }
        QaLogEntity log = qaLogRepo.findById(request.qaLogId()).orElse(null);
        if (log == null) {
            return ResponseEntity.status(404).body(Map.of("error", "qa_log not found"));
        }
        courseSpaceService.requireOwnedSpace(log.getCourseSpaceId(), resolved.userId());
        log.setFeedback(request.feedback());
        qaLogRepo.save(log);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Internal records ──

    record EvidenceBlock(long parentId, long docId, String docName,
                         String chapterPath, String pageRange, double score,
                         String evidenceText, String docType) {}

    record CitationInfo(int index, String docName, String chapterPath,
                        String pageRange, double score, String source) {}

    // ── MySQL Fallback Search (when Milvus + BM25 both empty) ──

    private List<FusionRankService.RankedParent> fallbackMySqlSearch(long courseSpaceId, String query, int topN) {
        // Simple keyword-based fallback: load parent chunks from MySQL and do basic matching
        List<DocChunkEntity> parents = docChunkRepo.findAllByCourseSpaceIdAndChunkType(courseSpaceId, "parent");
        if (parents.isEmpty()) return Collections.emptyList();

        String queryLower = query.toLowerCase();
        String[] keywords = queryLower.split("[\\s,，。、]+");

        Map<Long, String> docTypeMap = csDocRepo.findAllByCourseSpaceId(courseSpaceId).stream()
                .collect(Collectors.toMap(
                        CourseSpaceDocumentEntity::getDocumentId,
                        d -> d.getDocType() != null ? d.getDocType() : "textbook",
                        (a, b) -> a));
        List<FusionRankService.RankedParent> results = new ArrayList<>();
        for (DocChunkEntity chunk : parents) {
            double score = computeFallbackScore(query, chunk);
            if (score > 0.0) {
                String docType = docTypeMap.getOrDefault(chunk.getDocumentId(), "textbook");
                results.add(new FusionRankService.RankedParent(
                        chunk.getId(), chunk.getDocumentId(),
                        score, chunk.getChapterPath(), chunk.getPageRange(),
                        docType));
            }
        }
        results.sort((a, b) -> Double.compare(b.finalScore(), a.finalScore()));
        return results.stream().limit(topN).collect(Collectors.toList());
    }

    // ── RAG Prompt ──

    private String buildRagSystemPrompt(List<EvidenceBlock> blocks, String query,
                                         String intentType, boolean integrityViolation) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个数据结构课程的学习助手。请严格基于以下课程资料回答学生的问题。\n\n");

        if (integrityViolation) {
            sb.append("## ⚠️ 学术诚信约束\n");
            sb.append("检测到该请求可能涉及代写。你只能提供：\n");
            sb.append("1. 解题思路和方向\n");
            sb.append("2. 关键步骤的伪代码或结构\n");
            sb.append("3. 自查检查点\n");
            sb.append("绝对不能给出可直接提交的完整代码或报告。\n\n");
        }

        sb.append("## 课程资料\n\n");
        for (int i = 0; i < blocks.size(); i++) {
            EvidenceBlock b = blocks.get(i);
            sb.append("[").append(i + 1).append("] ");
            sb.append("《").append(b.docName).append("》");
            if (b.chapterPath != null && !b.chapterPath.isBlank()) sb.append(b.chapterPath);
            if (b.pageRange != null && !b.pageRange.isBlank()) sb.append(" (第").append(b.pageRange).append("页)");
            sb.append("\n").append(b.evidenceText).append("\n\n");
        }

        sb.append("## 回答要求\n");
        sb.append("1. 回答必须基于上述课程资料，关键结论需标注引用编号如 [1]\n");
        sb.append("2. 如果资料中没有相关内容，明确告知学生\"当前课程资料未覆盖此问题\"\n");

        sb.append("2.1 Never invent facts beyond the provided evidence. If evidence is insufficient, explicitly say the course materials do not cover the question.\n");

        // Intent-specific instructions
        if ("debug".equals(intentType)) {
            sb.append("3. 当前为调试类问题，请重点分析代码错误原因，给出修复思路\n");
        } else if ("procedure".equals(intentType)) {
            sb.append("3. 当前为操作步骤类问题，请按步骤清晰列出操作流程\n");
        } else if ("concept".equals(intentType)) {
            sb.append("3. 当前为概念理解类问题，请给出清晰的定义和解释\n");
        } else if ("summary".equals(intentType)) {
            sb.append("3. 当前为总结类问题，请系统梳理相关知识点\n");
        } else {
            sb.append("3. 回答格式：结论 → 解释/步骤 → 注意事项 → 引用来源\n");
        }

        sb.append("4. 使用中文回答，适当使用代码示例\n");
        sb.append("5. 对于代码类问题，只提供思路和关键步骤，不直接给出完整可提交的代码\n\n");
        sb.append("## 学生问题\n").append(query);
        return sb.toString();
    }

    // ── Citations JSON ──

    private double computeQuerySupportRatio(String query, List<EvidenceBlock> evidenceBlocks) {
        if (query == null || query.isBlank() || evidenceBlocks == null || evidenceBlocks.isEmpty()) {
            return 0.0;
        }
        Set<String> terms = extractRetrievalTerms(query);
        if (terms.isEmpty()) {
            return 0.0;
        }
        String combined = evidenceBlocks.stream()
                .map(EvidenceBlock::evidenceText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"))
                .toLowerCase(Locale.ROOT);
        long hitCount = terms.stream().filter(combined::contains).count();
        return (double) hitCount / terms.size();
    }

    private double computeFallbackScore(String query, DocChunkEntity chunk) {
        if (chunk == null || query == null || query.isBlank()) {
            return 0.0;
        }
        Set<String> terms = extractRetrievalTerms(query);
        if (terms.isEmpty()) {
            return 0.0;
        }
        String chapter = chunk.getChapterPath() == null ? "" : chunk.getChapterPath().toLowerCase(Locale.ROOT);
        String content = chunk.getContent() == null ? "" : chunk.getContent().toLowerCase(Locale.ROOT);
        String wholeQuery = normalizeRetrievalQuery(query);
        String combined = chapter + "\n" + content;
        long hitCount = terms.stream().filter(combined::contains).count();
        if (hitCount == 0 && !combined.contains(wholeQuery)) {
            return 0.0;
        }
        double overlapScore = (double) hitCount / terms.size();
        double exactScore = combined.contains(wholeQuery) ? 1.0 : 0.0;
        double chapterScore = chapter.isBlank() ? 0.0
                : (double) terms.stream().filter(chapter::contains).count() / terms.size();
        double score = 0.45 * exactScore + 0.40 * overlapScore + 0.15 * chapterScore;
        return score >= 0.08 ? score : 0.0;
    }

    private Set<String> extractRetrievalTerms(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        String normalized = normalizeRetrievalQuery(text);
        String[] tokens = normalized
                .split("[\\s\\uFF0C\\u3002\\uFF01\\uFF1F\\u3001\\uFF1B\\uFF1A\\u201C\\u201D\\u2018\\u2019\\uFF08\\uFF09\\u3010\\u3011\\u300A\\u300B\\p{Punct}]+");
        Set<String> stopTerms = Set.of("什么", "么是", "请问", "如何", "怎么", "为什", "为什么", "一下", "一下子", "介绍");
        Set<String> terms = new LinkedHashSet<>();
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.length() < 2) {
                continue;
            }
            if (trimmed.length() <= 6 && !stopTerms.contains(trimmed)) {
                terms.add(trimmed);
            }
            if (trimmed.length() > 2) {
                for (int i = 0; i < trimmed.length() - 1; i++) {
                    String gram = trimmed.substring(i, i + 2);
                    if (!stopTerms.contains(gram)) {
                        terms.add(gram);
                    }
                }
            }
        }
        return terms;
    }

    private String normalizeRetrievalQuery(String text) {
        String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        String[] prefixes = {"什么是", "什么叫", "什么叫做", "请问", "请解释", "解释一下", "介绍一下", "如何", "怎么", "为什么", "说说"};
        for (String prefix : prefixes) {
            if (normalized.startsWith(prefix)) {
                normalized = normalized.substring(prefix.length()).trim();
                break;
            }
        }
        return normalized;
    }

    private String buildCitationsJson(List<CitationInfo> citations) {
        JsonArray arr = new JsonArray();
        for (CitationInfo c : citations) {
            JsonObject obj = new JsonObject();
            obj.addProperty("index", c.index);
            obj.addProperty("docName", c.docName);
            obj.addProperty("chapterPath", c.chapterPath != null ? c.chapterPath : "");
            obj.addProperty("pageRange", c.pageRange != null ? c.pageRange : "");
            obj.addProperty("score", c.score);
            obj.addProperty("source", c.source);
            arr.add(obj);
        }
        return arr.toString();
    }

    // ── QaLog ──

    private void saveQaLog(RagChatRequest request, CourseSpaceEntity cs, String fullAnswer,
                           double coverageScore, String mode, boolean usedWeb,
                           String intentType, List<CitationInfo> citations,
                           String studentId) {
        try {
            String chunkIdsJson = citations.stream()
                    .map(c -> String.valueOf(c.index))
                    .collect(Collectors.joining(",", "[", "]"));
            String citationsJson = buildCitationsJson(citations);

            QaLogEntity logEntity = new QaLogEntity();
            logEntity.setCourseSpace(cs);
            logEntity.setStudentId(studentId == null || studentId.isBlank() ? "anonymous" : studentId);
            logEntity.setQuery(request.query());
            logEntity.setRetrievedChunkIds(chunkIdsJson);
            logEntity.setTop1Score(coverageScore);
            logEntity.setAnswerText(fullAnswer);
            logEntity.setCitationsJson(citationsJson);
            logEntity.setMode(mode);
            logEntity.setCoverageScore(coverageScore);
            logEntity.setUsedWeb(usedWeb);
            logEntity.setIntentType(intentType);

            qaLogRepo.save(logEntity);
            log.debug("[RAG] qa_log saved for courseSpaceId={}", request.courseSpaceId());
        } catch (Exception e) {
            log.error("[RAG] failed to save qa_log: {}", e.getMessage(), e);
        }
    }
}
