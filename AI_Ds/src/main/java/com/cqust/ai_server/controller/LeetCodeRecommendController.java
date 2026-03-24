package com.cqust.ai_server.controller;

import com.cqust.ai_server.entity.LeetCodeRecommendItem;
import com.cqust.ai_server.entity.LeetCodeRecommendRequest;
import com.cqust.ai_server.entity.UserEntity;
import com.cqust.ai_server.security.LegacySessionAccessResolver;
import com.cqust.ai_server.security.StudentSessionResolver;
import com.cqust.ai_server.service.LeetCodeRecommendationService;
import com.cqust.ai_server.service.LeetCodeSyncService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/recommendations/leetcode")
@CrossOrigin(
        origins = {"http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:5173", "http://127.0.0.1:5173"},
        allowCredentials = "true",
        allowedHeaders = "*"
)
public class LeetCodeRecommendController {

    private static final Logger logger = LoggerFactory.getLogger(LeetCodeRecommendController.class);

    @Autowired
    @Qualifier("intelligentRecommendationService")
    private LeetCodeRecommendationService recommendationService;

    @Autowired
    private LeetCodeSyncService syncService;

    @Autowired
    private StudentSessionResolver studentSessionResolver;

    @Autowired
    private LegacySessionAccessResolver legacySessionAccessResolver;

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateRecommendation(
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "default") String scene,
            @RequestParam(required = false) Integer studentId,
            HttpServletRequest request
    ) {
        try {
            Integer currentStudentId = requireCurrentStudentId(studentId, request);
            logger.info("Generate recommendation request received. studentId={}, limit={}, scene={}",
                    currentStudentId, limit, scene);

            String requestId = recommendationService.generateRecommendation(currentStudentId, limit, scene);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requestId", requestId);
            response.put("status", "pending");
            response.put("message", "recommendation request accepted");
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return error(e.getStatusCode(), e.getReason());
        } catch (Exception e) {
            logger.error("Failed to generate recommendation", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "failed to generate recommendation: " + e.getMessage());
        }
    }

    @GetMapping("/result/{requestId}")
    public ResponseEntity<Map<String, Object>> getRecommendationResult(
            @PathVariable String requestId,
            HttpServletRequest request
    ) {
        try {
            logger.info("Load recommendation result. requestId={}", requestId);
            LeetCodeRecommendRequest recommendationRequest = recommendationService.getRecommendationResult(requestId);
            if (recommendationRequest == null) {
                return error(HttpStatus.NOT_FOUND, "recommendation request not found");
            }

            authorizeRecommendationAccess(recommendationRequest.getStudentId(), request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requestId", recommendationRequest.getRequestId());
            response.put("status", recommendationRequest.getStatus());
            response.put("studentId", recommendationRequest.getStudentId());
            response.put("scene", recommendationRequest.getScene());
            response.put("requestLimit", recommendationRequest.getRequestLimit());
            response.put("createdAt", recommendationRequest.getCreatedAt());
            response.put("finishedAt", recommendationRequest.getFinishedAt());

            if (recommendationRequest.isCompleted()) {
                List<LeetCodeRecommendItem> items = recommendationService.getRecommendationItems(requestId);
                response.put("items", items);
                response.put("itemCount", items.size());
            } else if (recommendationRequest.isFailed()) {
                response.put("errorMessage", recommendationRequest.getErrorMessage());
            }

            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return error(e.getStatusCode(), e.getReason());
        } catch (Exception e) {
            logger.error("Failed to load recommendation result", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "failed to load recommendation result: " + e.getMessage());
        }
    }

    @GetMapping("/sync")
    public ResponseEntity<Map<String, Object>> generateRecommendationSync(
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) Integer studentId,
            HttpServletRequest request
    ) {
        try {
            Integer currentStudentId = requireCurrentStudentId(studentId, request);
            logger.info("Generate sync recommendation request received. studentId={}, limit={}",
                    currentStudentId, limit);

            List<LeetCodeRecommendItem> items = recommendationService.generateRecommendationSync(currentStudentId, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("items", items);
            response.put("itemCount", items.size());
            response.put("message", "recommendation generated");
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return error(e.getStatusCode(), e.getReason());
        } catch (Exception e) {
            logger.error("Failed to generate sync recommendation", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "failed to generate recommendation: " + e.getMessage());
        }
    }

    @PostMapping("/feedback")
    public ResponseEntity<Map<String, Object>> recordFeedback(
            @RequestParam String requestId,
            @RequestParam(required = false) Integer studentId,
            @RequestParam Long problemId,
            @RequestParam String action,
            @RequestParam(required = false) String sessionId,
            HttpServletRequest request
    ) {
        try {
            Integer currentStudentId = requireCurrentStudentId(studentId, request);
            logger.info("Record recommendation feedback. requestId={}, studentId={}, problemId={}, action={}",
                    requestId, currentStudentId, problemId, action);

            boolean success = recommendationService.recordFeedback(
                    requestId,
                    currentStudentId,
                    problemId,
                    action,
                    sessionId
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "feedback recorded" : "feedback rejected");
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return error(e.getStatusCode(), e.getReason());
        } catch (Exception e) {
            logger.error("Failed to record recommendation feedback", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "failed to record feedback: " + e.getMessage());
        }
    }

    @PostMapping("/admin/sync")
    public ResponseEntity<Map<String, Object>> syncLeetCodeData(HttpServletRequest request) {
        try {
            requireAdmin(request);
            logger.info("Start LeetCode dataset sync");
            String jsonFilePath = "datasets/leetcode/solutions_cleaned.json";
            int syncCount = syncService.syncProblemsFromJson(jsonFilePath);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("syncCount", syncCount);
            response.put("message", "dataset sync completed");
            response.put("stats", syncService.getSyncStats());
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return error(e.getStatusCode(), e.getReason());
        } catch (Exception e) {
            logger.error("Failed to sync LeetCode dataset", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "failed to sync dataset: " + e.getMessage());
        }
    }

    @GetMapping("/admin/stats")
    public ResponseEntity<Map<String, Object>> getSyncStats(HttpServletRequest request) {
        try {
            requireAdmin(request);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stats", syncService.getSyncStats());
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return error(e.getStatusCode(), e.getReason());
        } catch (Exception e) {
            logger.error("Failed to load sync stats", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "failed to load sync stats: " + e.getMessage());
        }
    }

    private Integer requireCurrentStudentId(Integer requestedStudentId, HttpServletRequest request) {
        String sessionStudentId = studentSessionResolver.requireStudentId(request);
        Integer currentStudentId = parseStudentId(sessionStudentId);
        if (requestedStudentId != null && !requestedStudentId.equals(currentStudentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }
        return currentStudentId;
    }

    private void authorizeRecommendationAccess(Integer recommendationStudentId, HttpServletRequest request) {
        UserEntity user = legacySessionAccessResolver.requireAuthenticated(request);
        String role = normalize(user.getRole());
        if ("admin".equals(role)) {
            return;
        }
        Integer currentStudentId = requireCurrentStudentId(null, request);
        if (recommendationStudentId == null || !recommendationStudentId.equals(currentStudentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }
    }

    private void requireAdmin(HttpServletRequest request) {
        legacySessionAccessResolver.requireAdmin(request);
    }

    private Integer parseStudentId(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid student id");
        }
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatusCode status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
