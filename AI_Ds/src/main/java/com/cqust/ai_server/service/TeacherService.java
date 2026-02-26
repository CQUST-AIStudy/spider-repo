package com.cqust.ai_server.service;

import com.cqust.ai_server.entity.teacher.Teacher;

public interface TeacherService {
    
    /**
     * 根据用户名查询老师信息
     * @param username 用户名
     * @return 老师信息
     */
    Teacher findByUsername(String username);
    
    /**
     * 根据用户名获取老师ID
     * @param username 用户名
     * @return 老师ID
     */
    Integer findTeacherIdByUsername(String username);
    
    /**
     * 根据老师ID查询老师信息
     * @param teacherId 老师ID
     * @return 老师信息
     */
    Teacher findByTeacherId(int teacherId);
}