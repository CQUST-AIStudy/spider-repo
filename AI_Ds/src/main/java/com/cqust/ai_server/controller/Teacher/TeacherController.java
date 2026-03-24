package com.cqust.ai_server.controller.Teacher;

import com.cqust.ai_server.dao.StudentDao;
import com.cqust.ai_server.entity.UserEntity;
import com.cqust.ai_server.entity.teacher.Teacher;
import com.cqust.ai_server.security.LegacySessionAccessResolver;
import com.cqust.ai_server.service.TeacherService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private LegacySessionAccessResolver legacySessionAccessResolver;

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getTeacherInfo(HttpServletRequest request) {
        try {
            Teacher teacher = requireCurrentTeacher(request);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", teacher);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "failed to load teacher info: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/class-student-count/{teacherId}")
    public ResponseEntity<Map<String, Object>> getClassStudentCountByTeacherId(
            @PathVariable Integer teacherId,
            HttpServletRequest request
    ) {
        try {
            if (teacherId == null || teacherId <= 0) {
                return error("invalid teacher id");
            }

            Teacher teacher = requireTeacherAccess(teacherId, request);
            Integer studentCount = studentDao.getStudentCountByTeacherId(teacherId);
            if (studentCount == null) {
                studentCount = 0;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("teacherId", teacherId);
            data.put("teacherName", teacher.getTeacher_name());
            data.put("classroom", teacher.getClassroom());
            data.put("studentCount", studentCount);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return error("failed to query class student count: " + e.getMessage());
        }
    }

    private Teacher requireCurrentTeacher(HttpServletRequest request) {
        UserEntity user = legacySessionAccessResolver.requireAuthenticated(request);
        String role = normalize(user.getRole());
        if (!"teacher".equals(role) && !"admin".equals(role)) {
            throw new IllegalStateException("teacher role required");
        }

        String username = normalize(user.getUsername());
        Teacher teacher = username == null ? null : teacherService.findByUsername(username);
        if (teacher == null) {
            throw new IllegalStateException("teacher info not found");
        }
        return teacher;
    }

    private Teacher requireTeacherAccess(Integer teacherId, HttpServletRequest request) {
        UserEntity user = legacySessionAccessResolver.requireAuthenticated(request);
        String role = normalize(user.getRole());
        if ("admin".equals(role)) {
            Teacher teacher = teacherService.findByTeacherId(teacherId);
            if (teacher == null) {
                throw new IllegalStateException("teacher info not found");
            }
            return teacher;
        }
        if (!"teacher".equals(role)) {
            throw new IllegalStateException("teacher role required");
        }

        Teacher currentTeacher = requireCurrentTeacher(request);
        if (!teacherId.equals(currentTeacher.getTeacher_id())) {
            throw new IllegalStateException("forbidden");
        }
        return currentTeacher;
    }

    private ResponseEntity<Map<String, Object>> error(String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("message", message);
        return ResponseEntity.badRequest().body(errorResponse);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
