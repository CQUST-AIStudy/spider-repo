package com.tap.backend.service;

import com.tap.backend.domain.classroom.ClassStudentEntity;
import com.tap.backend.domain.classroom.TeachingClassEntity;
import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.repo.ClassStudentRepository;
import com.tap.backend.repo.TeachingClassRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public TeachingClassEntity createClass(
            UserEntity teacher,
            String name,
            String classCode,
            String joinPassword,
            String grade,
            String courseName,
            String description,
            String ptaKeyword,
            Boolean syncEnabled
    ) {
        if (classRepo.existsByClassCode(classCode)) {
            throw new IllegalArgumentException("class code already exists: " + classCode);
        }
        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setTeacher(teacher);
        teachingClass.setName(name);
        teachingClass.setClassCode(classCode);
        teachingClass.setJoinPassword(joinPassword);
        teachingClass.setGrade(grade);
        teachingClass.setCourseName(courseName);
        teachingClass.setDescription(description);
        if (ptaKeyword != null && !ptaKeyword.isBlank()) {
            teachingClass.setPtaKeyword(ptaKeyword);
        }
        if (syncEnabled != null) {
            teachingClass.setSyncEnabled(syncEnabled);
        }
        return classRepo.save(teachingClass);
    }

    @Transactional
    public TeachingClassEntity updateClass(
            Long classId,
            Long teacherId,
            String name,
            String joinPassword,
            String grade,
            String courseName,
            String description,
            String ptaKeyword,
            Boolean syncEnabled
    ) {
        TeachingClassEntity teachingClass = requireOwnedClass(classId, teacherId);
        if (name != null) {
            teachingClass.setName(name);
        }
        if (joinPassword != null) {
            teachingClass.setJoinPassword(joinPassword);
        }
        if (grade != null) {
            teachingClass.setGrade(grade);
        }
        if (courseName != null) {
            teachingClass.setCourseName(courseName);
        }
        if (description != null) {
            teachingClass.setDescription(description);
        }
        if (ptaKeyword != null) {
            teachingClass.setPtaKeyword(ptaKeyword);
        }
        if (syncEnabled != null) {
            teachingClass.setSyncEnabled(syncEnabled);
        }
        return classRepo.save(teachingClass);
    }

    @Transactional
    public void deleteClass(Long classId, Long teacherId) {
        classRepo.delete(requireOwnedClass(classId, teacherId));
    }

    @Transactional(readOnly = true)
    public List<ClassStudentEntity> listStudents(Long classId) {
        return studentRepo.findAllByClassId(classId);
    }

    @Transactional(readOnly = true)
    public List<ClassStudentEntity> listStudentsForTeacher(Long classId, Long teacherId) {
        requireOwnedClass(classId, teacherId);
        return studentRepo.findAllByClassId(classId);
    }

    @Transactional
    public ClassStudentEntity addStudent(Long classId, String studentName, String studentNum, Long userId) {
        if (studentNum != null && studentRepo.existsByClassIdAndStudentNum(classId, studentNum)) {
            throw new IllegalArgumentException("student number already exists in this class");
        }
        TeachingClassEntity teachingClass = classRepo.findById(classId)
                .orElseThrow(() -> new NoSuchElementException("class not found"));
        ClassStudentEntity student = new ClassStudentEntity();
        student.setTeachingClass(teachingClass);
        student.setStudentName(studentName);
        student.setStudentNum(studentNum);
        student.setUserId(userId);
        return studentRepo.save(student);
    }

    @Transactional
    public ClassStudentEntity addStudentForTeacher(
            Long classId,
            Long teacherId,
            String studentName,
            String studentNum,
            Long userId
    ) {
        requireOwnedClass(classId, teacherId);
        return addStudent(classId, studentName, studentNum, userId);
    }

    @Transactional
    public void removeStudent(Long studentRecordId) {
        studentRepo.deleteById(studentRecordId);
    }

    @Transactional
    public void removeStudentForTeacher(Long classId, Long studentRecordId, Long teacherId) {
        requireOwnedClass(classId, teacherId);
        ClassStudentEntity student = studentRepo.findById(studentRecordId)
                .orElseThrow(() -> new NoSuchElementException("student not found"));
        if (!classId.equals(student.getClassId())) {
            throw new NoSuchElementException("student not found");
        }
        studentRepo.delete(student);
    }

    @Transactional
    public ClassStudentEntity joinClass(
            String classCode,
            String password,
            String studentName,
            String studentNum,
            Long userId
    ) {
        TeachingClassEntity teachingClass = classRepo.findByClassCode(classCode)
                .orElseThrow(() -> new NoSuchElementException("class code not found"));
        if (!teachingClass.getJoinPassword().equals(password)) {
            throw new SecurityException("invalid class password");
        }
        if (studentNum != null && studentRepo.existsByClassIdAndStudentNum(teachingClass.getId(), studentNum)) {
            throw new IllegalArgumentException("student already joined this class");
        }
        ClassStudentEntity student = new ClassStudentEntity();
        student.setTeachingClass(teachingClass);
        student.setStudentName(studentName);
        student.setStudentNum(studentNum);
        student.setUserId(userId);
        return studentRepo.save(student);
    }

    @Transactional(readOnly = true)
    public long countStudents(Long classId) {
        return studentRepo.countByClassId(classId);
    }

    @Transactional(readOnly = true)
    public List<ClassStudentEntity> listClassesByUser(Long userId) {
        return studentRepo.findAllByUserId(userId);
    }

    private TeachingClassEntity requireOwnedClass(Long classId, Long teacherId) {
        TeachingClassEntity teachingClass = classRepo.findById(classId)
                .orElseThrow(() -> new NoSuchElementException("class not found"));
        if (!teacherId.equals(teachingClass.getTeacherId())) {
            throw new SecurityException("forbidden");
        }
        return teachingClass;
    }
}
