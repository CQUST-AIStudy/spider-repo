package com.cqust.ai_server.controller.Teacher;

import com.cqust.ai_server.dao.StudentDao;
import com.cqust.ai_server.entity.Experiment;
import com.cqust.ai_server.entity.Score;
import com.cqust.ai_server.entity.Student;
import com.cqust.ai_server.entity.UserEntity;
import com.cqust.ai_server.entity.teacher.Teacher;
import com.cqust.ai_server.entity.teacher.TeacherExperiment;
import com.cqust.ai_server.security.LegacySessionAccessResolver;
import com.cqust.ai_server.service.ExperimentService;
import com.cqust.ai_server.service.ScoreService;
import com.cqust.ai_server.service.TeacherService;
import jakarta.servlet.http.HttpServletRequest;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher")
public class ExperimentController {

    @Autowired
    private ExperimentService experimentService;

    @Autowired
    private ScoreService scoreService;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private LegacySessionAccessResolver legacySessionAccessResolver;

    @GetMapping("/experiments")
    public ResponseEntity<Map<String, Object>> getTeacherExperimentList(HttpServletRequest request) {
        try {
            Teacher teacher = requireCurrentTeacher(request);
            Integer teacherId = teacher.getTeacher_id();
            int studentCount = getStudentCount(teacherId);
            List<Experiment> experiments = experimentService.findExperimentsByTeacherId(String.valueOf(teacherId));
            List<TeacherExperiment> teacherExperiments = new ArrayList<>();
            DecimalFormat decimalFormat = new DecimalFormat("#.##");

            for (Experiment experiment : experiments) {
                TeacherExperiment teacherExperiment = new TeacherExperiment(
                        experiment.getExperiment_id(),
                        experiment.getName(),
                        experiment.getDeadline(),
                        experiment.getCreatedAt()
                );

                List<Score> experimentScores = scoreService.findByExperimentId(experiment.getExperiment_id());
                long submissionCount = experimentScores.stream()
                        .filter(score -> {
                            String status = score.getStatus();
                            Integer scoreValue = score.getScore();
                            return "completed".equalsIgnoreCase(status)
                                    || (scoreValue != null && scoreValue > 0);
                        })
                        .map(Score::getUsername)
                        .distinct()
                        .count();
                teacherExperiment.setSubmissionCount((int) submissionCount);

                double totalScore = experimentScores.stream()
                        .filter(score -> score.getScore() != null && score.getScore() > 0)
                        .mapToInt(Score::getScore)
                        .sum();

                long scoredSubmissionCount = experimentScores.stream()
                        .filter(score -> score.getScore() != null && score.getScore() > 0)
                        .map(Score::getUsername)
                        .distinct()
                        .count();

                if (scoredSubmissionCount > 0 && studentCount > 0) {
                    double averageScore = totalScore / studentCount;
                    teacherExperiment.setAverageScore(Double.parseDouble(decimalFormat.format(averageScore)));
                } else {
                    teacherExperiment.setAverageScore(0.0);
                }

                teacherExperiments.add(teacherExperiment);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", teacherExperiments);
            response.put("total", teacherExperiments.size());
            response.put("studentCount", studentCount);
            response.put("teacherInfo", teacher);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return error("failed to load teacher experiments: " + e.getMessage());
        }
    }

    @GetMapping("/allStudentExperiments")
    public ResponseEntity<Map<String, Object>> getAllStudentExperiments(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Teacher teacher = requireCurrentTeacher(request);
            Integer teacherId = teacher.getTeacher_id();
            List<Student> students = studentDao.getStudentsByTeacherId(teacherId);
            if (students == null || students.isEmpty()) {
                response.put("success", true);
                response.put("data", new ArrayList<>());
                response.put("message", "no students found");
                response.put("teacherInfo", teacher);
                return ResponseEntity.ok(response);
            }

            List<Experiment> experiments = experimentService.findExperimentsByTeacherId(String.valueOf(teacherId));
            List<Map<String, Object>> studentExperimentDataList = new ArrayList<>();

            for (Student student : students) {
                Integer studentId = student.getStudent_id();
                String scoreUsername = String.valueOf(studentId);
                List<Score> studentScores = scoreService.findPerExperimentSumScoresByUsername(scoreUsername);
                if ((studentScores == null || studentScores.isEmpty()) && student.getUsername() != null) {
                    studentScores = scoreService.findPerExperimentSumScoresByUsername(student.getUsername());
                }

                Map<Integer, Score> scoresByExperimentId = studentScores.stream()
                        .collect(Collectors.toMap(
                                Score::getExperiment_id,
                                score -> score,
                                (existing, replacement) -> existing
                        ));

                for (Experiment experiment : experiments) {
                    Map<String, Object> experimentData = new HashMap<>();
                    int experimentId = experiment.getExperiment_id();

                    experimentData.put("studentId", studentId);
                    experimentData.put("studentName", student.getName());
                    experimentData.put("studentUsername", student.getUsername() != null ? student.getUsername() : scoreUsername);
                    experimentData.put("className", student.getClass_name());
                    experimentData.put("experimentId", experimentId);
                    experimentData.put("experimentName", experiment.getName());
                    experimentData.put("deadline", experiment.getDeadline());

                    Score score = scoresByExperimentId.get(experimentId);
                    if (score != null) {
                        experimentData.put("status", "completed");
                        experimentData.put("submitTime", score.getSubmit_time());
                        experimentData.put("score", score.getScore());
                        String plagiarismRate = scoreService.getexperimentPlagiarismRate(studentId, experimentId);
                        double averagePlagiarismRate = calculateAveragePlagiarismRate(plagiarismRate);
                        experimentData.put("plagiarismRate", Math.round(averagePlagiarismRate * 100) / 100.0);
                    } else {
                        experimentData.put("status", "not_started");
                        experimentData.put("submitTime", null);
                        experimentData.put("score", 0);
                        experimentData.put("plagiarismRate", 0.0);
                    }

                    studentExperimentDataList.add(experimentData);
                }
            }

            studentExperimentDataList.sort((left, right) -> {
                String leftClass = (String) left.get("className");
                String rightClass = (String) right.get("className");
                int classCompare = safeString(leftClass).compareTo(safeString(rightClass));
                if (classCompare != 0) {
                    return classCompare;
                }
                Integer leftStudentId = (Integer) left.get("studentId");
                Integer rightStudentId = (Integer) right.get("studentId");
                return leftStudentId.compareTo(rightStudentId);
            });

            response.put("success", true);
            response.put("data", studentExperimentDataList);
            response.put("total", studentExperimentDataList.size());
            response.put("teacherInfo", teacher);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "failed to load student experiments: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/class")
    public ResponseEntity<Map<String, Object>> getClass(HttpServletRequest request) {
        try {
            Teacher teacher = requireCurrentTeacher(request);
            Map<String, Object> response = new HashMap<>();
            response.put("id", teacher.getClassroom());
            response.put("name", teacher.getClassroom());
            response.put("grade", "");
            response.put("studentCount", getStudentCount(teacher.getTeacher_id()));
            response.put("teacherId", teacher.getTeacher_id());
            response.put("teacherName", teacher.getTeacher_name());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return error("failed to load class info: " + e.getMessage());
        }
    }

    @GetMapping("/studentList")
    public ResponseEntity<Map<String, Object>> getStudentList(HttpServletRequest request) {
        try {
            Teacher teacher = requireCurrentTeacher(request);
            List<Student> students = studentDao.getStudentsByTeacherId(teacher.getTeacher_id());
            Map<String, Object> response = new HashMap<>();
            response.put("students", students == null ? new ArrayList<>() : students);
            response.put("teacherId", teacher.getTeacher_id());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return error("failed to load student list: " + e.getMessage());
        }
    }

    private Teacher requireCurrentTeacher(HttpServletRequest request) {
        UserEntity user = legacySessionAccessResolver.requireAuthenticated(request);
        String role = normalize(user.getRole());
        if (!"teacher".equals(role) && !"admin".equals(role)) {
            throw new IllegalStateException("teacher role required");
        }

        String username = normalize(user.getUsername());
        Teacher teacher = username == null ? null : teacherService.findByUsername(username);
        if (teacher == null) {
            throw new IllegalStateException("teacher info not found");
        }
        return teacher;
    }

    private int getStudentCount(Integer teacherId) {
        Integer studentCount = teacherId == null ? null : studentDao.getStudentCountByTeacherId(teacherId);
        return studentCount == null ? 0 : studentCount;
    }

    private double calculateAveragePlagiarismRate(String plagiarismRates) {
        if (plagiarismRates == null || plagiarismRates.isEmpty()) {
            return 0.0;
        }

        String[] rates = plagiarismRates.split(",");
        double sum = 0.0;
        int count = 0;
        for (String rate : rates) {
            if ("-".equals(rate.trim())) {
                continue;
            }
            try {
                sum += Double.parseDouble(rate.replace("%", "").trim());
                count++;
            } catch (NumberFormatException ignored) {
                // Ignore malformed rate fragments and keep the remaining values.
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    private ResponseEntity<Map<String, Object>> error(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return ResponseEntity.badRequest().body(response);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
