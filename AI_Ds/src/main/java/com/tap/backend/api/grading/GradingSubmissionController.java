package com.tap.backend.api.grading;

import com.tap.backend.service.GradingSubmissionService;
import com.tap.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/grading/submissions")
public class GradingSubmissionController {

    private final GradingSubmissionService submissionService;
    private final com.tap.backend.repo.UserRepository userRepo;

    public GradingSubmissionController(GradingSubmissionService submissionService,
                                        com.tap.backend.repo.UserRepository userRepo) {
        this.submissionService = submissionService;
        this.userRepo = userRepo;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.of(submissionService.getDetail(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/scores")
    public ResponseEntity<?> overrideScore(@PathVariable Long id,
                                            @AuthenticationPrincipal UserDetails principal,
                                            HttpServletRequest request,
                                            @RequestBody OverrideRequest req) {
        Long teacherId = resolveUserId(principal, request);
        try {
            var result = submissionService.overrideScore(id, req.dimensionId(),
                    req.newScore(), req.newComment(), req.reason(), teacherId);
            return ResponseEntity.ok(ApiResponse.of(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 生成 AI 总评（以任课教师口吻）
     */
    @PostMapping("/{id}/generate-review")
    public ResponseEntity<?> generateReview(@PathVariable Long id) {
        try {
            String review = submissionService.generateFinalReview(id);
            return ResponseEntity.ok(ApiResponse.of(Map.of("finalReviewComment", review)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 手动保存/编辑总评
     */
    @PutMapping("/{id}/review")
    public ResponseEntity<?> saveReview(@PathVariable Long id,
                                         @RequestBody Map<String, String> body) {
        try {
            submissionService.saveFinalReview(id, body.get("finalReviewComment"));
            return ResponseEntity.ok(ApiResponse.of(Map.of("message", "保存成功")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Long resolveUserId(UserDetails principal, HttpServletRequest request) {
        if (principal != null) {
            return userRepo.findByUsername(principal.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"))
                    .getId();
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            String username = (String) session.getAttribute("username");
            if (username != null) {
                return userRepo.findByUsername(username).orElseGet(() -> {
                    var u = new com.tap.backend.domain.user.UserEntity();
                    u.setUsername(username);
                    u.setDisplayName(username);
                    u.setRole(com.tap.backend.domain.user.UserRole.TEACHER);
                    return userRepo.save(u);
                }).getId();
            }
        }
        throw new IllegalArgumentException("未登录，请先登录");
    }

    public record OverrideRequest(Long dimensionId, BigDecimal newScore,
                                   String newComment, String reason) {}
}
