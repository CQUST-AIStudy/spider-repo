package com.cqust.ai_server.controller;

import com.cqust.ai_server.entity.LeetCodeProblem;
import com.cqust.ai_server.service.LeetCodeProblemService;
import com.cqust.ai_server.service.LeetCodeExecutionService;
import com.cqust.ai_server.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LeetCode练习控制器
 */
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
    private StudentService studentService;

    /**
     * 获取题目详情
     */
    @GetMapping("/problem/{problemId}")
    public ResponseEntity<Map<String, Object>> getProblem(@PathVariable Long problemId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            LeetCodeProblem problem = problemService.findById(problemId);
            if (problem == null) {
                response.put("success", false);
                response.put("message", "题目不存在");
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
            problemData.put("sampleTestCases", generateSampleTestCases(problem));

            response.put("success", true);
            response.put("data", problemData);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "获取题目失败: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 运行代码
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runCode(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Integer studentId = getCurrentStudentId(httpRequest, request);
            if (studentId == null) {
                response.put("success", false);
                response.put("message", "用户未登录");
                return ResponseEntity.ok(response);
            }

            Long problemId = Long.valueOf(request.get("problemId").toString());
            String code = (String) request.get("code");
            String language = (String) request.get("language");
            String testInput = (String) request.get("testInput");

            // 执行代码
            Map<String, Object> result = executionService.runCode(problemId, code, language, testInput);
            
            response.put("success", true);
            response.put("data", result);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "运行代码失败: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 提交解答
     */
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitSolution(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Integer studentId = getCurrentStudentId(httpRequest, request);
            if (studentId == null) {
                response.put("success", false);
                response.put("message", "用户未登录");
                return ResponseEntity.ok(response);
            }

            Long problemId = Long.valueOf(request.get("problemId").toString());
            String code = (String) request.get("code");
            String language = (String) request.get("language");

            // 提交并评测
            Map<String, Object> result = executionService.submitSolution(studentId, problemId, code, language);
            
            response.put("success", true);
            response.put("data", result);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "提交解答失败: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    private Integer getCurrentStudentId(HttpServletRequest request, Map<String, Object> payload) {
        if (request != null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Integer studentId = parseInteger(session.getAttribute("studentId"));
                if (studentId != null) {
                    return studentId;
                }
                Integer usernum = parseInteger(session.getAttribute("usernum"));
                if (usernum != null) {
                    return usernum;
                }
                String username = (String) session.getAttribute("username");
                if (username != null && !username.isBlank()) {
                    try {
                        Integer sid = studentService.findStudentIdByUsername(username);
                        if (sid != null) {
                            return sid;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        if (payload != null) {
            Integer studentId = parseInteger(payload.get("studentId"));
            if (studentId != null) {
                return studentId;
            }
        }

        return null;
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

    /**
     * 测试接口 - 获取所有题目
     */
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
            response.put("message", "获取题目列表失败: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 生成示例测试用例
     */
    private String[] generateSampleTestCases(LeetCodeProblem problem) {
        // 从题目描述中提取示例，或返回默认测试用例
        // 这里简化处理，返回一些通用的测试用例
        return new String[]{
            "示例输入 1",
            "示例输入 2"
        };
    }
}
