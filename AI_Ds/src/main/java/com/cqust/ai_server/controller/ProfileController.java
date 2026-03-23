package com.cqust.ai_server.controller;

import com.cqust.ai_server.entity.UserEntity;
import com.cqust.ai_server.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private ProfileService profileService;

    /**
     * 学生画像 - 学生自己查看或教师查看指定学生
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<Map<String, Object>> getStudentProfile(@PathVariable String studentId) {
        Map<String, Object> profile = profileService.getStudentProfile(studentId);
        return ResponseEntity.ok(profile);
    }

    /**
     * 学生查看自己的画像（通过session中的学号）
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyProfile(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("currentUser") == null) {
            return ResponseEntity.ok(Map.of("error", "未登录"));
        }
        // 直接 cast 成 UserEntity，避免反射
        UserEntity user = (UserEntity) session.getAttribute("currentUser");
        String usernum = user.getUsernum();
        if (usernum == null || usernum.isBlank()) {
            return ResponseEntity.ok(Map.of("error", "未绑定学号"));
        }
        Map<String, Object> profile = profileService.getStudentProfile(usernum);
        return ResponseEntity.ok(profile);
    }

    /**
     * 班级画像 - 教师查看
     */
    @GetMapping("/class")
    public ResponseEntity<Map<String, Object>> getClassProfile(
            HttpServletRequest request,
            @RequestParam(required = false) String className) {
        String scopedClassName = normalizeClassName(className);
        if (scopedClassName == null) {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("currentUser") != null) {
                UserEntity user = (UserEntity) session.getAttribute("currentUser");
                scopedClassName = normalizeClassName(user.getClassname());
            }
        }
        Map<String, Object> profile = profileService.getClassProfile(scopedClassName);
        return ResponseEntity.ok(profile);
    }

    /**
     * 技能树配置
     */
    @GetMapping("/skilltree")
    public ResponseEntity<Map<String, Object>> getSkillTree() {
        Map<String, Object> tree = profileService.getSkillTreeConfig();
        return ResponseEntity.ok(tree);
    }

    /**
     * 强制刷新AI学习建议 - 重新调用DeepSeek分析
     */
    @PostMapping("/feedback/refresh/{studentId}")
    public ResponseEntity<Map<String, Object>> refreshFeedback(@PathVariable String studentId) {
        Map<String, Object> result = profileService.refreshFeedback(studentId);
        return ResponseEntity.ok(result);
    }

    /**
     * 学生自己刷新AI学习建议
     */
    @PostMapping("/feedback/refresh/me")
    public ResponseEntity<Map<String, Object>> refreshMyFeedback(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("currentUser") == null) {
            return ResponseEntity.ok(Map.of("error", "未登录"));
        }
        UserEntity user = (UserEntity) session.getAttribute("currentUser");
        String usernum = user.getUsernum();
        if (usernum == null || usernum.isBlank()) {
            return ResponseEntity.ok(Map.of("error", "未绑定学号"));
        }
        Map<String, Object> result = profileService.refreshFeedback(usernum);
        return ResponseEntity.ok(result);
    }
    private String normalizeClassName(String className) {
        if (className == null) {
            return null;
        }
        String trimmed = className.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
