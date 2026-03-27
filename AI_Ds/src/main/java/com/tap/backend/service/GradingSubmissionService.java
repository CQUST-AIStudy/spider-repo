package com.tap.backend.service;

import com.tap.backend.audit.AuditAction;
import com.tap.backend.audit.AuditService;
import com.tap.backend.domain.grading.*;
import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.repo.*;
import com.tap.backend.ai.AiProvider;
import com.cqust.ai_server.dao.ExperimentDao;
import com.cqust.ai_server.dao.ScoreDao;
import com.cqust.ai_server.dao.StudentDao;
import com.cqust.ai_server.dao.SubmissionDao;
import com.cqust.ai_server.entity.Experiment;
import com.cqust.ai_server.entity.Score;
import com.cqust.ai_server.entity.Student;
import com.cqust.ai_server.entity.StudentCode;
import com.cqust.ai_server.entity.Submission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class GradingSubmissionService {

    private final GradingSubmissionRepository submissionRepo;
    private final ScoreItemRepository scoreItemRepo;
    private final EvidenceBlockRepository evidenceRepo;
    private final ScoreOverrideRepository overrideRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;
    private final AiProvider aiProvider;
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
        this.experimentDao = experimentDao;
        this.studentDao = studentDao;
        this.submissionDao = submissionDao;
        this.scoreDao = scoreDao;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDetail(Long submissionId, Long teacherId) {
        GradingSubmissionEntity sub = requireOwnedSubmission(submissionId, teacherId);

        List<ScoreItemEntity> scores = scoreItemRepo.findAllBySubmissionId(submissionId);
        List<EvidenceBlockEntity> evidence = evidenceRepo.findAllBySubmissionId(submissionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", sub.getId());
        result.put("taskId", sub.getTaskId());
        result.put("studentName", sub.getStudentName());
        result.put("className", sub.getClassName());
        result.put("studentNo", sub.getStudentNo());
        result.put("status", sub.getStatus().name());
        result.put("totalScore", sub.getTotalScore());
        result.put("finalReviewComment", sub.getFinalReviewComment());
        result.put("scores", scores.stream().map(this::scoreDto).toList());
        result.put("evidenceBlocks", evidence.stream().map(this::evidenceDto).toList());
        return result;
    }

    @Transactional
    public Map<String, Object> overrideScore(Long submissionId, Long dimensionId,
                                              BigDecimal newScore, String newComment,
                                              String reason, Long teacherId) {
        requireOwnedSubmission(submissionId, teacherId);
        ScoreItemEntity scoreItem = scoreItemRepo.findBySubmissionIdAndDimensionId(submissionId, dimensionId)
                .orElseThrow(() -> new IllegalArgumentException("Score item not found"));

        if (newScore.compareTo(BigDecimal.ZERO) < 0 || newScore.compareTo(scoreItem.getMaxScore()) > 0) {
            throw new IllegalArgumentException("Score must be between 0 and " + scoreItem.getMaxScore());
        }

        UserEntity teacher = userRepo.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        // Create override record
        ScoreOverrideEntity override = new ScoreOverrideEntity();
        override.setScoreItem(scoreItem);
        override.setTeacher(teacher);
        override.setOldScore(scoreItem.getScore());
        override.setNewScore(newScore);
        override.setOldComment(scoreItem.getComment());
        override.setNewComment(newComment);
        override.setReason(reason);
        overrideRepo.save(override);

        // Update score item
        scoreItem.setScore(newScore);
        scoreItem.setComment(newComment);
        scoreItem.setStatus(ScoreItemStatus.SCORED);
        scoreItemRepo.save(scoreItem);

        // Recalculate total score
        GradingSubmissionEntity sub = requireOwnedSubmission(submissionId, teacherId);
        BigDecimal total = recalculateTotal(submissionId);
        sub.setTotalScore(total);
        submissionRepo.save(sub);

        // Audit
        auditService.record(null, AuditAction.SCORE_OVERRIDE, "score_item",
                scoreItem.getId().toString(), Map.of("teacherId", teacherId,
                        "oldScore", override.getOldScore() != null ? override.getOldScore().toString() : "null",
                        "newScore", newScore.toString()), null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submissionId);
        result.put("totalScore", total);
        result.put("overrideId", override.getId());
        return result;
    }

    private BigDecimal recalculateTotal(Long submissionId) {
        List<ScoreItemEntity> scores = scoreItemRepo.findAllBySubmissionId(submissionId);
        BigDecimal total = BigDecimal.ZERO;
        for (ScoreItemEntity si : scores) {
            if (si.getScore() != null && si.getMaxScore().compareTo(BigDecimal.ZERO) > 0) {
                // (score / max_score) * weight
                BigDecimal ratio = si.getScore().divide(si.getMaxScore(), 6, RoundingMode.HALF_UP);
                total = total.add(ratio.multiply(BigDecimal.valueOf(si.getWeight())));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 生成 AI 总评（以任课教师口吻）
     */
    @Transactional
    public String generateFinalReview(Long submissionId, Long teacherId) {
        GradingSubmissionEntity sub = requireOwnedSubmission(submissionId, teacherId);
        List<ScoreItemEntity> scores = scoreItemRepo.findAllBySubmissionId(submissionId);

        StringBuilder scoreSummary = new StringBuilder();
        for (ScoreItemEntity si : scores) {
            scoreSummary.append(String.format("- 维度(ID:%d): 得分 %s/%s, 评语: %s\n",
                    si.getDimensionId(),
                    si.getScore() != null ? si.getScore().toString() : "N/A",
                    si.getMaxScore().toString(),
                    si.getComment() != null ? si.getComment() : "无"));
        }

        String prompt = "你是一位大学任课教师，请根据以下学生的实验报告评分情况，以任课教师的口吻撰写一段总评价。\n"
                + "要求：语气亲切专业，指出优点和不足，给出改进建议，150-300字。\n\n"
                + "学生姓名：" + (sub.getStudentName() != null ? sub.getStudentName() : "未知") + "\n"
                + "总分：" + (sub.getTotalScore() != null ? sub.getTotalScore().toString() : "N/A") + "\n\n"
                + "各维度评分：\n" + scoreSummary + "\n"
                + "请直接输出总评文字，不要输出JSON。";

        try {
            // Use the AiProvider's chat method via structured summary (reuse the AI infrastructure)
            var summaryResult = aiProvider.structuredSummary(
                    new AiProvider.StructuredSummaryInput("review", submissionId.toString(), prompt, 150, 300));
            // The structured summary returns JSON, but we want plain text
            // Fall back to direct call
            String review = summaryResult.researchProblemMotivation();
            if (review != null && !review.isBlank()) {
                sub.setFinalReviewComment(review);
                submissionRepo.save(sub);
                return review;
            }
        } catch (Exception ignored) {
            // structuredSummary may not work well for plain text, use fallback
        }

        // Fallback: generate a simple review based on scores
        String review = generateSimpleReview(sub, scores);
        sub.setFinalReviewComment(review);
        submissionRepo.save(sub);
        return review;
    }

    @Transactional
    public void saveFinalReview(Long submissionId, String review, Long teacherId) {
        GradingSubmissionEntity sub = requireOwnedSubmission(submissionId, teacherId);
        sub.setFinalReviewComment(review);
        submissionRepo.save(sub);
    }

    @Transactional
    public Map<String, Object> publishToStudentReport(Long submissionId, Long teacherId) {
        GradingSubmissionEntity sub = requireOwnedSubmission(submissionId, teacherId);
        Long experimentIdValue = sub.getTask().getExperimentId();
        if (experimentIdValue == null) {
            throw new IllegalArgumentException("This grading task is not bound to an experiment");
        }

        int experimentId = Math.toIntExact(experimentIdValue);
        Experiment experiment = experimentDao.findExperimentById(experimentId);
        if (experiment == null) {
            throw new IllegalArgumentException("Linked experiment was not found");
        }

        Student student = resolveStudent(sub);
        if (student == null) {
            throw new IllegalArgumentException("Matched student was not found in the legacy experiment system");
        }

        Submission latestSubmission = findLatestSubmission(student, experimentId);
        StudentCode studentCode = studentDao.findCodeByStudentIdAndExperimentId(student.getStudent_id(), experimentId);
        List<ScoreItemEntity> scores = scoreItemRepo.findAllBySubmissionId(submissionId);

        String legacyUsername = resolveLegacyUsername(student, sub);
        String report = buildPublishedReport(experiment, student, latestSubmission, sub, scores);

        Submission publishedSubmission = new Submission();
        publishedSubmission.setUsername(legacyUsername);
        publishedSubmission.setExperiment_id(experimentId);
        publishedSubmission.setCode(resolveCode(latestSubmission, studentCode));
        publishedSubmission.setReport(report);
        publishedSubmission.setSubmit_time(new Date());
        submissionDao.saveSubmission(publishedSubmission);

        Integer publishedScore = sub.getTotalScore() == null
                ? null
                : sub.getTotalScore().setScale(0, RoundingMode.HALF_UP).intValue();
        upsertLegacyScore(student, sub, experiment, publishedScore);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submissionId);
        result.put("experimentId", experimentId);
        result.put("studentId", student.getStudent_id());
        result.put("studentName", student.getName());
        result.put("publishedScore", publishedScore);
        result.put("report", report);
        return result;
    }

    private GradingSubmissionEntity requireOwnedSubmission(Long submissionId, Long teacherId) {
        GradingSubmissionEntity sub = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));
        if (!teacherId.equals(sub.getTask().getTeacherId())) {
            throw new IllegalArgumentException("Submission not found");
        }
        return sub;
    }

    private String generateSimpleReview(GradingSubmissionEntity sub, List<ScoreItemEntity> scores) {
        BigDecimal total = sub.getTotalScore();
        String name = sub.getStudentName() != null ? sub.getStudentName() : "同学";
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("同学，");

        if (total != null && total.compareTo(new BigDecimal("0.8")) >= 0) {
            sb.append("本次实验报告整体完成质量较好。");
        } else if (total != null && total.compareTo(new BigDecimal("0.6")) >= 0) {
            sb.append("本次实验报告基本达到要求，但仍有提升空间。");
        } else {
            sb.append("本次实验报告存在较多不足，需要认真改进。");
        }

        for (ScoreItemEntity si : scores) {
            if (si.getComment() != null && !si.getComment().isBlank()) {
                sb.append(si.getComment()).append(" ");
            }
        }
        sb.append("希望在后续实验中继续努力，不断提高。");
        return sb.toString();
    }

    private Map<String, Object> scoreDto(ScoreItemEntity si) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dimensionId", si.getDimensionId());
        m.put("score", si.getScore());
        m.put("maxScore", si.getMaxScore());
        m.put("weight", si.getWeight());
        m.put("comment", si.getComment());
        m.put("status", si.getStatus().name());
        m.put("evidenceIdsJson", si.getEvidenceIdsJson());
        return m;
    }

    private Map<String, Object> evidenceDto(EvidenceBlockEntity eb) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evidenceId", eb.getEvidenceId());
        m.put("kind", eb.getKind().name());
        m.put("page", eb.getPage());
        m.put("content", eb.getContent());
        m.put("confidence", eb.getConfidence());
        m.put("imageKey", eb.getImageKey());
        return m;
    }

    private Student resolveStudent(GradingSubmissionEntity sub) {
        if (sub.getStudentId() != null) {
            Student student = studentDao.findByStudentId(Math.toIntExact(sub.getStudentId()));
            if (student != null) {
                return student;
            }
        }
        if (sub.getStudentNo() != null && sub.getStudentNo().matches("\\d+")) {
            Student student = studentDao.findByStudentId(Integer.parseInt(sub.getStudentNo()));
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

    private String resolveLegacyUsername(Student student, GradingSubmissionEntity sub) {
        if (student.getUsername() != null && !student.getUsername().isBlank()) {
            return student.getUsername();
        }
        if (sub.getStudentNo() != null && !sub.getStudentNo().isBlank()) {
            return sub.getStudentNo();
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

    private void upsertLegacyScore(Student student, GradingSubmissionEntity gradingSubmission,
                                   Experiment experiment, Integer publishedScore) {
        String[] usernames = new String[] {
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
                                        Student student,
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
                + "## 实验目的\n待补充。\n\n"
                + "## 实验环境\n待补充。\n\n"
                + "## 实验内容\n待补充。\n\n"
                + "## 实验步骤\n待补充。\n\n"
                + "## 实验结果\n待补充。\n\n"
                + "## 实验总结\n待补充。";
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
                builder.append("- ").append(comment).append("\n");
            }
        }

        if (builder.length() == 0) {
            builder.append("教师暂未填写评语。");
        }
        return builder.toString().trim();
    }
}
