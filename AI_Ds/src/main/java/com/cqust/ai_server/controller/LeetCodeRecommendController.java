package com.cqust.ai_server.controller;

import com.cqust.ai_server.entity.LeetCodeRecommendRequest;
import com.cqust.ai_server.entity.LeetCodeRecommendItem;
import com.cqust.ai_server.service.LeetCodeRecommendationService;
import com.cqust.ai_server.service.LeetCodeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LeetCode推荐系统API控制器
 */
@RestController
@RequestMapping("/api/recommendations/leetcode")
@CrossOrigin(origins = "*")
public class LeetCodeRecommendController {

    private static final Logger logger = LoggerFactory.getLogger(LeetCodeRecommendController.class);

    @Autowired
    @Qualifier("intelligentRecommendationService")
    private LeetCodeRecommendationService recommendationService;

    @Autowired
    private LeetCodeSyncService syncService;

    /**
     * 生成推荐请求
     * TODO: 从JWT token中获取学生ID，而不是从参数获取
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateRecommendation(
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "default") String scene,
            @RequestParam Integer studentId) { // 临时从参数获取，生产环境应从JWT获取
        
        try {
            logger.info("收到推荐生成请求: studentId={}, limit={}, scene={}", studentId, limit, scene);
            
            String requestId = recommendationService.generateRecommendation(studentId, limit, scene);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requestId", requestId);
            response.put("status", "pending");
            response.put("message", "推荐请求已提交");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("生成推荐请求失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "生成推荐失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 查询推荐结果
     */
    @GetMapping("/result/{requestId}")
    public ResponseEntity<Map<String, Object>> getRecommendationResult(@PathVariable String requestId) {
        try {
            logger.info("查询推荐结果: requestId={}", requestId);
            
            LeetCodeRecommendRequest request = recommendationService.getRecommendationResult(requestId);
            if (request == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "推荐请求不存在");
                return ResponseEntity.status(404).body(response);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requestId", request.getRequestId());
            response.put("status", request.getStatus());
            response.put("studentId", request.getStudentId());
            response.put("scene", request.getScene());
            response.put("requestLimit", request.getRequestLimit());
            response.put("createdAt", request.getCreatedAt());
            response.put("finishedAt", request.getFinishedAt());
            
            if (request.isCompleted()) {
                List<LeetCodeRecommendItem> items = recommendationService.getRecommendationItems(requestId);
                response.put("items", items);
                response.put("itemCount", items.size());
            } else if (request.isFailed()) {
                response.put("errorMessage", request.getErrorMessage());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("查询推荐结果失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "查询推荐结果失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    /**
     * 同步生成推荐（兼容旧接口）
     */
    @GetMapping("/sync")
    public ResponseEntity<Map<String, Object>> generateRecommendationSync(
            @RequestParam Integer studentId,
            @RequestParam(defaultValue = "20") Integer limit) {
        
        try {
            logger.info("收到同步推荐请求: studentId={}, limit={}", studentId, limit);
            
            List<LeetCodeRecommendItem> items = recommendationService.generateRecommendationSync(studentId, limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("items", items);
            response.put("itemCount", items.size());
            response.put("message", "推荐生成成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("同步生成推荐失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "生成推荐失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 记录推荐反馈
     */
    @PostMapping("/feedback")
    public ResponseEntity<Map<String, Object>> recordFeedback(
            @RequestParam String requestId,
            @RequestParam Integer studentId,
            @RequestParam Long problemId,
            @RequestParam String action,
            @RequestParam(required = false) String sessionId) {
        
        try {
            logger.info("收到推荐反馈: requestId={}, studentId={}, problemId={}, action={}", 
                       requestId, studentId, problemId, action);
            
            boolean success = recommendationService.recordFeedback(requestId, studentId, problemId, action, sessionId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "反馈记录成功" : "反馈记录失败");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("记录推荐反馈失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "记录反馈失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 数据同步接口（管理员使用）
     */
    @PostMapping("/admin/sync")
    public ResponseEntity<Map<String, Object>> syncLeetCodeData() {
        try {
            logger.info("开始同步LeetCode数据");
            
            // 使用相对路径指向清洗后的数据文件
            String jsonFilePath = "datasets/leetcode/solutions_cleaned.json";
            int syncCount = syncService.syncProblemsFromJson(jsonFilePath);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("syncCount", syncCount);
            response.put("message", "数据同步完成");
            response.put("stats", syncService.getSyncStats());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("同步LeetCode数据失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "数据同步失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 获取同步统计信息
     */
    @GetMapping("/admin/stats")
    public ResponseEntity<Map<String, Object>> getSyncStats() {
        try {
            String stats = syncService.getSyncStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stats", stats);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取同步统计信息失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取统计信息失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
}