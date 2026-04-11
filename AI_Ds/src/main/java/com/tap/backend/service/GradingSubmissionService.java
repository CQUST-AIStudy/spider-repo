package com.tap.backend.service;

import com.cqust.ai_server.dao.ExperimentDao;
import com.cqust.ai_server.dao.ScoreDao;
import com.cqust.ai_server.dao.StudentDao;
import com.cqust.ai_server.dao.SubmissionDao;
import com.cqust.ai_server.entity.Experiment;
import com.cqust.ai_server.entity.Score;
import com.cqust.ai_server.entity.Student;
import com.cqust.ai_server.entity.StudentCode;
import com.cqust.ai_server.entity.Submission;
import com.tap.backend.ai.AiProvider;
import com.tap.backend.audit.AuditAction;
import com.tap.backend.audit.AuditService;
import com.tap.backend.domain.grading.EvidenceBlockEntity;
import com.tap.backend.domain.grading.GradingSubmissionEntity;
import com.tap.backend.domain.grading.GradingTraceEntity;
import com.tap.backend.domain.grading.ReportFileEntity;
import com.tap.backend.domain.grading.RubricDimensionEntity;
import com.tap.backend.domain.grading.ScoreItemEntity;
import com.tap.backend.domain.grading.ScoreItemStatus;
import com.tap.backend.domain.grading.ScoreOverrideEntity;
import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.infra.storage.ObjectStorageService;
import com.tap.backend.repo.EvidenceBlockRepository;
import com.tap.backend.repo.GradingSubmissionRepository;
import com.tap.backend.repo.GradingTraceRepository;
import com.tap.backend.repo.ReportFileRepository;
import com.tap.backend.repo.ScoreItemRepository;
import com.tap.backend.repo.ScoreOverrideRepository;
import com.tap.backend.repo.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradingSubmissionService {

    private final GradingSubmissionRepository submissionRepo;
    private final ScoreItemRepository scoreItemRepo;
    private final EvidenceBlockRepository evidenceRepo;
    private final ScoreOverrideRepository overrideRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;
    private final AiProvider aiProvider;
    private final GradingTraceRepository traceRepo;
    private final ReportFileRepository reportFileRepo;
    private final ObjectStorageService storageService;
    private final AnnotatedStudentReportService annotatedStudentReportService;
    private final ExperimentDao experimentDao;
    private final StudentDao studentDao;
    private final SubmissionDao submissionDao;
    private final ScoreDao scoreDao;

    public GradingSubmissionService(GradingSubmissionRepository submissionRepo,
                                    ScoreItemRepository scoreItemRepo,
                                    EvidenceBlockRepository evidenceRepo,
                                    ScoreOverrideRepository overrideRepo,
                                    UserRepository userRepo,
                                    AuditService auditService,
                                    AiProvider aiProvider,
                                    GradingTraceRepository traceRepo,
                                    ReportFileRepository reportFileRepo,
                                    ObjectStorageService storageService,
                                    AnnotatedStudentReportService annotatedStudentReportService,
                                    ExperimentDao experimentDao,
                                    StudentDao studentDao,
                                    SubmissionDao submissionDao,
                                    ScoreDao scoreDao) {
        this.submissionRepo = submissionRepo;
        this.scoreItemRepo = scoreItemRepo;
        this.evidenceRepo = evidenceRepo;
        this.overrideRepo = overrideRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
        this.aiProvider = aiProvider;
        this.traceRepo = traceRepo;
        this.reportFileRepo = reportFileRepo;
        this.storageService = storageService;
        this.annotatedStudentReportService = annotatedStudentReportService;
        this.experimentDao = experimentDao;
        this.studentDao = studentDao;
        this.submissionDao = submissionDao;
        this.scoreDao = scoreDao;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDetail(Long submissionId, Long teacherId) {
        GradingSubmissionEntity submission = requireOwnedSubmission(submissionId, teacherId);
        List<ScoreItemEntity> scores = scoreItemRepo.findAllBySubmissionId(submissionId);
        List<EvidenceBlockEntity> evidence = evidenceRepo.findAllBySubmissionId(submissionId);
        List<GradingTraceEntity> traces = traceRepo.findAllBySubmissionIdOrderByCreatedAtAsc(submissionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("taskId", submission.getTaskId());
        result.put("studentName", submission.getStudentName());
        result.put("originalFilename", submission.getOriginalFilename());
        result.put("className", submission.getClassName());
        result.put("studentNo", submission.getStudentNo());
        result.put("status", submission.getStatus().name());
        result.put("totalScore", submission.getTotalScore());
        result.put("finalReviewComment", submission.getFinalReviewComment());
        result.put("scores", scores.stream().map(this::scoreDto).toList());
        result.put("evidenceBlocks", evidence.stream().map(this::evidenceDto).toList());
        result.put("traces", traces.stream().map(this::traceDto).toList());
        result.put("reportFiles", reportFileRepo.findAllBySubmissionIdOrderByCreatedAtDesc(submissionId).stream()
                .map(this::reportFileDto)
                .toList());
        ReportFileEntity preferredReport = selectPreferredReport(submissionId);
        result.put("hasDownloadableReport", preferredReport != null);
        result.put("preferredReportFileType", preferredReport != null ? preferredReport.getFileType() : null);
        return result;
    }

    @Transactional
    public Map<String, Object> overrideScore(Long submissionId,
                                             Long dimensionId,
                                             BigDecimal newScore,
                                             String newComment,
                                             String reason,
                                             Long teacherId) {
        requireOwnedSubmission(submissionId, teacherId);
        ScoreItemEntity scoreItem = scoreItemRepo.findBySubmissionIdAndDimensionId(submissionId, dimensionId)
                .orElseThrow(() -> new IllegalArgumentException("Score item not found"));

        if (newScore.compareTo(BigDecimal.ZERO) < 0 || newScore.compareTo(scoreItem.getMaxScore()) > 0) {
            throw new IllegalArgumentException("Score must be between 0 and " + scoreItem.getMaxScore());
        }

        UserEntity teacher = userRepo.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        ScoreOverrideEntity override = new ScoreOverrideEntity();
        override.setScoreItem(scoreItem);
        override.setTeacher(teacher);
        override.setOldScore(scoreItem.getScore());
        override.setNewScore(newScore);
        override.setOldComment(scoreItem.getComment());
        override.setNewComment(newComment);
        override.setReason(reason);
        overrideRepo.save(override);

        scoreItem.setScore(newScore);
        scoreItem.setComment(newComment);
        scoreItem.setStatus(ScoreItemStatus.SCORED);
        scoreItemRepo.save(scoreItem);

        GradingSubmissionEntity submission = requireOwnedSubmission(submissionId, teacherId);
        BigDecimal total = recalculateTotal(submissionId);
        submission.setTotalScore(total);
        submissionRepo.save(submission);
        refreshAnnotatedReportIfPresent(submission);

        auditService.record(
                null,
                AuditAction.SCORE_OVERRIDE,
                "score_item",
                scoreItem.getId().toString(),
                Map.of(
                        "teacherId", teacherId,
                        "oldScore", override.getOldScore() != null ? override.getOldScore().toString() : "null",
                        "newScore", newScore.toString()
                ),
                null
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submissionId);
        result.put("totalScore", total);
        result.put("overrideId", override.getId());
        return result;
    }

    @Transactional
    public String generateFinalReview(Long submissionId, Long teacherId) {
        GradingSubmissionEntity submission = requireOwnedSubmission(submissionId, teacherId);
        List<ScoreItemEntity> scores = scoreItemRepo.findAllBySubmissionId(submissionId);
        Map<Long, String> dimensionNames = buildDimensionNameMap(submission);
        ExperimentContext experimentContext = extractExperimentContext(submissionId);

        String review = generateStructuredReview(submission, scores, dimensionNames, experimentContext);
        submission.setFinalReviewComment(review);
        submissionRepo.save(submission);
        refreshAnnotatedReportIfPresent(submission);
        return review;
    }

    @Transactional
    public void saveFinalReview(Long submissionId, String review, Long teacherId) {
        GradingSubmissionEntity submission = requireOwnedSubmission(submissionId, teacherId);
        submission.setFinalReviewComment(review);
        submissionRepo.save(submission);
        refreshAnnotatedReportIfPresent(submission);
    }

    @Transactional
    public Map<String, Object> publishToStudentReport(Long submissionId, Long teacherId) {
        GradingSubmissionEntity submission = requireOwnedSubmission(submissionId, teacherId);
        List<ScoreItemEntity> scores = scoreItemRepo.findAllBySubmissionId(submissionId);
        Map<Long, String> dimensionNames = buildDimensionNameMap(submission);
        ExperimentContext experimentContext = extractExperimentContext(submissionId);
        String teacherComment = buildTeacherComment(submission, scores, dimensionNames, experimentContext);
        if (submission.getFinalReviewComment() == null || submission.getFinalReviewComment().isBlank()) {
            submission.setFinalReviewComment(teacherComment);
            submissionRepo.save(submission);
        }
        AnnotatedReportArtifact annotatedReport = createAnnotatedReport(submission, scores, teacherComment);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submissionId);
        result.put("studentName", submission.getStudentName());
        result.put("annotatedFileType", annotatedReport.fileType());
        result.put("annotatedContentType", annotatedReport.contentType());
        result.put("annotatedObjectKey", annotatedReport.objectKey());

        List<String> warnings = new ArrayList<>();
        Long experimentIdValue = submission.getTask().getExperimentId();
        if (experimentIdValue == null) {
            warnings.add("Legacy experiment publish skipped: task is not bound to an experiment.");
        } else {
            publishLegacyReport(submission, scores, Math.toIntExact(experimentIdValue), result, warnings);
        }

        if (!warnings.isEmpty()) {
            result.put("warnings", warnings);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> ensureReviewAndAnnotatedReport(Long submissionId, Long teacherId) {
        GradingSubmissionEntity submission = requireOwnedSubmission(submissionId, teacherId);
        if (submission.getTotalScore() == null) {
            throw new IllegalStateException("Submission has not been scored yet");
        }

        boolean needsReview = submission.getFinalReviewComment() == null || submission.getFinalReviewComment().isBlank();
        boolean needsAnnotatedReport = !hasAnnotatedReport(submissionId);

        if (needsReview) {
            generateFinalReview(submissionId, teacherId);
            submission = requireOwnedSubmission(submissionId, teacherId);
        }

        if (needsAnnotatedReport || needsReview) {
            return publishToStudentReport(submissionId, teacherId);
        }

        ReportFileEntity preferredReport = selectPreferredReport(submissionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submissionId);
        result.put("studentName", submission.getStudentName());
        result.put("finalReviewComment", submission.getFinalReviewComment());
        result.put("annotatedReady", preferredReport != null);
        result.put("annotatedFileType", preferredReport != null ? preferredReport.getFileType() : null);
        result.put("annotatedObjectKey", preferredReport != null ? preferredReport.getObjectKey() : null);
        return result;
    }

    private void publishLegacyReport(GradingSubmissionEntity submission,
                                     List<ScoreItemEntity> scores,
                                     int experimentId,
                                     Map<String, Object> result,
                                     List<String> warnings) {
        Experiment experiment = experimentDao.findExperimentById(experimentId);
        if (experiment == null) {
            warnings.add("Legacy experiment publish skipped: linked experiment was not found.");
            return;
        }

        Student student = resolveStudent(submission);
        if (student == null) {
            warnings.add("Legacy experiment publish skipped: matched student was not found in the legacy system.");
            return;
        }

        Submission latestSubmission = findLatestSubmission(student, experimentId);
        StudentCode studentCode = studentDao.findCodeByStudentIdAndExperimentId(student.getStudent_id(), experimentId);
        String legacyUsername = resolveLegacyUsername(student, submission);
        String report = buildPublishedReport(experiment, latestSubmission, submission, scores);

        Submission publishedSubmission = new Submission();
        publishedSubmission.setUsername(legacyUsername);
        publishedSubmission.setExperiment_id(experimentId);
        publishedSubmission.setCode(resolveCode(latestSubmission, studentCode));
        publishedSubmission.setReport(report);
        publishedSubmission.setSubmit_time(new Date());
        submissionDao.saveSubmission(publishedSubmission);

        Integer publishedScore = submission.getTotalScore() == null
                ? null
                : submission.getTotalScore().setScale(0, RoundingMode.HALF_UP).intValue();
        upsertLegacyScore(student, submission, experiment, publishedScore);

        result.put("experimentId", experimentId);
        result.put("studentId", student.getStudent_id());
        result.put("publishedScore", publishedScore);
        result.put("report", report);
    }

    private AnnotatedReportArtifact createAnnotatedReport(GradingSubmissionEntity submission,
                                                          List<ScoreItemEntity> scores,
                                                          String teacherComment) {
        byte[] originalBytes = storageService.getBytes(submission.getPdfObjectKey());
        Map<Long, String> dimensionNames = buildDimensionNameMap(submission);
        List<String> dimensionComments = buildAnnotationHighlights(scores, dimensionNames);

        AnnotatedStudentReportService.RenderedReport rendered = annotatedStudentReportService.render(
                submission.getOriginalFilename(),
                originalBytes,
                submission.getStudentName(),
                submission.getTotalScore(),
                teacherComment,
                dimensionComments,
                resolveTeacherSignature(submission.getTask())
        );

        String objectKey = "grading/" + submission.getId() + "/annotated" + rendered.extension();
        storageService.putBytes(objectKey, rendered.bytes(), rendered.contentType());

        ReportFileEntity reportFile = reportFileRepo.findBySubmissionIdAndFileType(submission.getId(), rendered.fileType())
                .orElseGet(ReportFileEntity::new);
        reportFile.setTask(submission.getTask());
        reportFile.setSubmission(submission);
        reportFile.setFileType(rendered.fileType());
        reportFile.setObjectKey(objectKey);
        reportFileRepo.save(reportFile);

        return new AnnotatedReportArtifact(rendered.fileType(), rendered.contentType(), objectKey);
    }

    private void refreshAnnotatedReportIfPresent(GradingSubmissionEntity submission) {
        if (!hasAnnotatedReport(submission.getId())) {
            return;
        }
        List<ScoreItemEntity> scores = scoreItemRepo.findAllBySubmissionId(submission.getId());
        Map<Long, String> dimensionNames = buildDimensionNameMap(submission);
        ExperimentContext experimentContext = extractExperimentContext(submission.getId());
        String teacherComment = buildTeacherComment(submission, scores, dimensionNames, experimentContext);
        createAnnotatedReport(submission, scores, teacherComment);
    }

    public boolean hasAnnotatedReport(Long submissionId) {
        return reportFileRepo.findBySubmissionIdAndFileType(
                submissionId,
                AnnotatedStudentReportService.FILE_TYPE_ANNOTATED_DOCX
        ).isPresent() || reportFileRepo.findBySubmissionIdAndFileType(
                submissionId,
                AnnotatedStudentReportService.FILE_TYPE_ANNOTATED_PDF
        ).isPresent();
    }

    private ReportFileEntity selectPreferredReport(Long submissionId) {
        return reportFileRepo.findAllBySubmissionIdOrderByCreatedAtDesc(submissionId).stream()
                .max((left, right) -> Integer.compare(reportPriority(left), reportPriority(right)))
                .orElse(null);
    }

    private int reportPriority(ReportFileEntity report) {
        if (report == null || report.getFileType() == null) {
            return 0;
        }
        return switch (report.getFileType()) {
            case AnnotatedStudentReportService.FILE_TYPE_ANNOTATED_DOCX -> 4;
            case AnnotatedStudentReportService.FILE_TYPE_ANNOTATED_PDF -> 3;
            case "pdf" -> 2;
            default -> 1;
        };
    }

    private Map<String, Object> reportFileDto(ReportFileEntity reportFile) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", reportFile.getId());
        dto.put("fileType", reportFile.getFileType());
        dto.put("objectKey", reportFile.getObjectKey());
        dto.put("createdAt", reportFile.getCreatedAt() != null ? reportFile.getCreatedAt().toString() : null);
        return dto;
    }

    private GradingSubmissionEntity requireOwnedSubmission(Long submissionId, Long teacherId) {
        GradingSubmissionEntity submission = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));
        if (!teacherId.equals(submission.getTask().getTeacherId())) {
            throw new IllegalArgumentException("Submission not found");
        }
        return submission;
    }

    private BigDecimal recalculateTotal(Long submissionId) {
        BigDecimal total = BigDecimal.ZERO;
        for (ScoreItemEntity scoreItem : scoreItemRepo.findAllBySubmissionId(submissionId)) {
            if (scoreItem.getScore() == null || scoreItem.getMaxScore().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal ratio = scoreItem.getScore().divide(scoreItem.getMaxScore(), 6, RoundingMode.HALF_UP);
            total = total.add(ratio.multiply(BigDecimal.valueOf(scoreItem.getWeight())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
    private String generateStructuredReview(GradingSubmissionEntity submission,
                                            List<ScoreItemEntity> scores,
                                            Map<Long, String> dimensionNames,
                                            ExperimentContext experimentContext) {
        if (scores == null || scores.isEmpty()) {
            return generateSimpleReview(submission, List.of(), dimensionNames, experimentContext);
        }
        List<DimensionInsight> rankedInsights = buildRankedInsights(scores, dimensionNames);
        List<DimensionInsight> strengths = rankedInsights.stream()
                .sorted(Comparator.comparing(DimensionInsight::ratio).reversed())
                .limit(2)
                .toList();
        List<DimensionInsight> weaknesses = rankedInsights.stream()
                .sorted(Comparator.comparing(DimensionInsight::ratio))
                .filter(insight -> !insight.formatOnly())
                .filter(insight -> insight.ratio() < 0.9d)
                .limit(2)
                .toList();
        return composeConciseTeacherReview(submission, experimentContext, strengths, weaknesses);
    }
    private String generateSimpleReview(GradingSubmissionEntity submission,
                                        List<ScoreItemEntity> scores,
                                        Map<Long, String> dimensionNames,
                                        ExperimentContext experimentContext) {
        List<DimensionInsight> rankedInsights = buildRankedInsights(scores, dimensionNames);
        List<DimensionInsight> strengths = rankedInsights.stream()
                .sorted(Comparator.comparing(DimensionInsight::ratio).reversed())
                .limit(2)
                .toList();
        List<DimensionInsight> weaknesses = rankedInsights.stream()
                .sorted(Comparator.comparing(DimensionInsight::ratio))
                .filter(insight -> !insight.formatOnly())
                .limit(2)
                .toList();
        return composeConciseTeacherReview(submission, experimentContext, strengths, weaknesses);
    }
    private Map<String, Object> scoreDto(ScoreItemEntity scoreItem) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dimensionId", scoreItem.getDimensionId());
        data.put("score", scoreItem.getScore());
        data.put("maxScore", scoreItem.getMaxScore());
        data.put("weight", scoreItem.getWeight());
        data.put("comment", scoreItem.getComment());
        data.put("status", scoreItem.getStatus().name());
        data.put("evidenceIdsJson", scoreItem.getEvidenceIdsJson());
        return data;
    }

    private Map<String, Object> evidenceDto(EvidenceBlockEntity evidenceBlock) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("evidenceId", evidenceBlock.getEvidenceId());
        data.put("kind", evidenceBlock.getKind().name());
        data.put("page", evidenceBlock.getPage());
        data.put("content", evidenceBlock.getContent());
        data.put("confidence", evidenceBlock.getConfidence());
        data.put("imageKey", evidenceBlock.getImageKey());
        return data;
    }

    private Map<String, Object> traceDto(GradingTraceEntity trace) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("traceId", trace.getId());
        data.put("step", trace.getStep());
        data.put("status", trace.getStatus());
        data.put("durationMs", trace.getDurationMs());
        data.put("modelUsed", trace.getModelUsed());
        data.put("inputTokens", trace.getInputTokens());
        data.put("outputTokens", trace.getOutputTokens());
        data.put("errorMessage", trace.getErrorMessage());
        data.put("metadataJson", trace.getMetadataJson());
        data.put("createdAt", trace.getCreatedAt() != null ? trace.getCreatedAt().toString() : null);
        return data;
    }

    private Student resolveStudent(GradingSubmissionEntity submission) {
        if (submission.getStudentId() != null) {
            Student student = studentDao.findByStudentId(Math.toIntExact(submission.getStudentId()));
            if (student != null) {
                return student;
            }
        }
        if (submission.getStudentNo() != null && submission.getStudentNo().matches("\\d+")) {
            Student student = studentDao.findByStudentId(Integer.parseInt(submission.getStudentNo()));
            if (student != null) {
                return student;
            }
        }
        return null;
    }

    private Submission findLatestSubmission(Student student, int experimentId) {
        Submission submission = null;
        if (student.getUsername() != null && !student.getUsername().isBlank()) {
            submission = submissionDao.findByUsernameAndExperimentId(student.getUsername(), experimentId);
        }
        if (submission == null) {
            submission = submissionDao.findByUsernameAndExperimentId(String.valueOf(student.getStudent_id()), experimentId);
        }
        return submission;
    }

    private String resolveLegacyUsername(Student student, GradingSubmissionEntity submission) {
        if (student.getUsername() != null && !student.getUsername().isBlank()) {
            return student.getUsername();
        }
        if (submission.getStudentNo() != null && !submission.getStudentNo().isBlank()) {
            return submission.getStudentNo();
        }
        return String.valueOf(student.getStudent_id());
    }

    private String resolveCode(Submission latestSubmission, StudentCode studentCode) {
        if (latestSubmission != null && latestSubmission.getCode() != null && !latestSubmission.getCode().isBlank()) {
            return latestSubmission.getCode();
        }
        if (studentCode != null && studentCode.getCode() != null) {
            return studentCode.getCode();
        }
        return "";
    }
    private void upsertLegacyScore(Student student,
                                   GradingSubmissionEntity gradingSubmission,
                                   Experiment experiment,
                                   Integer publishedScore) {
        String[] usernames = new String[]{
                student.getUsername(),
                gradingSubmission.getStudentNo(),
                String.valueOf(student.getStudent_id())
        };

        Score score = null;
        String matchedUsername = null;
        for (String candidate : usernames) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            score = scoreDao.findByUsernameAndExperimentNum(candidate, experiment.getNum());
            if (score != null) {
                matchedUsername = candidate;
                break;
            }
        }

        if (matchedUsername == null) {
            matchedUsername = resolveLegacyUsername(student, gradingSubmission);
        }
        if (score == null) {
            score = new Score();
            score.setUsername(matchedUsername);
            score.setExperiment_id(experiment.getExperiment_id());
            score.setNum(experiment.getNum());
        }

        score.setReal_name(student.getName());
        score.setScore(publishedScore);
        score.setSubmit_time(new Date());
        score.setStatus("completed");
        if (score.getPlagiarism_rate() == null || score.getPlagiarism_rate().isBlank()) {
            score.setPlagiarism_rate("0.0");
        }

        if (score.getScore_id() > 0) {
            scoreDao.updateScore(score);
        } else {
            scoreDao.saveScore(score);
        }
    }

        private String buildPublishedReport(Experiment experiment,
                                        Submission latestSubmission,
                                        GradingSubmissionEntity gradingSubmission,
                                        List<ScoreItemEntity> scoreItems) {
        String baseReport = latestSubmission != null ? latestSubmission.getReport() : null;
        String normalizedBase = normalizeBaseReport(baseReport, experiment);
        Map<Long, String> dimensionNames = buildDimensionNameMap(gradingSubmission);
        ExperimentContext experimentContext = extractExperimentContext(gradingSubmission.getId());
        String teacherComment = buildTeacherComment(gradingSubmission, scoreItems, dimensionNames, experimentContext);
        String scoreText = gradingSubmission.getTotalScore() == null
                ? "待评"
                : gradingSubmission.getTotalScore().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " 分";

        return normalizedBase.trim()
                + "\n\n## 教师评分\n"
                + "本次教师评分：" + scoreText + "\n\n"
                + "## 教师评语\n"
                + teacherComment.trim()
                + "\n";
    }

        private String normalizeBaseReport(String baseReport, Experiment experiment) {
        String fallback = "# " + experiment.getName() + "实验报告\n\n"
                + "## 实验目的\n待补充\n\n"
                + "## 实验环境\n待补充\n\n"
                + "## 实验内容\n待补充\n\n"
                + "## 实验步骤\n待补充\n\n"
                + "## 实验结果\n待补充\n\n"
                + "## 实验总结\n待补充";
        String normalized = (baseReport == null || baseReport.isBlank()) ? fallback : baseReport.trim();
        normalized = normalized.replaceAll("(?s)\\n*## 教师评分\\n.*?(?=\\n## |\\z)", "");
        normalized = normalized.replaceAll("(?s)\\n*## 教师评语\\n.*?(?=\\n## |\\z)", "");
        return normalized.trim();
    }

    private String buildTeacherComment(GradingSubmissionEntity gradingSubmission,
                                       List<ScoreItemEntity> scoreItems,
                                       Map<Long, String> dimensionNames,
                                       ExperimentContext experimentContext) {
        String reviewBody = gradingSubmission.getFinalReviewComment();
        if (reviewBody == null || reviewBody.isBlank()) {
            reviewBody = generateStructuredReview(gradingSubmission, scoreItems, dimensionNames, experimentContext);
        }
        return compressTeacherReview(reviewBody);
    }

    private String composeConciseTeacherReview(GradingSubmissionEntity submission,
                                               ExperimentContext experimentContext,
                                               List<DimensionInsight> strengths,
                                               List<DimensionInsight> weaknesses) {
        BigDecimal total = submission.getTotalScore();
        String studentName = submission.getStudentName() == null || submission.getStudentName().isBlank()
                ? "该同学"
                : submission.getStudentName();
        String focus = summarizeExperimentFocus(experimentContext.toReviewLine(), 110);
        String strongNames = joinDimensionNames(strengths);
        String weakNames = joinDimensionNames(weaknesses);
        String knowledgeIssue = weaknesses.isEmpty()
                ? "对实验相关核心知识点已有基本掌握，但还可以继续把结论依据和知识迁移写得更扎实。"
                : "当前更需要加强的是" + weakNames + "，尤其要把相关原理、关键步骤为什么这样做、结果为什么能说明问题讲清楚。";
        String reportIssue = weaknesses.isEmpty()
                ? "报告撰写整体较完整，但仍建议进一步压缩与实验目标无关的铺陈，把目的、步骤、结果和结论之间的对应关系写得更紧凑。"
                : "报告撰写的主要问题不是格式，而是对实验任务与结果之间的对应关系概括不够集中，关键现象、原因分析和结论支撑还不够突出。";
        String strengthsLine = strengths.isEmpty()
                ? "优点是能够按要求完成主要实验流程，说明你具备基本的动手实现能力。"
                : "优点是你在" + strongNames + "方面表现相对稳定，说明对应知识点已经具备一定掌握基础，也能较好完成主要实验流程。";
        String extension = buildExtensionAdvice(experimentContext, weaknesses);

        String review = studentName + "本次实验" + overallPerformanceText(total) + "。"
                + (focus.isBlank() ? "" : "结合本次实验的目的与上机要求，重点应围绕" + focus + "来判断任务是否真正完成。")
                + "从当前报告看，" + buildTaskCompletionSummary(total, weaknesses, experimentContext)
                + knowledgeIssue
                + reportIssue
                + strengthsLine
                + "后续建议优先围绕薄弱环节做一次针对性复盘，重新梳理实验目标、关键步骤、现象解释和结论依据，避免只停留在“做出来了”，而没有说明“为什么成立、为什么有效”。"
                + extension
                + buildEncouragement(total, strengths);
        return compressTeacherReview(review);
    }

    private String compressTeacherReview(String reviewBody) {
        String normalized = safeText(reviewBody)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .replaceAll("分项得分参考[:：].*", "")
                .replaceAll("实验成绩[:：][^。；]*[。；]?", "")
                .trim();
        if (normalized.isBlank()) {
            normalized = "本次实验已完成批改。建议继续围绕实验任务完成度、原理理解、结果分析与结论表达进行复盘，把主要知识点真正学懂、讲清并落实到报告中。";
        }
        if (normalized.length() < 300) {
            normalized = normalized + "后续学习时可以把课堂示例与本次实验结果对照起来，重点检查自己是否真正理解了实验目的、方法选择和现象解释之间的关系，再用更凝练的语言总结收获与不足。";
        }
        if (normalized.length() > 500) {
            normalized = normalized.substring(0, 500);
        }
        if (!"。！？".contains(String.valueOf(normalized.charAt(normalized.length() - 1)))) {
            normalized = normalized + "。";
        }
        return normalized;
    }

    private String buildExtensionAdvice(ExperimentContext experimentContext, List<DimensionInsight> weaknesses) {
        String focus = summarizeExperimentFocus(experimentContext.toTeacherCommentLine(), 80);
        if (!focus.isBlank()) {
            return "课外延伸建议可以继续查阅与“" + focus + "”相关的教材示例、课堂代码或案例说明，把实验结论放回原理框架中再理解一遍。";
        }
        if (weaknesses != null && !weaknesses.isEmpty()) {
            return "课外延伸建议优先围绕" + joinDimensionNames(weaknesses) + "查阅教材对应章节、实验指导书示例或相关案例，先把概念和实验现象之间的联系打通。";
        }
        return "课外延伸建议继续结合教材、实验指导书和课堂案例，尝试把本次实验方法迁移到相近问题中，提升知识应用能力。";
    }

    private String resolveTeacherSignature(GradingTaskEntity task) {
        if (task == null) {
            return "任课教师";
        }
        if (task.getTeacherSignature() != null && !task.getTeacherSignature().isBlank()) {
            return task.getTeacherSignature().trim();
        }
        UserEntity teacher = task.getTeacher();
        if (teacher != null) {
            if (teacher.getDisplayName() != null && !teacher.getDisplayName().isBlank()) {
                return teacher.getDisplayName().trim();
            }
            if (teacher.getUsername() != null && !teacher.getUsername().isBlank()) {
                return teacher.getUsername().trim();
            }
        }
        return "任课教师";
    }

    private ExperimentContext extractExperimentContext(Long submissionId) {
        List<EvidenceBlockEntity> evidenceBlocks = evidenceRepo.findAllBySubmissionId(submissionId);
        List<String> lines = new ArrayList<>();
        for (EvidenceBlockEntity evidenceBlock : evidenceBlocks) {
            if (evidenceBlock == null || evidenceBlock.getContent() == null || evidenceBlock.getContent().isBlank()) {
                continue;
            }
            String kind = evidenceBlock.getKind() == null ? "" : evidenceBlock.getKind().name();
            if ("VLM_FAILED".equalsIgnoreCase(kind)) {
                continue;
            }
            for (String line : evidenceBlock.getContent().replace('\r', '\n').split("\n")) {
                String normalized = normalizeEvidenceLine(line);
                if (!normalized.isBlank()) {
                    lines.add(normalized);
                }
                if (lines.size() >= 160) {
                    break;
                }
            }
            if (lines.size() >= 160) {
                break;
            }
        }
        return new ExperimentContext(
                extractSectionSnippet(lines, List.of("实验目的", "实验目标", "目的")),
                extractSectionSnippet(lines, List.of("上机要求", "实验要求", "任务要求", "要求")),
                extractSectionSnippet(lines, List.of("实验内容", "实验任务", "实验原理", "主要内容"))
        );
    }

    private Map<Long, String> buildDimensionNameMap(GradingSubmissionEntity submission) {
        Map<Long, String> result = new HashMap<>();
        if (submission == null || submission.getTask() == null || submission.getTask().getRubric() == null) {
            return result;
        }
        for (RubricDimensionEntity dimension : submission.getTask().getRubric().getDimensions()) {
            result.put(dimension.getId(), dimension.getName());
        }
        return result;
    }

    private List<DimensionInsight> buildRankedInsights(List<ScoreItemEntity> scores, Map<Long, String> dimensionNames) {
        return scores.stream()
                .map(score -> toInsight(score, dimensionNames))
                .filter(Objects::nonNull)
                .toList();
    }

    private DimensionInsight toInsight(ScoreItemEntity scoreItem, Map<Long, String> dimensionNames) {
        if (scoreItem == null || scoreItem.getMaxScore() == null || scoreItem.getMaxScore().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        double ratio = scoreItem.getScore() == null
                ? 0d
                : scoreItem.getScore().divide(scoreItem.getMaxScore(), 4, RoundingMode.HALF_UP).doubleValue();
        String dimensionName = dimensionNames.getOrDefault(scoreItem.getDimensionId(), "维度" + scoreItem.getDimensionId());
        String comment = normalizeComment(scoreItem.getComment());
        boolean formatOnly = isFormatOnlyComment(comment);
        return new DimensionInsight(dimensionName, ratio, comment, scoreItem.getScore(), scoreItem.getMaxScore(), formatOnly);
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return "";
        }
        return comment.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private String normalizeEvidenceLine(String line) {
        if (line == null) {
            return "";
        }
        return line.replace('\u3000', ' ')
                .replaceAll("^[#>*\\-\\d.\\s]+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String extractSectionSnippet(List<String> lines, List<String> sectionKeywords) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        for (int i = 0; i < lines.size(); i++) {
            String current = lines.get(i);
            if (!containsAnyKeyword(current, sectionKeywords)) {
                continue;
            }
            List<String> parts = new ArrayList<>();
            parts.add(trimSectionPrefix(current, sectionKeywords));
            for (int j = i + 1; j < lines.size() && parts.size() < 4; j++) {
                String next = lines.get(j);
                if (looksLikeAnotherSection(next)) {
                    break;
                }
                if (!next.isBlank()) {
                    parts.add(next);
                }
            }
            String snippet = String.join("；", parts).replaceAll("；+", "；").trim();
            if (!snippet.isBlank()) {
                return snippet.length() > 120 ? snippet.substring(0, 120) : snippet;
            }
        }
        return "";
    }

    private boolean containsAnyKeyword(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String trimSectionPrefix(String text, List<String> keywords) {
        String result = text;
        for (String keyword : keywords) {
            result = result.replace(keyword, "");
        }
        result = result.replaceFirst("^[：:;；、,，.。\\-\\s]+", "").trim();
        return result.isBlank() ? text.trim() : result;
    }

    private boolean looksLikeAnotherSection(String text) {
        return containsAnyKeyword(text, List.of(
                "实验目的", "实验目标", "上机要求", "实验要求", "任务要求",
                "实验内容", "实验任务", "实验原理", "实验步骤", "实验结果", "实验总结", "结论"
        ));
    }

        private boolean isFormatOnlyComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return false;
        }
        String normalized = comment.toLowerCase();
        boolean hasFormatKeyword = Set.of(
                "格式", "排版", "版面", "字体", "实验环境", "环境配置",
                "python版本", "python 版本", "页码", "封面", "截图", "命名规范"
        ).stream().anyMatch(normalized::contains);
        boolean hasKnowledgeKeyword = Set.of(
                "原理", "方法", "步骤", "结果", "分析", "结论",
                "理解", "知识点", "思路", "实现", "数据", "误差", "问题", "验证"
        ).stream().anyMatch(normalized::contains);
        return hasFormatKeyword && !hasKnowledgeKeyword;
    }

    private String overallPerformanceText(BigDecimal total) {
        if (total == null) {
            return "\u4ecd\u5728\u7b49\u5f85\u8bc4\u5206\u7ed3\u679c";
        }
        if (total.compareTo(new BigDecimal("90")) >= 0) {
            return "\u5b8c\u6210\u8d28\u91cf\u8f83\u9ad8\uff0c\u5bf9\u5b9e\u9a8c\u4efb\u52a1\u548c\u6838\u5fc3\u77e5\u8bc6\u70b9\u7684\u638c\u63e1\u6bd4\u8f83\u624e\u5b9e";
        }
        if (total.compareTo(new BigDecimal("80")) >= 0) {
            return "\u6574\u4f53\u5b8c\u6210\u8f83\u597d\uff0c\u5df2\u7ecf\u4f53\u73b0\u51fa\u8f83\u7a33\u5b9a\u7684\u77e5\u8bc6\u7406\u89e3\u548c\u5b9e\u9a8c\u5206\u6790\u80fd\u529b";
        }
        if (total.compareTo(new BigDecimal("75")) >= 0) {
            return "\u5df2\u7ecf\u8fbe\u5230\u57fa\u672c\u8981\u6c42\uff0c\u4e3b\u8981\u5b9e\u9a8c\u4efb\u52a1\u80fd\u591f\u5b8c\u6210\uff0c\u4f46\u90e8\u5206\u77e5\u8bc6\u70b9\u7684\u7406\u89e3\u548c\u8868\u8fbe\u8fd8\u4e0d\u591f\u7a33\u5b9a";
        }
        return "\u8fd8\u6709\u7ee7\u7eed\u63d0\u5347\u7684\u7a7a\u95f4\uff0c\u5efa\u8bae\u56de\u5230\u5b9e\u9a8c\u6838\u5fc3\u4efb\u52a1\u548c\u5173\u952e\u77e5\u8bc6\u70b9\u4e0a\u518d\u505a\u4e00\u6b21\u590d\u76d8";
    }
    private String buildKnowledgeSummary(BigDecimal total,
                                         List<DimensionInsight> strengths,
                                         List<DimensionInsight> weaknesses) {
        if (total == null) {
            return "\u5f53\u524d\u8fd8\u6ca1\u6709\u5f62\u6210\u5b8c\u6574\u8bc4\u5206\uff0c\u5efa\u8bae\u5148\u7ed3\u5408\u5206\u9879\u7ed3\u679c\u67e5\u770b\u77e5\u8bc6\u638c\u63e1\u60c5\u51b5\u3002";
        }
        StringBuilder builder = new StringBuilder();
        if (!strengths.isEmpty()) {
            builder.append("\u5728")
                    .append(joinDimensionNames(strengths))
                    .append("\u65b9\u9762\u638c\u63e1\u8f83\u597d\uff0c\u8bf4\u660e\u4f60\u5bf9\u76f8\u5173\u77e5\u8bc6\u70b9\u5df2\u7ecf\u4e0d\u4ec5\u4f1a\u505a\uff0c\u800c\u4e14\u80fd\u591f\u8f83\u7a33\u5b9a\u5730\u5b8c\u6210\u5b9e\u9a8c\u6b65\u9aa4\u3002");
        }
        if (!weaknesses.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append("\u540e\u7eed\u9700\u8981\u91cd\u70b9\u8865\u5f3a")
                    .append(joinDimensionNames(weaknesses))
                    .append("\uff0c\u5c24\u5176\u8981\u628a\u201c\u80fd\u5b8c\u6210\u201d\u8fdb\u4e00\u6b65\u63d0\u5347\u5230\u201c\u80fd\u89e3\u91ca\u539f\u7406\u3001\u80fd\u5206\u6790\u7ed3\u679c\u3001\u80fd\u8bf4\u660e\u7ed3\u8bba\u201d\u3002");
        }
        return builder.length() == 0
                ? "\u672c\u6b21\u5b9e\u9a8c\u7ed3\u679c\u8bf4\u660e\u4f60\u5bf9\u57fa\u7840\u5185\u5bb9\u5df2\u6709\u4e00\u5b9a\u638c\u63e1\uff0c\u540e\u7eed\u53ef\u4ee5\u7ee7\u7eed\u63d0\u5347\u5206\u6790\u6df1\u5ea6\u548c\u77e5\u8bc6\u8fc1\u79fb\u80fd\u529b\u3002"
                : builder.toString();
    }
    private String buildInsightSummary(List<DimensionInsight> insights, String fallback, boolean focusWeakness) {
        if (insights == null || insights.isEmpty()) {
            return fallback;
        }
        List<String> parts = new ArrayList<>();
        for (DimensionInsight insight : insights) {
            String detail = insight.comment();
            if (detail.isBlank() || (focusWeakness && insight.formatOnly())) {
                detail = focusWeakness
                        ? "\u8fd8\u9700\u8981\u8fdb\u4e00\u6b65\u628a\u5b9e\u9a8c\u73b0\u8c61\u3001\u7ed3\u679c\u539f\u56e0\u548c\u7ed3\u8bba\u4f9d\u636e\u8bb2\u6e05\u695a"
                        : "\u5b8c\u6210\u60c5\u51b5\u8f83\u7a33\u5b9a\uff0c\u8bf4\u660e\u76f8\u5173\u77e5\u8bc6\u638c\u63e1\u6bd4\u8f83\u624e\u5b9e";
            }
            parts.add(insight.dimensionName() + "\u65b9\u9762" + detail);
        }
        return String.join("\uff1b", parts) + "\u3002";
    }
    private String buildImprovementAdvice(List<DimensionInsight> weaknesses, List<DimensionInsight> strengths) {
        List<String> advice = new ArrayList<>();
        if (weaknesses != null && !weaknesses.isEmpty()) {
            advice.add("\u5efa\u8bae\u5148\u56f4\u7ed5" + joinDimensionNames(weaknesses) + "\u590d\u76d8\u5b9e\u9a8c\u8fc7\u7a0b\uff0c\u660e\u786e\u6bcf\u4e00\u6b65\u4e3a\u4ec0\u4e48\u8fd9\u6837\u505a\u3001\u7ed3\u679c\u8bf4\u660e\u4e86\u4ec0\u4e48\u3001\u7ed3\u8bba\u662f\u5426\u548c\u5b9e\u9a8c\u76ee\u6807\u5bf9\u5e94\u3002");
        }
        advice.add("\u5199\u5b9e\u9a8c\u62a5\u544a\u65f6\u4f18\u5148\u8bf4\u660e\u5b9e\u9a8c\u4efb\u52a1\u662f\u5426\u5b8c\u6210\u3001\u5173\u952e\u539f\u7406\u662f\u5426\u7406\u89e3\u3001\u7ed3\u679c\u5206\u6790\u662f\u5426\u5230\u4f4d\u3001\u7ed3\u8bba\u662f\u5426\u80fd\u56de\u5e94\u5b9e\u9a8c\u8981\u6c42\uff0c\u4e0d\u5fc5\u628a\u7cbe\u529b\u8fc7\u591a\u653e\u5728\u73af\u5883\u7248\u672c\u6216\u683c\u5f0f\u7ec6\u8282\u4e0a\u3002");
        if (strengths != null && !strengths.isEmpty()) {
            advice.add("\u628a\u4f60\u5728" + joinDimensionNames(strengths) + "\u4e2d\u7684\u5df2\u6709\u4f18\u52bf\u7ee7\u7eed\u4fdd\u6301\u4e0b\u6765\uff0c\u5e76\u5c1d\u8bd5\u8fc1\u79fb\u5230\u76ee\u524d\u76f8\u5bf9\u8584\u5f31\u7684\u73af\u8282\u3002");
        }
        return String.join("", advice);
    }
    private String buildTaskCompletionSummary(BigDecimal total,
                                              List<DimensionInsight> weaknesses,
                                              ExperimentContext experimentContext) {
        String focus = summarizeExperimentFocus(experimentContext.toReviewLine(), 90);
        StringBuilder builder = new StringBuilder();
        if (total == null) {
            builder.append("\u5f53\u524d\u8fd8\u6ca1\u6709\u5b8c\u6574\u8bc4\u5206\u7ed3\u679c\uff0c\u5efa\u8bae\u5148\u67e5\u770b\u662f\u5426\u5df2\u7ecf\u8986\u76d6\u672c\u6b21\u5b9e\u9a8c\u7684\u6838\u5fc3\u4efb\u52a1\u3002");
        } else if (total.compareTo(new BigDecimal("85")) >= 0) {
            builder.append("\u4ece\u5f53\u524d\u62a5\u544a\u5185\u5bb9\u770b\uff0c\u4f60\u5df2\u7ecf\u6bd4\u8f83\u5b8c\u6574\u5730\u5b8c\u6210\u4e86\u672c\u6b21\u5b9e\u9a8c\u7684\u4e3b\u8981\u4efb\u52a1\uff0c\u5173\u952e\u6b65\u9aa4\u3001\u7ed3\u679c\u5c55\u793a\u548c\u57fa\u672c\u5206\u6790\u90fd\u6bd4\u8f83\u5230\u4f4d\u3002");
        } else if (total.compareTo(new BigDecimal("75")) >= 0) {
            builder.append("\u4f60\u5df2\u7ecf\u5b8c\u6210\u4e86\u672c\u6b21\u5b9e\u9a8c\u7684\u4e3b\u8981\u4efb\u52a1\uff0c\u4f46\u90e8\u5206\u5173\u952e\u6b65\u9aa4\u8bf4\u660e\u3001\u7ed3\u679c\u5206\u6790\u6216\u7ed3\u8bba\u56de\u5e94\u8fd8\u53ef\u4ee5\u518d\u5145\u5b9e\u4e00\u4e9b\u3002");
        } else {
            builder.append("\u76ee\u524d\u53ea\u5b8c\u6210\u4e86\u672c\u6b21\u5b9e\u9a8c\u4e2d\u7684\u90e8\u5206\u4efb\u52a1\uff0c\u5173\u952e\u7ed3\u679c\u5c55\u793a\u3001\u539f\u56e0\u5206\u6790\u6216\u7ed3\u8bba\u95ed\u73af\u8fd8\u4e0d\u591f\u5b8c\u6574\u3002");
        }
        if (!focus.isBlank()) {
            builder.append(" \u672c\u6b21\u5b9e\u9a8c\u91cd\u70b9\u5305\u62ec").append(focus).append("\u3002");
        }
        if (weaknesses != null && !weaknesses.isEmpty()) {
            builder.append(" \u5f53\u524d\u6700\u9700\u8981\u8865\u5f3a\u7684\u73af\u8282\u662f").append(joinDimensionNames(weaknesses)).append("\u3002");
        }
        return builder.toString().trim();
    }
    private String buildEncouragement(BigDecimal total, List<DimensionInsight> strengths) {
        if (total == null) {
            return "\u5148\u628a\u5b9e\u9a8c\u4efb\u52a1\u5b8c\u6574\u505a\u51fa\u6765\uff0c\u518d\u56de\u5934\u68c0\u67e5\u77e5\u8bc6\u70b9\u548c\u7ed3\u8bba\u8868\u8fbe\uff0c\u4f1a\u66f4\u5bb9\u6613\u770b\u5230\u81ea\u5df1\u7684\u8fdb\u6b65\u3002";
        }
        if (total.compareTo(new BigDecimal("90")) >= 0) {
            return "\u4f60\u5df2\u7ecf\u5177\u5907\u6bd4\u8f83\u624e\u5b9e\u7684\u5b9e\u9a8c\u57fa\u7840\uff0c\u7ee7\u7eed\u4fdd\u6301\u8fd9\u79cd\u5b8c\u6210\u8d28\u91cf\uff0c\u5e76\u5c1d\u8bd5\u628a\u5206\u6790\u5199\u5f97\u66f4\u6df1\u5165\uff0c\u4f1a\u66f4\u6709\u8bf4\u670d\u529b\u3002";
        }
        if (total.compareTo(new BigDecimal("80")) >= 0) {
            return "\u6574\u4f53\u57fa\u7840\u5df2\u7ecf\u4e0d\u9519\uff0c\u53ea\u8981\u7ee7\u7eed\u628a\u4f18\u52bf\u90e8\u5206\u4fdd\u6301\u4f4f\uff0c\u518d\u628a\u5206\u6790\u548c\u603b\u7ed3\u5199\u5f97\u66f4\u900f\u5f7b\uff0c\u6210\u7ee9\u8fd8\u4f1a\u7ee7\u7eed\u63d0\u5347\u3002";
        }
        if (total.compareTo(new BigDecimal("75")) >= 0) {
            return "\u4f60\u5df2\u7ecf\u5177\u5907\u5b8c\u6210\u5b9e\u9a8c\u4efb\u52a1\u7684\u57fa\u7840\uff0c\u540e\u7eed\u53ea\u8981\u628a\u8584\u5f31\u77e5\u8bc6\u70b9\u518d\u8865\u4e00\u8865\uff0c\u628a\u7ed3\u679c\u5206\u6790\u5199\u624e\u5b9e\uff0c\u63d0\u5347\u4f1a\u5f88\u660e\u663e\u3002";
        }
        if (strengths != null && !strengths.isEmpty()) {
            return "\u867d\u7136\u5f53\u524d\u8fd8\u6709\u63d0\u5347\u7a7a\u95f4\uff0c\u4f46\u4f60\u5728" + joinDimensionNames(strengths) + "\u4e0a\u5df2\u7ecf\u6709\u4e00\u5b9a\u57fa\u7840\uff0c\u7ee7\u7eed\u8865\u9f50\u5173\u952e\u4efb\u52a1\uff0c\u540e\u9762\u4f1a\u8fdb\u6b65\u5f97\u66f4\u5feb\u3002";
        }
        return "\u5148\u4e0d\u8981\u7740\u6025\uff0c\u628a\u5b9e\u9a8c\u76ee\u6807\u3001\u5173\u952e\u6b65\u9aa4\u548c\u7ed3\u679c\u89e3\u91ca\u4e00\u9879\u4e00\u9879\u8865\u5b8c\u6574\uff0c\u5b66\u4e60\u6548\u679c\u4f1a\u9010\u6b65\u7a33\u5b9a\u4e0b\u6765\u3002";
    }
    private String summarizeExperimentFocus(String focus, int maxLength) {
        if (focus == null || focus.isBlank()) {
            return "";
        }
        String normalized = focus.replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }
    private String joinDimensionNames(List<DimensionInsight> insights) {
        return insights.stream()
                .map(DimensionInsight::dimensionName)
                .distinct()
                .limit(3)
                .reduce((left, right) -> left + "\u3001" + right)
                .orElse("\u76f8\u5173\u7ef4\u5ea6");
    }
    private String formatScoreLine(ScoreItemEntity scoreItem, Map<Long, String> dimensionNames) {
        if (scoreItem == null) {
            return null;
        }
        String name = dimensionNames.getOrDefault(scoreItem.getDimensionId(), "\u7ef4\u5ea6" + scoreItem.getDimensionId());
        String scoreText = (scoreItem.getScore() == null ? "\u5f85\u8bc4" : scoreItem.getScore().stripTrailingZeros().toPlainString())
                + "/" + (scoreItem.getMaxScore() == null ? "-" : scoreItem.getMaxScore().stripTrailingZeros().toPlainString());
        String comment = normalizeComment(scoreItem.getComment());
        if (comment.isBlank()) {
            return name + "\uff1a" + scoreText;
        }
        return name + "\uff1a" + scoreText + "\u3002\u70b9\u8bc4\uff1a" + comment;
    }
    private List<String> buildAnnotationHighlights(List<ScoreItemEntity> scores, Map<Long, String> dimensionNames) {
        List<DimensionInsight> ranked = buildRankedInsights(scores, dimensionNames);
        List<String> highlights = new ArrayList<>();
        ranked.stream()
                .sorted(Comparator.comparing(DimensionInsight::ratio).reversed())
                .limit(2)
                .forEach(insight -> highlights.add("\u4f18\u70b9\uff1a" + insight.dimensionName() + "\u638c\u63e1\u8f83\u7a33\uff0c" + conciseInsightComment(insight, false)));
        ranked.stream()
                .sorted(Comparator.comparing(DimensionInsight::ratio))
                .filter(insight -> !insight.formatOnly())
                .limit(2)
                .forEach(insight -> highlights.add("\u5efa\u8bae\uff1a" + insight.dimensionName() + "\u8fd8\u9700\u52a0\u5f3a\uff0c" + conciseInsightComment(insight, true)));
        return highlights.stream().distinct().limit(4).toList();
    }
    private String conciseInsightComment(DimensionInsight insight, boolean weak) {
        if (insight.comment().isBlank()) {
            return weak
                    ? "\u5efa\u8bae\u8865\u5145\u539f\u7406\u8bf4\u660e\u3001\u7ed3\u679c\u5206\u6790\u548c\u7ed3\u8bba\u4f9d\u636e\u3002"
                    : "\u8bf4\u660e\u4f60\u5bf9\u8fd9\u4e00\u90e8\u5206\u77e5\u8bc6\u638c\u63e1\u8f83\u597d\u3002";
        }
        return insight.comment().endsWith("\u3002") ? insight.comment() : insight.comment() + "\u3002";
    }
    private record AnnotatedReportArtifact(String fileType, String contentType, String objectKey) {}

    private record DimensionInsight(String dimensionName,
                                    double ratio,
                                    String comment,
                                    BigDecimal score,
                                    BigDecimal maxScore,
                                    boolean formatOnly) {}

        private record ExperimentContext(String objective, String requirements, String contents) {
        private String toReviewLine() {
            List<String> parts = new ArrayList<>();
            if (objective != null && !objective.isBlank()) {
                parts.add("实验目的包括" + objective);
            }
            if (requirements != null && !requirements.isBlank()) {
                parts.add("上机要求包括" + requirements);
            }
            if (contents != null && !contents.isBlank()) {
                parts.add("实验内容涉及" + contents);
            }
            return String.join("；", parts);
        }

        private String toTeacherCommentLine() {
            List<String> parts = new ArrayList<>();
            if (objective != null && !objective.isBlank()) {
                parts.add("目的：" + objective);
            }
            if (requirements != null && !requirements.isBlank()) {
                parts.add("要求：" + requirements);
            }
            if (contents != null && !contents.isBlank()) {
                parts.add("内容：" + contents);
            }
            return String.join("；", parts);
        }
    }
}


