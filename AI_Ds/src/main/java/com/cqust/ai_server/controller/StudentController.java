package com.cqust.ai_server.controller;

import com.cqust.ai_server.entity.Student;
import com.cqust.ai_server.service.StudentService;
import com.cqust.ai_server.entity.AIRemarks;
import com.cqust.ai_server.service.AIRemarksService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class StudentController {

    private static final Logger logger = LoggerFactory.getLogger(StudentController.class);

    @Autowired
    private StudentService studentService;

    @Autowired
    private AIRemarksService aiRemarksService;

    /**
     * 根据用户名查询对应的学生ID
     * @param username 用户名
     * @return 包含学生ID的响应
     */
    @GetMapping("students/id/{username}")
    public ResponseEntity<Map<String, Object>> findStudentIdByUsername(@PathVariable String username) {
        logger.info("接收到查询学生ID的请求，用户名: {}", username);
        Map<String, Object> response = new HashMap<>();
        
        try {
            Integer studentId = studentService.findStudentIdByUsername(username);
            
            if (studentId != null) {
                logger.info("找到用户名 {} 对应的学生ID: {}", username, studentId);
                response.put("success", true);
                response.put("studentId", studentId);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("未找到用户名 {} 对应的学生ID", username);
                response.put("success", false);
                response.put("message", "未找到对应的学生ID");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("查询学生ID时出错: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "查询学生ID时发生错误: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 根据用户名查询学生信息
     * @param username 用户名
     * @return 学生信息
     */
    @GetMapping("/students/username/{username}")
    public ResponseEntity<Map<String, Object>> findStudentByUsername(@PathVariable String username) {
        logger.info("接收到查询学生信息的请求，用户名: {}", username);
        Map<String, Object> response = new HashMap<>();
        
        try {
            Student student = studentService.findByUsername(username);
            
            if (student != null) {
                logger.info("找到用户名为 {} 的学生", username);
                response.put("success", true);
                response.put("student", student);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("未找到用户名为 {} 的学生", username);
                response.put("success", false);
                response.put("message", "未找到学生信息");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("查询学生信息时出错: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "查询学生信息时发生错误: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 根据学生ID查询学生信息
     * @param studentId 学生ID
     * @return 学生信息
     */
    @GetMapping("/students/{studentId}")
    public ResponseEntity<Map<String, Object>> findStudentById(@PathVariable int studentId) {
        logger.info("接收到查询学生信息的请求，学生ID: {}", studentId);
        Map<String, Object> response = new HashMap<>();
        
        try {
            Student student = studentService.findByStudentId(studentId);
            
            if (student != null) {
                logger.info("找到学生ID为 {} 的学生", studentId);
                response.put("success", true);
                response.put("student", student);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("未找到学生ID为 {} 的学生", studentId);
                response.put("success", false);
                response.put("message", "未找到学生信息");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("查询学生信息时出错: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "查询学生信息时发生错误: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取所有学生列表
     * @return 学生列表
     */
    @GetMapping("/students")
    public ResponseEntity<Map<String, Object>> getAllStudents() {
        logger.info("接收到查询所有学生的请求");
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Student> students = studentService.findAllStudents();
            
            if (students != null && !students.isEmpty()) {
                logger.info("成功获取到 {} 名学生的信息", students.size());
                response.put("success", true);
                response.put("students", students);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("未找到任何学生信息");
                response.put("success", false);
                response.put("message", "未找到任何学生信息");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("查询所有学生时出错: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "查询学生列表时发生错误: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 根据学生ID和实验ID获取AI备注
     *
     * @param studentId    学生ID
     * @param experimentId 实验ID
     * @return 包含AI备注的响应
     */
    @GetMapping("/ai_remarks")
    public ResponseEntity<Map<String, Object>> getAIRemark(Integer studentId, int experimentId) {

        logger.info("接收到获取AI备注的请求，学生ID: {}, 实验ID: {}", studentId, experimentId);
        Map<String, Object> response = new HashMap<>();
        
        try {
            AIRemarks aiRemarks = aiRemarksService.getAIRemarkByStudentAndExperiment(studentId, experimentId);
            
            if (aiRemarks != null) {
                logger.info("找到学生ID为{}，实验ID为{}的AI备注", studentId, experimentId);
                response.put("success", true);
                response.put("data", aiRemarks);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("未找到学生ID为{}，实验ID为{}的AI备注", studentId, experimentId);
                response.put("success", false);
                response.put("message", "未找到AI备注");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("获取AI备注时出错: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "获取AI备注时发生错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 保存或更新AI备注
     * @param aiRemarks AI备注信息
     * @return 操作结果
     */
    @PostMapping("/ai_remarks")
    public ResponseEntity<Map<String, Object>> saveOrUpdateAIRemark(@RequestBody AIRemarks aiRemarks) {
        logger.info("接收到保存或更新AI备注的请求: {}", aiRemarks);
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean result = aiRemarksService.saveOrUpdateAIRemark(aiRemarks);
            
            if (result) {
                logger.info("成功保存或更新AI备注");
                response.put("success", true);
                response.put("message", "AI备注保存成功");
                return ResponseEntity.ok(response);
            } else {
                logger.warn("保存或更新AI备注失败");
                response.put("success", false);
                response.put("message", "AI备注保存失败");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("保存或更新AI备注时出错: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "保存AI备注时发生错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 删除AI备注
     * @param studentId 学生ID
     * @param experimentId 实验ID
     * @return 操作结果
     */
    @DeleteMapping("/ai_remarks")
    public ResponseEntity<Map<String, Object>> deleteAIRemark(
            @RequestParam("studentId") Integer studentId,
            @RequestParam("experimentId") Integer experimentId) {
        logger.info("接收到删除AI备注的请求，学生ID: {}, 实验ID: {}", studentId, experimentId);
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean result = aiRemarksService.deleteAIRemark(studentId, experimentId);
            
            if (result) {
                logger.info("成功删除学生ID为{}，实验ID为{}的AI备注", studentId, experimentId);
                response.put("success", true);
                response.put("message", "AI备注删除成功");
                return ResponseEntity.ok(response);
            } else {
                logger.warn("删除学生ID为{}，实验ID为{}的AI备注失败", studentId, experimentId);
                response.put("success", false);
                response.put("message", "AI备注删除失败");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("删除AI备注时出错: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "删除AI备注时发生错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}