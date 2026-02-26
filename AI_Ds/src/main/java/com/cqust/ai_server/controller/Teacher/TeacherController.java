package com.cqust.ai_server.controller.Teacher;

import com.cqust.ai_server.dao.StudentDao;
import com.cqust.ai_server.entity.teacher.Teacher;
import com.cqust.ai_server.service.TeacherService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    @Autowired
    private TeacherService teacherService;
    
    @Autowired
    private StudentDao studentDao;
    
    /**
     * 获取当前登录老师信息
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getTeacherInfo(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String username = null;
        if (session != null) {
            username = (String) session.getAttribute("username");
        }
        if (username == null || username.isBlank()) {
            username = "teacher1"; // 默认教师账号
        }
        try {
            Teacher teacher = teacherService.findByUsername(username);
            if (teacher == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "找不到该老师信息");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", teacher);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取老师信息失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    

    /**
     * 根据指定的老师ID查询班级学生数量
     */
    @GetMapping("/class-student-count/{teacherId}")
    public ResponseEntity<Map<String, Object>> getClassStudentCountByTeacherId(@PathVariable Integer teacherId) {
        try {
            if (teacherId == null || teacherId <= 0) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "无效的老师ID");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // 查询该老师班级的学生数量
            Integer studentCount = studentDao.getStudentCountByTeacherId(teacherId);
            if (studentCount == null) {
                studentCount = 0;
            }
            
            // 获取老师信息，包括班级名称
            Teacher teacher = teacherService.findByTeacherId(teacherId);
            if (teacher == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "找不到该老师信息");
                return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "查询班级学生数量失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
