package com.cqust.ai_server.controller;

import com.cqust.ai_server.entity.LeetCodeProblem;
import com.cqust.ai_server.security.StudentSessionResolver;
import com.cqust.ai_server.service.LeetCodeExecutionService;
import com.cqust.ai_server.service.LeetCodeProblemService;
import com.cqust.ai_server.service.LeetCodeRecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leetcode")
@CrossOrigin(
        origins = {"http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:5173", "http://127.0.0.1:5173"},
        allowCredentials = "true"
)
public class LeetCodeController {

    @Autowired
    private LeetCodeProblemService problemService;

    @Autowired
    private LeetCodeExecutionService executionService;

    @Autowired
    @Qualifier("intelligentRecommendationService")
    private LeetCodeRecommendationService recommendationService;

    @Autowired
    private StudentSessionResolver studentSessionResolver;

    @GetMapping("/problem/{problemId}")
    public ResponseEntity<Map<String, Object>> getProblem(@PathVariable Long problemId) {
        Map<String, Object> response = new HashMap<>();

        try {
            LeetCodeProblem problem = problemService.findById(problemId);
            if (problem == null) {
                response.put("success", false);
                response.put("message", "problem not found");
                return ResponseEntity.ok(response);
            }

            Map<String, Object> problemData = new HashMap<>();
            problemData.put("id", problem.getId());
            problemData.put("problemCode", problem.getProblemCode());
            problemData.put("title", problem.getTitleMain());
            problemData.put("titleAlt", problem.getTitleAlt());
            problemData.put("difficulty", problem.getDifficulty());
            problemData.put("problemText", problem.getProblemText());
            problemData.put("solutionText", problem.getSolutionText());
            problemData.put("estimatedMinutes", problem.getEstimatedMinutes());
            problemData.put("sampleTestCases", generateSampleTestCases());

            response.put("success", true);
            response.put("data", problemData);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "failed to load problem: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runCode(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {

        Map<String, Object> response = new HashMap<>();

        try {
            Integer studentId = getCurrentStudentId(httpRequest);
            if (studentId == null) {
                response.put("success", false);
                response.put("message", "authentication required");
                return ResponseEntity.ok(response);
            }

            Long problemId = Long.valueOf(request.get("problemId").toString());
            String code = (String) request.get("code");
            String language = (String) request.get("language");
            String testInput = (String) request.get("testInput");

            Map<String, Object> result = executionService.runCode(problemId, code, language, testInput);
            response.put("success", true);
            response.put("data", result);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "failed to run code: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitSolution(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {

        Map<String, Object> response = new HashMap<>();

        try {
            Integer studentId = getCurrentStudentId(httpRequest);
            if (studentId == null) {
                response.put("success", false);
                response.put("message", "authentication required");
                return ResponseEntity.ok(response);
            }

            Long problemId = Long.valueOf(request.get("problemId").toString());
            String code = (String) request.get("code");
            String language = (String) request.get("language");
            String recommendationRequestId = request.get("recommendationRequestId") == null
                    ? null
                    : request.get("recommendationRequestId").toString();
            String recommendationSessionId = request.get("recommendationSessionId") == null
                    ? null
                    : request.get("recommendationSessionId").toString();

            Map<String, Object> result = executionService.submitSolution(studentId, problemId, code, language);

            if (Boolean.TRUE.equals(result.get("accepted"))
                    && recommendationRequestId != null
                    && !recommendationRequestId.isBlank()) {
                recommendationService.recordFeedback(
                        recommendationRequestId,
                        studentId,
                        problemId,
                        "complete",
                        recommendationSessionId
                );
            }

            response.put("success", true);
            response.put("data", result);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "failed to submit solution: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/test/problems")
    public ResponseEntity<Map<String, Object>> getAllProblems() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<LeetCodeProblem> problems = problemService.findAll();
            response.put("success", true);
            response.put("count", problems.size());
            response.put("data", problems);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "failed to load problem list: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    private Integer getCurrentStudentId(HttpServletRequest request) {
        try {
            return parseInteger(studentSessionResolver.requireStudentId(request));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number) {
                int parsed = ((Number) value).intValue();
                return parsed > 0 ? parsed : null;
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return null;
            }
            int parsed = Integer.parseInt(text);
            return parsed > 0 ? parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String[] generateSampleTestCases() {
        return new String[]{
                "sample input 1",
                "sample input 2"
        };
    }
}
