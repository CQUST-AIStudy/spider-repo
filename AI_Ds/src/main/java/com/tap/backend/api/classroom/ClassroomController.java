package com.tap.backend.api.classroom;

import com.tap.backend.domain.classroom.ClassStudentEntity;
import com.tap.backend.domain.classroom.TeachingClassEntity;
import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.repo.UserRepository;
import com.tap.backend.service.TeachingClassService;
import com.tap.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/classes")
public class ClassroomController {

    private final TeachingClassService classService;
    private final UserRepository userRepo;

    public ClassroomController(TeachingClassService classService, UserRepository userRepo) {
        this.classService = classService;
        this.userRepo = userRepo;
    }

    private UserEntity resolveUser(UserDetails principal) {
        if (principal != null) {
            return userRepo.findByUsername(principal.getUsername())
                    .orElseThrow(() -> new NoSuchElementException("用户不存在"));
        }
        // 如果没有JWT认证，尝试使用第一个教师用户作为默认
        return userRepo.findAll().stream()
                .filter(u -> u.getRole() != null && u.getRole().name().equals("TEACHER"))
                .findFirst()
                .orElseGet(() -> userRepo.findAll().stream().findFirst()
                        .orElseThrow(() -> new NoSuchElementException("系统中没有用户")));
    }

    // ========== 教师端 ==========

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listClasses(@AuthenticationPrincipal UserDetails principal) {
        UserEntity user = resolveUser(principal);
        List<TeachingClassEntity> classes = classService.listByTeacher(user.getId());
        // 如果当前教师没有班级，返回所有班级（方便开发调试）
        if (classes.isEmpty()) {
            classes = classService.listAll();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeachingClassEntity tc : classes) {
            result.add(toMap(tc));
        }
        return ApiResponse.of(result);
    }

    record CreateClassRequest(String name, String classCode, String joinPassword,
                              String grade, String courseName, String description,
                              String ptaKeyword, Boolean syncEnabled) {}

    @PostMapping
    public ApiResponse<Map<String, Object>> createClass(@AuthenticationPrincipal UserDetails principal,
                                                         @RequestBody CreateClassRequest req) {
        UserEntity user = resolveUser(principal);
        TeachingClassEntity tc = classService.createClass(user, req.name(), req.classCode(),
                req.joinPassword(), req.grade(), req.courseName(), req.description(),
                req.ptaKeyword(), req.syncEnabled());
        return ApiResponse.of(toMap(tc));
    }

    record UpdateClassRequest(String name, String joinPassword, String grade,
                              String courseName, String description,
                              String ptaKeyword, Boolean syncEnabled) {}

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateClass(@AuthenticationPrincipal UserDetails principal,
                                                         @PathVariable Long id,
                                                         @RequestBody UpdateClassRequest req) {
        UserEntity user = resolveUser(principal);
        TeachingClassEntity tc = classService.updateClass(id, user.getId(), req.name(),
                req.joinPassword(), req.grade(), req.courseName(), req.description(),
                req.ptaKeyword(), req.syncEnabled());
        return ApiResponse.of(toMap(tc));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteClass(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable Long id) {
        UserEntity user = resolveUser(principal);
        classService.deleteClass(id, user.getId());
        return ApiResponse.of(null);
    }

    // ========== 班级学生管理 ==========

    @GetMapping("/{id}/students")
    public ApiResponse<List<Map<String, Object>>> listStudents(@PathVariable Long id) {
        List<ClassStudentEntity> students = classService.listStudents(id);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassStudentEntity cs : students) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", cs.getId());
            m.put("studentName", cs.getStudentName());
            m.put("studentNum", cs.getStudentNum());
            m.put("userId", cs.getUserId());
            m.put("joinedAt", cs.getJoinedAt());
            result.add(m);
        }
        return ApiResponse.of(result);
    }

    record AddStudentRequest(String studentName, String studentNum) {}

    @PostMapping("/{id}/students")
    public ApiResponse<Map<String, Object>> addStudent(@PathVariable Long id,
                                                        @RequestBody AddStudentRequest req) {
        ClassStudentEntity cs = classService.addStudent(id, req.studentName(), req.studentNum(), null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", cs.getId());
        m.put("studentName", cs.getStudentName());
        m.put("studentNum", cs.getStudentNum());
        m.put("joinedAt", cs.getJoinedAt());
        return ApiResponse.of(m);
    }

    @DeleteMapping("/{classId}/students/{studentId}")
    public ApiResponse<Void> removeStudent(@PathVariable Long classId, @PathVariable Long studentId) {
        classService.removeStudent(studentId);
        return ApiResponse.of(null);
    }

    // ========== 学生端：加入班级 ==========

    record JoinClassRequest(String classCode, String password, String studentName, String studentNum) {}

    @PostMapping("/join")
    public ApiResponse<Map<String, Object>> joinClass(@AuthenticationPrincipal UserDetails principal,
                                                       @RequestBody JoinClassRequest req) {
        Long userId = null;
        if (principal != null) {
            UserEntity user = resolveUser(principal);
            userId = user.getId();
        }
        ClassStudentEntity cs = classService.joinClass(req.classCode(), req.password(),
                req.studentName(), req.studentNum(), userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", cs.getId());
        m.put("classId", cs.getClassId());
        m.put("studentName", cs.getStudentName());
        m.put("joinedAt", cs.getJoinedAt());
        return ApiResponse.of(m);
    }

    // ========== helpers ==========

    private Map<String, Object> toMap(TeachingClassEntity tc) {
        long studentCount = classService.countStudents(tc.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", tc.getId());
        m.put("name", tc.getName());
        m.put("classCode", tc.getClassCode());
        m.put("joinPassword", tc.getJoinPassword());
        m.put("grade", tc.getGrade());
        m.put("courseName", tc.getCourseName());
        m.put("description", tc.getDescription());
        m.put("studentCount", studentCount);
        m.put("ptaKeyword", tc.getPtaKeyword());
        m.put("syncEnabled", tc.getSyncEnabled());
        m.put("lastSyncAt", tc.getLastSyncAt());
        m.put("syncStatus", tc.getSyncStatus());
        m.put("createdAt", tc.getCreatedAt());
        m.put("updatedAt", tc.getUpdatedAt());
        return m;
    }
}
