package com.cqust.ai_server.service;

import com.cqust.ai_server.entity.StudentCode;
import java.util.List;

public interface StudentCodeService {
    
    /**
     * 根据学生ID查询所有代码
     * @param studentId 学生ID
     * @return 学生代码列表
     */
    List<StudentCode> findCodeByStudentId(int studentId);
    
    /**
     * 根据学生ID和实验ID查询特定代码
     * @param studentId 学生ID
     * @param experimentId 实验ID
     * @return 学生代码
     */
    StudentCode findCodeByStudentIdAndExperimentId(int studentId, int experimentId);
}