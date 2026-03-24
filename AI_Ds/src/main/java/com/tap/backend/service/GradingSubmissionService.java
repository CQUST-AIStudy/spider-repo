package com.tap.backend.service;

import com.tap.backend.audit.AuditAction;
import com.tap.backend.audit.AuditService;
import com.tap.backend.domain.grading.*;
import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.repo.*;
import com.tap.backend.ai.AiProvider;
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

    public GradingSubmissionService(GradingSubmissionRepository submissionRepo,
                                     ScoreItemRepository scoreItemRepo,
                                     EvidenceBlockRepository evidenceRepo,
                                     ScoreOverrideRepository overrideRepo,
                                     UserRepository userRepo,
                                     AuditService auditService,
                                     AiProvider aiProvider) {
        this.submissionRepo = submissionRepo;
        this.scoreItemRepo = scoreItemRepo;
        this.evidenceRepo = evidenceRepo;
        this.overrideRepo = overrideRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
        this.aiProvider = aiProvider;
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
}
