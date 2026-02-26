package com.tap.backend.service;

import com.tap.backend.domain.classroom.ClassStudentEntity;
import com.tap.backend.domain.classroom.TeachingClassEntity;
import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.repo.ClassStudentRepository;
import com.tap.backend.repo.TeachingClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TeachingClassService {

    private final TeachingClassRepository classRepo;
    private final ClassStudentRepository studentRepo;

    public TeachingClassService(TeachingClassRepository classRepo, ClassStudentRepository studentRepo) {
        this.classRepo = classRepo;
        this.studentRepo = studentRepo;
    }

    @Transactional(readOnly = true)
    public List<TeachingClassEntity> listByTeacher(Long teacherId) {
        return classRepo.findAllByTeacherId(teacherId);
    }

    @Transactional(readOnly = true)
    public List<TeachingClassEntity> listAll() {
        return classRepo.findAll();
    }

    @Transactional
    public TeachingClassEntity createClass(UserEntity teacher, String name, String classCode,
                                           String joinPassword, String grade, String courseName, String description) {
        if (classRepo.existsByClassCode(classCode)) {
            throw new IllegalArgumentException("班级号已存在: " + classCode);
        }
        TeachingClassEntity tc = new TeachingClassEntity();
        tc.setTeacher(teacher);
        tc.setName(name);
        tc.setClassCode(classCode);
        tc.setJoinPassword(joinPassword);
        tc.setGrade(grade);
        tc.setCourseName(courseName);
        tc.setDescription(description);
        return classRepo.save(tc);
    }

    @Transactional
    public TeachingClassEntity updateClass(Long classId, Long teacherId, String name,
                                           String joinPassword, String grade, String courseName, String description,
                                           String ptaKeyword, Boolean syncEnabled) {
        TeachingClassEntity tc = classRepo.findById(classId)
                .orElseThrow(() -> new NoSuchElementException("班级不存在"));
        if (!tc.getTeacherId().equals(teacherId)) {
            throw new SecurityException("无权修改此班级");
        }
        if (name != null) tc.setName(name);
        if (joinPassword != null) tc.setJoinPassword(joinPassword);
        if (grade != null) tc.setGrade(grade);
        if (courseName != null) tc.setCourseName(courseName);
        if (description != null) tc.setDescription(description);
        if (ptaKeyword != null) tc.setPtaKeyword(ptaKeyword);
        if (syncEnabled != null) tc.setSyncEnabled(syncEnabled);
        return classRepo.save(tc);
    }

    @Transactional
    public void deleteClass(Long classId, Long teacherId) {
        TeachingClassEntity tc = classRepo.findById(classId)
                .orElseThrow(() -> new NoSuchElementException("班级不存在"));
        if (!tc.getTeacherId().equals(teacherId)) {
            throw new SecurityException("无权删除此班级");
        }
        classRepo.delete(tc);
    }

    @Transactional(readOnly = true)
    public List<ClassStudentEntity> listStudents(Long classId) {
        return studentRepo.findAllByClassId(classId);
    }

    @Transactional
    public ClassStudentEntity addStudent(Long classId, String studentName, String studentNum, Long userId) {
        if (studentNum != null && studentRepo.existsByClassIdAndStudentNum(classId, studentNum)) {
            throw new IllegalArgumentException("该学号已在班级中");
        }
        TeachingClassEntity tc = classRepo.findById(classId)
                .orElseThrow(() -> new NoSuchElementException("班级不存在"));
        ClassStudentEntity cs = new ClassStudentEntity();
        cs.setTeachingClass(tc);
        cs.setStudentName(studentName);
        cs.setStudentNum(studentNum);
        cs.setUserId(userId);
        return studentRepo.save(cs);
    }

    @Transactional
    public void removeStudent(Long studentRecordId) {
        studentRepo.deleteById(studentRecordId);
    }

    /** 学生通过班级号+密码加入班级 */
    @Transactional
    public ClassStudentEntity joinClass(String classCode, String password, String studentName, String studentNum, Long userId) {
        TeachingClassEntity tc = classRepo.findByClassCode(classCode)
                .orElseThrow(() -> new NoSuchElementException("班级号不存在"));
        if (!tc.getJoinPassword().equals(password)) {
            throw new SecurityException("班级密码错误");
        }
        if (studentNum != null && studentRepo.existsByClassIdAndStudentNum(tc.getId(), studentNum)) {
            throw new IllegalArgumentException("你已加入该班级");
        }
        ClassStudentEntity cs = new ClassStudentEntity();
        cs.setTeachingClass(tc);
        cs.setStudentName(studentName);
        cs.setStudentNum(studentNum);
        cs.setUserId(userId);
        return studentRepo.save(cs);
    }

    @Transactional(readOnly = true)
    public long countStudents(Long classId) {
        return studentRepo.countByClassId(classId);
    }

    @Transactional(readOnly = true)
    public List<ClassStudentEntity> listClassesByUser(Long userId) {
        return studentRepo.findAllByUserId(userId);
    }
}
