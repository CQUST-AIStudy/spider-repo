package com.tap.backend.repo;

import com.tap.backend.domain.classroom.ClassStudentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClassStudentRepository extends JpaRepository<ClassStudentEntity, Long> {
    List<ClassStudentEntity> findAllByClassId(Long classId);
    long countByClassId(Long classId);
    boolean existsByClassIdAndStudentNum(Long classId, String studentNum);
    List<ClassStudentEntity> findAllByUserId(Long userId);
}
