package com.cqust.ai_server.controller;

import com.cqust.ai_server.entity.StudentCode;
import com.cqust.ai_server.service.StudentCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生代码查询控制器
 */
@RestController
@RequestMapping("/api")
public class StudentCodeController {

    private static final Logger logger = LoggerFactory.getLogger(StudentCodeController.class);

    @Autowired
    private StudentCodeService studentCodeService;

    /**
     * 根据学生ID查询所有代码
     * @param studentId 学生ID
     * @return 包含代码列表的响应
     */
    @GetMapping("/student/code/{studentId}")
    public ResponseEntity<Map<String, Object>> getStudentCodes(@PathVariable int studentId) {
        logger.info("接收到查询学生代码的请求，学生ID: {}", studentId);
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<StudentCode> codeList = studentCodeService.findCodeByStudentId(studentId);
            
            if (codeList != null && !codeList.isEmpty()) {
                logger.info("找到学生ID为 {} 的代码记录，共 {} 条", studentId, codeList.size());
                response.put("success", true);
                response.put("codeList", codeList);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("未找到学生ID为 {} 的代码记录", studentId);
                response.put("success", false);
                response.put("message", "未找到该学生的代码记录");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("查询学生代码时出错: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "查询学生代码时发生错误: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 根据学生ID和实验ID查询特定代码
     * @param studentId 学生ID
     * @param experimentId 实验ID
     * @return 包含代码的响应
     */
    @GetMapping("/student/code/{studentId}/{experimentId}")
    public ResponseEntity<Map<String, Object>> getStudentExperimentCode(
            @PathVariable int studentId, 
            @PathVariable int experimentId) {
        logger.info("接收到查询学生特定实验代码的请求，学生ID: {}, 实验ID: {}", studentId, experimentId);
        Map<String, Object> response = new HashMap<>();
        
        try {
            StudentCode code = studentCodeService.findCodeByStudentIdAndExperimentId(studentId, experimentId);
            
            if (code != null) {
                logger.info("找到学生ID为 {} 实验ID为 {} 的代码记录", studentId, experimentId);
                response.put("success", true);
                response.put("code", code);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("未找到学生ID为 {} 实验ID为 {} 的代码记录", studentId, experimentId);
                response.put("success", false);
                response.put("message", "未找到该学生的指定实验代码");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("查询学生特定实验代码时出错: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "查询学生特定实验代码时发生错误: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}