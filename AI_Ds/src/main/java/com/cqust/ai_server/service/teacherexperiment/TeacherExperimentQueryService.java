package com.cqust.ai_server.service.teacherexperiment;

import com.cqust.ai_server.teacherexperiment.TeacherExperimentListResult;
import com.cqust.ai_server.teacherexperiment.TeacherStudentExperimentResult;

public interface TeacherExperimentQueryService {

    TeacherExperimentListResult getTeacherExperimentList(Integer teacherId);

    TeacherStudentExperimentResult getAllStudentExperiments(Integer teacherId);
}
