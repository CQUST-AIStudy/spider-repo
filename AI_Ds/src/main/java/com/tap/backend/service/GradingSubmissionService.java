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
                dimensionComments
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
        boolean hasAnnotatedReport = reportFileRepo.findBySubmissionIdAndFileType(
                submission.getId(),
                AnnotatedStudentReportService.FILE_TYPE_ANNOTATED_DOCX
        ).isPresent() || reportFileRepo.findBySubmissionIdAndFileType(
                submission.getId(),
                AnnotatedStudentReportService.FILE_TYPE_ANNOTATED_PDF
        ).isPresent();
        if (!hasAnnotatedReport) {
            return;
        }
        List<ScoreItemEntity> scores = scoreItemRepo.findAllBySubmissionId(submission.getId());
        Map<Long, String> dimensionNames = buildDimensionNameMap(submission);
        ExperimentContext experimentContext = extractExperimentContext(submission.getId());
        String teacherComment = buildTeacherComment(submission, scores, dimensionNames, experimentContext);
        createAnnotatedReport(submission, scores, teacherComment);
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

        BigDecimal total = submission.getTotalScore();
        String name = submission.getStudentName() != null ? submission.getStudentName() : "该同学";
        List<DimensionInsight> rankedInsights = buildRankedInsights(scores, dimensionNames);
        List<DimensionInsight> strengths = rankedInsights.stream()
                .sorted(Comparator.comparing(DimensionInsight::ratio).reversed())
                .limit(2)
                .toList();
        List<DimensionInsight> weaknesses = rankedInsights.stream()
                .sorted(Comparator.comparing(DimensionInsight::ratio))
                .filter(insight -> insight.ratio() < 0.9d)
                .limit(2)
                .toList();

        StringBuilder builder = new StringBuilder();
        builder.append("总体评价：")
                .append(name)
                .append("本次实验")
                .append(overallPerformanceText(total))
                .append("，本次批改重点关注你对实验原理、实现过程、结果分析和结论提炼的掌握情况，不以格式性细节作为主要扣分依据。");

        String requirementFocus = experimentContext.toReviewLine();
        if (!requirementFocus.isBlank()) {
            builder.append("\n实验要求对照：").append(requirementFocus);
        }

        builder.append("\n知识掌握情况：")
                .append(buildKnowledgeSummary(total, strengths, weaknesses));

        builder.append("\n主要优点：")
                .append(buildInsightSummary(strengths, "整体完成较稳定，说明你对实验核心内容已经具备基础理解。", false));

        builder.append("\n当前薄弱点：")
                .append(buildInsightSummary(weaknesses, "目前没有明显短板，后续可继续提升分析深度和迁移应用能力。", true));

        builder.append("\n改进建议：")
                .append(buildImprovementAdvice(weaknesses, strengths));
        return builder.toString().trim();
    }

    private String generateSimpleReview(GradingSubmissionEntity submission,
                                        List<ScoreItemEntity> scores,
                                        Map<Long, String> dimensionNames,
                                        ExperimentContext experimentContext) {
        BigDecimal total = submission.getTotalScore();
        String name = submission.getStudentName() != null ? submission.getStudentName() : "该同学";
        List<DimensionInsight> weaknesses = buildRankedInsights(scores, dimensionNames).stream()
                .sorted(Comparator.comparing(DimensionInsight::ratio))
                .filter(insight -> !insight.formatOnly())
                .limit(2)
                .toList();
        String requirementFocus = experimentContext.toReviewLine();
        return "总体评价：" + name + "本次实验" + overallPerformanceText(total)
                + (requirementFocus.isBlank() ? "" : "。本次实验应重点围绕" + requirementFocus)
                + "。后续请重点加强"
                + buildInsightSummary(weaknesses, "实验原理理解、结果解释和结论提炼", true)
                + "，写报告时优先说明自己为什么这样做、结果说明了什么。";
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
        StringBuilder builder = new StringBuilder();
        if (gradingSubmission.getFinalReviewComment() != null && !gradingSubmission.getFinalReviewComment().isBlank()) {
            builder.append(gradingSubmission.getFinalReviewComment().trim());
        }

        String requirementFocus = experimentContext.toTeacherCommentLine();
        if (!requirementFocus.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("实验要求：").append(requirementFocus);
        }

        List<String> scoreLines = scoreItems.stream()
                .sorted(Comparator.comparing(ScoreItemEntity::getDimensionId))
                .map(scoreItem -> formatScoreLine(scoreItem, dimensionNames))
                .filter(Objects::nonNull)
                .toList();
        if (!scoreLines.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("具体得分：\n");
            for (String line : scoreLines) {
                builder.append("- ").append(line).append('\n');
            }
        }

        if (builder.length() == 0) {
            builder.append("教师暂未填写评语。");
        }
        return builder.toString().trim();
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
        result = result.replaceFirst("^[：:：\\-\\s]+", "").trim();
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
            return "仍在等待评分结果";
        }
        if (total.compareTo(new BigDecimal("90")) >= 0) {
            return "表现较好，对核心知识和实验任务的掌握较为扎实";
        }
        if (total.compareTo(new BigDecimal("80")) >= 0) {
            return "完成度较高，已经体现出较好的知识理解和实验分析能力";
        }
        if (total.compareTo(new BigDecimal("75")) >= 0) {
            return "整体达到要求，核心内容基本掌握，但部分知识点的应用还不够稳定";
        }
        return "还有进一步提升空间，建议重点回看关键知识点和实验分析过程";
    }

    private String buildKnowledgeSummary(BigDecimal total,
                                         List<DimensionInsight> strengths,
                                         List<DimensionInsight> weaknesses) {
        if (total == null) {
            return "当前尚未形成完整评分，建议先查看分项结果。";
        }
        StringBuilder builder = new StringBuilder();
        if (!strengths.isEmpty()) {
            builder.append("在")
                    .append(joinDimensionNames(strengths))
                    .append("方面表现较稳，说明对相关知识点已有一定理解。");
        }
        if (!weaknesses.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append("后续应重点补强")
                    .append(joinDimensionNames(weaknesses))
                    .append("，尤其要把“会做”进一步提升到“会解释、会分析、会总结”。");
        }
        return builder.length() == 0
                ? "本次实验结果说明你对基础内容已有一定掌握，后续可继续提升分析深度和迁移应用能力。"
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
                        ? "需要进一步结合实验现象解释思路与依据"
                        : "完成情况较为稳定";
            }
            parts.add(insight.dimensionName() + "方面" + detail);
        }
        return String.join("；", parts) + "。";
    }

    private String buildImprovementAdvice(List<DimensionInsight> weaknesses, List<DimensionInsight> strengths) {
        List<String> advice = new ArrayList<>();
        if (weaknesses != null && !weaknesses.isEmpty()) {
            advice.add("先围绕" + joinDimensionNames(weaknesses) + "复盘实验过程，明确每一步为什么这样做、结果说明了什么。");
        }
        advice.add("撰写实验报告时优先说明原理理解、关键步骤、结果分析和问题定位，不必把精力过多放在实验环境、版本号或排版等格式性细节上。");
        if (strengths != null && !strengths.isEmpty()) {
            advice.add("把你在" + joinDimensionNames(strengths) + "中的已有优势继续保留下来，并尝试迁移到薄弱环节。");
        }
        return String.join("", advice);
    }

    private String joinDimensionNames(List<DimensionInsight> insights) {
        return insights.stream()
                .map(DimensionInsight::dimensionName)
                .distinct()
                .limit(3)
                .reduce((left, right) -> left + "、" + right)
                .orElse("相关维度");
    }

    private String formatScoreLine(ScoreItemEntity scoreItem, Map<Long, String> dimensionNames) {
        if (scoreItem == null) {
            return null;
        }
        String name = dimensionNames.getOrDefault(scoreItem.getDimensionId(), "维度" + scoreItem.getDimensionId());
        String scoreText = (scoreItem.getScore() == null ? "待评" : scoreItem.getScore().stripTrailingZeros().toPlainString())
                + "/" + (scoreItem.getMaxScore() == null ? "-" : scoreItem.getMaxScore().stripTrailingZeros().toPlainString());
        String comment = normalizeComment(scoreItem.getComment());
        if (comment.isBlank()) {
            return name + "：" + scoreText;
        }
        return name + "：" + scoreText + "。点评：" + comment;
    }

    private List<String> buildAnnotationHighlights(List<ScoreItemEntity> scores, Map<Long, String> dimensionNames) {
        List<DimensionInsight> ranked = buildRankedInsights(scores, dimensionNames);
        List<String> highlights = new ArrayList<>();
        ranked.stream()
                .sorted(Comparator.comparing(DimensionInsight::ratio).reversed())
                .limit(2)
                .forEach(insight -> highlights.add("优点：" + insight.dimensionName() + "表现较稳，" + conciseInsightComment(insight, false)));
        ranked.stream()
                .sorted(Comparator.comparing(DimensionInsight::ratio))
                .filter(insight -> !insight.formatOnly())
                .limit(2)
                .forEach(insight -> highlights.add("薄弱点：" + insight.dimensionName() + "需要加强，" + conciseInsightComment(insight, true)));
        return highlights.stream().distinct().limit(4).toList();
    }

    private String conciseInsightComment(DimensionInsight insight, boolean weak) {
        if (insight.comment().isBlank()) {
            return weak ? "建议补充原理说明、结果分析和结论依据。" : "说明你对该部分知识掌握较好。";
        }
        return insight.comment().endsWith("。") ? insight.comment() : insight.comment() + "。";
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
                parts.add("实验目的为" + objective);
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
