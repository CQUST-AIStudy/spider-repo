package com.tap.backend.api.grading;

import com.tap.backend.security.TeacherPrincipalResolver;
import com.tap.backend.security.UserPrincipal;
import com.tap.backend.service.GradingSubmissionService;
import com.tap.common.api.ApiResponse;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grading/submissions")
public class GradingSubmissionController {

    private final GradingSubmissionService submissionService;
    private final TeacherPrincipalResolver teacherPrincipalResolver;

    public GradingSubmissionController(
            GradingSubmissionService submissionService,
            TeacherPrincipalResolver teacherPrincipalResolver
    ) {
        this.submissionService = submissionService;
        this.teacherPrincipalResolver = teacherPrincipalResolver;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long teacherId = teacherPrincipalResolver.requireTeacherId(principal);
        try {
            return ResponseEntity.ok(ApiResponse.of(submissionService.getDetail(id, teacherId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/scores")
    public ResponseEntity<?> overrideScore(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody OverrideRequest req
    ) {
        Long teacherId = teacherPrincipalResolver.requireTeacherId(principal);
        try {
            var result = submissionService.overrideScore(
                    id,
                    req.dimensionId(),
                    req.newScore(),
                    req.newComment(),
                    req.reason(),
                    teacherId
            );
            return ResponseEntity.ok(ApiResponse.of(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/generate-review")
    public ResponseEntity<?> generateReview(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long teacherId = teacherPrincipalResolver.requireTeacherId(principal);
        try {
            String review = submissionService.generateFinalReview(id, teacherId);
            return ResponseEntity.ok(ApiResponse.of(Map.of("finalReviewComment", review)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/review")
    public ResponseEntity<?> saveReview(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body
    ) {
        Long teacherId = teacherPrincipalResolver.requireTeacherId(principal);
        try {
            submissionService.saveFinalReview(id, body.get("finalReviewComment"), teacherId);
            return ResponseEntity.ok(ApiResponse.of(Map.of("message", "Saved successfully")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    public record OverrideRequest(
            Long dimensionId,
            BigDecimal newScore,
            String newComment,
            String reason
    ) {}
}
