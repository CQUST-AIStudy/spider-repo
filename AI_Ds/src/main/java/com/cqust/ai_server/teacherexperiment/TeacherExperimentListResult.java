package com.cqust.ai_server.teacherexperiment;

import com.cqust.ai_server.entity.teacher.TeacherExperiment;
import java.util.Collections;
import java.util.List;

public class TeacherExperimentListResult {

    private final List<TeacherExperiment> experiments;
    private final int studentCount;

    public TeacherExperimentListResult(List<TeacherExperiment> experiments, int studentCount) {
        this.experiments = experiments == null ? Collections.emptyList() : experiments;
        this.studentCount = studentCount;
    }

    public List<TeacherExperiment> getExperiments() {
        return experiments;
    }

    public int getStudentCount() {
        return studentCount;
    }
}
