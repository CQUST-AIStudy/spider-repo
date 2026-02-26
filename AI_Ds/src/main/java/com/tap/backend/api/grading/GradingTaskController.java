package com.tap.backend.api.grading;

import com.tap.backend.domain.grading.GradingTaskEntity;
import com.tap.backend.domain.grading.GradingTaskStatus;
import com.tap.backend.domain.grading.GradingSubmissionEntity;
import com.tap.backend.service.GradingTaskService;
import com.tap.common.api.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.*;

@RestController
@RequestMapping("/api/grading/tasks")
public class GradingTaskController {

    private final GradingTaskService taskService;
    private final com.tap.backend.repo.UserRepository userRepo;

    public GradingTaskController(GradingTaskService taskService,
                                  com.tap.backend.repo.UserRepository userRepo) {
        this.taskService = taskService;
        this.userRepo = userRepo;
    }

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal UserDetails principal,
                                    HttpServletRequest request,
                                    @RequestParam("files") MultipartFile[] files,
                                    @RequestParam("rubricId") Long rubricId,
                                    @RequestParam(value = "experimentId", required = false) Long experimentId,
                                    @RequestParam(value = "classId", required = false) Long classId,
                                    @RequestParam(value = "scoreRangeMin", required = false) java.math.BigDecimal scoreRangeMin,
                                    @RequestParam(value = "scoreRangeMax", required = false) java.math.BigDecimal scoreRangeMax) {
        Long teacherId = resolveUserId(principal, request);
        try {
            var result = taskService.createTask(teacherId, experimentId, classId, rubricId,
                    scoreRangeMin, scoreRangeMax, files);
            return ResponseEntity.ok(ApiResponse.of(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal UserDetails principal,
                                  HttpServletRequest request,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String status) {
        Long teacherId = resolveUserId(principal, request);
        GradingTaskStatus statusEnum = status != null ? GradingTaskStatus.valueOf(status) : null;
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<GradingTaskEntity> tasks = taskService.getTaskList(teacherId, statusEnum, pageable);

        var content = tasks.getContent().stream().map(this::toListDto).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("totalElements", tasks.getTotalElements());
        result.put("totalPages", tasks.getTotalPages());
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        try {
            GradingTaskEntity task = taskService.getTaskDetail(id);
            List<GradingSubmissionEntity> subs = taskService.getTaskSubmissions(id);

            Map<String, Object> dto = new LinkedHashMap<>(toListDto(task));
            dto.put("submissions", subs.stream().map(this::toSubDto).toList());
            return ResponseEntity.ok(ApiResponse.of(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retry(@PathVariable Long id) {
        try {
            taskService.retryFailed(id);
            return ResponseEntity.ok(ApiResponse.of(Map.of("message", "Retry initiated")));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                     @AuthenticationPrincipal UserDetails principal,
                                     HttpServletRequest request) {
        Long teacherId = resolveUserId(principal, request);
        try {
            taskService.deleteTask(id, teacherId);
            return ResponseEntity.ok(ApiResponse.of(Map.of("message", "删除成功")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 导出 Excel：教师选择学生，选择是否包含评语
     */
    @PostMapping("/{id}/export-excel")
    public ResponseEntity<?> exportExcel(@PathVariable Long id,
                                          @RequestBody ExcelExportRequest req) {
        try {
            byte[] excel = taskService.exportExcel(id, req.submissionIds(), req.includeComments());
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=grading-export-" + id + ".xlsx")
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(excel);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    public record ExcelExportRequest(List<Long> submissionIds, boolean includeComments) {}

    /**
     * 兼容双认证体系：优先 JWT principal，回退到 HttpSession (AI_Ds 登录)
     */
    private Long resolveUserId(UserDetails principal, HttpServletRequest request) {
        // 1. JWT 认证 (tap 体系)
        if (principal != null) {
            return userRepo.findByUsername(principal.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"))
                    .getId();
        }
        // 2. Session 认证 (AI_Ds 体系) — 通过 username 查找或自动创建 tap_user
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

    private Map<String, Object> toListDto(GradingTaskEntity t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", t.getId());
        m.put("status", t.getStatus().name());
        m.put("totalCount", t.getTotalCount());
        m.put("completedCount", t.getCompletedCount());
        m.put("failedCount", t.getFailedCount());
        m.put("rubricId", t.getRubricId());
        m.put("experimentId", t.getExperimentId());
        m.put("createdAt", t.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> toSubDto(GradingSubmissionEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("submissionId", s.getId());
        m.put("studentName", s.getStudentName());
        m.put("className", s.getClassName());
        m.put("studentNo", s.getStudentNo());
        m.put("status", s.getStatus().name());
        m.put("totalScore", s.getTotalScore());
        m.put("originalFilename", s.getOriginalFilename());
        m.put("finalReviewComment", s.getFinalReviewComment());
        return m;
    }
}
