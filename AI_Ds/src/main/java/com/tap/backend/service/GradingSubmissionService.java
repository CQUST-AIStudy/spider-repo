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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

        StringBuilder scoreSummary = new StringBuilder();
        for (ScoreItemEntity score : scores) {
            scoreSummary.append("- 维度(ID:")
                    .append(score.getDimensionId())
                    .append("): 得分 ")
                    .append(score.getScore() != null ? score.getScore() : "N/A")
                    .append("/")
                    .append(score.getMaxScore())
                    .append(", 评语: ")
                    .append(score.getComment() != null ? score.getComment() : "无")
                    .append('\n');
        }

        String prompt = "你是一位实验课程教师，请根据以下分项评分为学生撰写简洁、客观、可执行的总评。\n"
                + "要求：控制在 80 到 220 字，先概括完成度，再指出优点和不足，最后给出改进建议。\n\n"
                + "学生姓名：" + (submission.getStudentName() != null ? submission.getStudentName() : "未知") + "\n"
                + "总分：" + (submission.getTotalScore() != null ? submission.getTotalScore() : "N/A") + "\n"
                + "分项评分：\n" + scoreSummary;

        try {
            var summaryResult = aiProvider.structuredSummary(
                    new AiProvider.StructuredSummaryInput("review", submissionId.toString(), prompt, 120, 260));
            String review = summaryResult.researchProblemMotivation();
            if (review != null && !review.isBlank()) {
                submission.setFinalReviewComment(review);
                submissionRepo.save(submission);
                refreshAnnotatedReportIfPresent(submission);
                return review;
            }
        } catch (Exception ignored) {
        }

        String review = generateSimpleReview(submission, scores);
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
        String teacherComment = buildTeacherComment(submission, scores);
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
        List<String> dimensionComments = scores.stream()
                .map(ScoreItemEntity::getComment)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(comment -> !comment.isBlank())
                .limit(4)
                .toList();

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
        String teacherComment = buildTeacherComment(submission, scores);
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

    private String generateSimpleReview(GradingSubmissionEntity submission, List<ScoreItemEntity> scores) {
        BigDecimal total = submission.getTotalScore();
        String name = submission.getStudentName() != null ? submission.getStudentName() : "同学";
        StringBuilder builder = new StringBuilder();
        builder.append(name).append("：");

        if (total != null && total.compareTo(new BigDecimal("85")) >= 0) {
            builder.append("本次实验报告整体完成质量较好。");
        } else if (total != null && total.compareTo(new BigDecimal("70")) >= 0) {
            builder.append("本次实验报告基本达到要求，但仍有进一步打磨空间。");
        } else {
            builder.append("本次实验报告存在较多不足，需要继续完善。");
        }

        for (ScoreItemEntity score : scores) {
            if (score.getComment() != null && !score.getComment().isBlank()) {
                builder.append(score.getComment().trim()).append(' ');
            }
        }
        builder.append("建议后续重点补强过程说明、结果分析和实验结论。");
        return builder.toString().trim();
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
        String teacherComment = buildTeacherComment(gradingSubmission, scoreItems);
        String scoreText = gradingSubmission.getTotalScore() == null
                ? "待评分"
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

    private String buildTeacherComment(GradingSubmissionEntity gradingSubmission, List<ScoreItemEntity> scoreItems) {
        StringBuilder builder = new StringBuilder();
        if (gradingSubmission.getFinalReviewComment() != null && !gradingSubmission.getFinalReviewComment().isBlank()) {
            builder.append(gradingSubmission.getFinalReviewComment().trim());
        }

        List<String> dimensionComments = scoreItems.stream()
                .map(ScoreItemEntity::getComment)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(comment -> !comment.isBlank())
                .toList();
        if (!dimensionComments.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("分项意见：\n");
            for (String comment : dimensionComments) {
                builder.append("- ").append(comment).append('\n');
            }
        }

        if (builder.length() == 0) {
            builder.append("教师暂未填写评语。");
        }
        return builder.toString().trim();
    }

    private record AnnotatedReportArtifact(String fileType, String contentType, String objectKey) {}
}
