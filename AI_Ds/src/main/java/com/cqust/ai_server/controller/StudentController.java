package com.cqust.ai_server.controller;

import com.cqust.ai_server.entity.AIRemarks;
import com.cqust.ai_server.entity.Student;
import com.cqust.ai_server.security.LegacySessionAccessResolver;
import com.cqust.ai_server.service.AIRemarksService;
import com.cqust.ai_server.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StudentController {

    private static final Logger logger = LoggerFactory.getLogger(StudentController.class);

    @Autowired
    private StudentService studentService;

    @Autowired
    private AIRemarksService aiRemarksService;

    @Autowired
    private LegacySessionAccessResolver legacySessionAccessResolver;

    @GetMapping("students/id/{username}")
    public ResponseEntity<Map<String, Object>> findStudentIdByUsername(
            @PathVariable String username,
            HttpServletRequest request
    ) {
        legacySessionAccessResolver.requireUsernameReadAccess(username, request);
        return findStudentIdByUsername(username);
    }

    public ResponseEntity<Map<String, Object>> findStudentIdByUsername(String username) {
        logger.info("query student id by username={}", username);
        Map<String, Object> response = new HashMap<>();

        try {
            Integer studentId = studentService.findStudentIdByUsername(username);
            if (studentId != null) {
                response.put("success", true);
                response.put("studentId", studentId);
                return ResponseEntity.ok(response);
            }

            response.put("success", false);
            response.put("message", "student id not found");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("query student id failed", e);
            response.put("success", false);
            response.put("message", "failed to query student id: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/students/username/{username}")
    public ResponseEntity<Map<String, Object>> findStudentByUsername(
            @PathVariable String username,
            HttpServletRequest request
    ) {
        legacySessionAccessResolver.requireUsernameReadAccess(username, request);
        return findStudentByUsername(username);
    }

    public ResponseEntity<Map<String, Object>> findStudentByUsername(String username) {
        logger.info("query student by username={}", username);
        Map<String, Object> response = new HashMap<>();

        try {
            Student student = studentService.findByUsername(username);
            if (student != null) {
                response.put("success", true);
                response.put("student", student);
                return ResponseEntity.ok(response);
            }

            response.put("success", false);
            response.put("message", "student not found");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("query student by username failed", e);
            response.put("success", false);
            response.put("message", "failed to query student: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/students/{studentId}")
    public ResponseEntity<Map<String, Object>> findStudentById(
            @PathVariable int studentId,
            HttpServletRequest request
    ) {
        legacySessionAccessResolver.requireStudentReadAccess(String.valueOf(studentId), request);
        return findStudentById(studentId);
    }

    public ResponseEntity<Map<String, Object>> findStudentById(int studentId) {
        logger.info("query student by id={}", studentId);
        Map<String, Object> response = new HashMap<>();

        try {
            Student student = studentService.findByStudentId(studentId);
            if (student != null) {
                response.put("success", true);
                response.put("student", student);
                return ResponseEntity.ok(response);
            }

            response.put("success", false);
            response.put("message", "student not found");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("query student by id failed", e);
            response.put("success", false);
            response.put("message", "failed to query student: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/students")
    public ResponseEntity<Map<String, Object>> getAllStudents(HttpServletRequest request) {
        legacySessionAccessResolver.requireTeacherOrAdmin(request);
        return getAllStudents();
    }

    public ResponseEntity<Map<String, Object>> getAllStudents() {
        logger.info("query all students");
        Map<String, Object> response = new HashMap<>();

        try {
            List<Student> students = studentService.findAllStudents();
            if (students != null && !students.isEmpty()) {
                response.put("success", true);
                response.put("students", students);
                return ResponseEntity.ok(response);
            }

            response.put("success", false);
            response.put("message", "students not found");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("query all students failed", e);
            response.put("success", false);
            response.put("message", "failed to query students: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/ai_remarks")
    public ResponseEntity<Map<String, Object>> getAIRemark(
            @RequestParam Integer studentId,
            @RequestParam int experimentId,
            HttpServletRequest request
    ) {
        legacySessionAccessResolver.requireStudentReadAccess(String.valueOf(studentId), request);
        return getAIRemark(studentId, experimentId);
    }

    public ResponseEntity<Map<String, Object>> getAIRemark(Integer studentId, int experimentId) {
        logger.info("query ai remark, studentId={}, experimentId={}", studentId, experimentId);
        Map<String, Object> response = new HashMap<>();

        try {
            AIRemarks aiRemarks = aiRemarksService.getAIRemarkByStudentAndExperiment(studentId, experimentId);
            if (aiRemarks != null) {
                response.put("success", true);
                response.put("data", aiRemarks);
                return ResponseEntity.ok(response);
            }

            response.put("success", false);
            response.put("message", "ai remark not found");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("query ai remark failed", e);
            response.put("success", false);
            response.put("message", "failed to query ai remark: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/ai_remarks")
    public ResponseEntity<Map<String, Object>> saveOrUpdateAIRemark(
            @RequestBody AIRemarks aiRemarks,
            HttpServletRequest request
    ) {
        if (aiRemarks == null || aiRemarks.getStudentId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "studentId is required"
            ));
        }
        legacySessionAccessResolver.requireStudentReadAccess(String.valueOf(aiRemarks.getStudentId()), request);
        return saveOrUpdateAIRemark(aiRemarks);
    }

    public ResponseEntity<Map<String, Object>> saveOrUpdateAIRemark(AIRemarks aiRemarks) {
        logger.info("save ai remark: {}", aiRemarks);
        Map<String, Object> response = new HashMap<>();

        try {
            boolean result = aiRemarksService.saveOrUpdateAIRemark(aiRemarks);
            if (result) {
                response.put("success", true);
                response.put("message", "ai remark saved");
                return ResponseEntity.ok(response);
            }

            response.put("success", false);
            response.put("message", "ai remark save failed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("save ai remark failed", e);
            response.put("success", false);
            response.put("message", "failed to save ai remark: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/ai_remarks")
    public ResponseEntity<Map<String, Object>> deleteAIRemark(
            @RequestParam("studentId") Integer studentId,
            @RequestParam("experimentId") Integer experimentId,
            HttpServletRequest request
    ) {
        legacySessionAccessResolver.requireStudentReadAccess(String.valueOf(studentId), request);
        return deleteAIRemark(studentId, experimentId);
    }

    public ResponseEntity<Map<String, Object>> deleteAIRemark(Integer studentId, Integer experimentId) {
        logger.info("delete ai remark, studentId={}, experimentId={}", studentId, experimentId);
        Map<String, Object> response = new HashMap<>();

        try {
            boolean result = aiRemarksService.deleteAIRemark(studentId, experimentId);
            if (result) {
                response.put("success", true);
                response.put("message", "ai remark deleted");
                return ResponseEntity.ok(response);
            }

            response.put("success", false);
            response.put("message", "ai remark delete failed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("delete ai remark failed", e);
            response.put("success", false);
            response.put("message", "failed to delete ai remark: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
