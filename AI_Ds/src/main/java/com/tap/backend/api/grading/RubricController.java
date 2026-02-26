package com.tap.backend.api.grading;

import com.tap.backend.domain.grading.GradingRubricEntity;
import com.tap.backend.domain.grading.RubricDimensionEntity;
import com.tap.backend.service.RubricService;
import com.tap.backend.service.RubricService.DimensionInput;
import com.tap.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/grading/rubrics")
public class RubricController {

    private final RubricService rubricService;
    private final com.tap.backend.repo.UserRepository userRepo;

    public RubricController(RubricService rubricService,
                            com.tap.backend.repo.UserRepository userRepo) {
        this.rubricService = rubricService;
        this.userRepo = userRepo;
    }

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal UserDetails principal,
                                    HttpServletRequest request,
                                    @RequestBody RubricRequest req) {
        Long teacherId = resolveUserId(principal, request);
        try {
            var rubric = rubricService.create(teacherId, req.name(), req.subject(),
                    req.description(), req.customPrompt(), toDimensionInputs(req.dimensions()));
            return ResponseEntity.ok(ApiResponse.of(toDto(rubric)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal UserDetails principal,
                                  HttpServletRequest request,
                                  @RequestParam(required = false) String subject) {
        Long teacherId = resolveUserId(principal, request);
        var rubrics = rubricService.listByTeacher(teacherId, subject);
        var dtos = rubrics.stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.of(dtos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        try {
            var rubric = rubricService.getDetail(id);
            return ResponseEntity.ok(ApiResponse.of(toDto(rubric)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody RubricRequest req) {
        try {
            var rubric = rubricService.update(id, req.name(), req.subject(),
                    req.description(), req.customPrompt(), toDimensionInputs(req.dimensions()));
            return ResponseEntity.ok(ApiResponse.of(toDto(rubric)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
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

    private List<DimensionInput> toDimensionInputs(List<DimensionDto> dims) {
        if (dims == null) return List.of();
        return dims.stream()
                .map(d -> new DimensionInput(d.name(), d.description(), d.maxScore(), d.weight()))
                .toList();
    }

    private Map<String, Object> toDto(GradingRubricEntity r) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("subject", r.getSubject() != null ? r.getSubject() : "");
        m.put("description", r.getDescription() != null ? r.getDescription() : "");
        m.put("customPrompt", r.getCustomPrompt() != null ? r.getCustomPrompt() : "");
        m.put("createdAt", r.getCreatedAt().toString());
        m.put("dimensions", r.getDimensions().stream().map(this::dimDto).toList());
        return m;
    }

    private Map<String, Object> dimDto(RubricDimensionEntity d) {
        return Map.of(
                "id", d.getId(),
                "name", d.getName(),
                "description", d.getDescription() != null ? d.getDescription() : "",
                "maxScore", d.getMaxScore(),
                "weight", d.getWeight(),
                "sortOrder", d.getSortOrder()
        );
    }

    public record RubricRequest(String name, String subject, String description,
                                 String customPrompt, List<DimensionDto> dimensions) {}

    public record DimensionDto(String name, String description,
                                BigDecimal maxScore, Integer weight) {}
}
