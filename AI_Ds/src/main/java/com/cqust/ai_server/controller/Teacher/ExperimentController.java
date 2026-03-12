package com.cqust.ai_server.controller.Teacher;

import com.cqust.ai_server.dao.StudentDao;
import com.cqust.ai_server.entity.Experiment;
import com.cqust.ai_server.entity.Score;
import com.cqust.ai_server.entity.Student;
import com.cqust.ai_server.entity.teacher.Teacher;
import com.cqust.ai_server.entity.teacher.TeacherExperiment;
import com.cqust.ai_server.service.ExperimentService;
import com.cqust.ai_server.service.ScoreService;
import com.cqust.ai_server.service.TeacherService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private TeacherController teacherController;

        @GetMapping("/experiments")
    public ResponseEntity<Map<String, Object>> getTeacherExperimentList(HttpServletRequest request){
        try {
            // 使用TeacherController的getTeacherInfo方法获取老师信息
            ResponseEntity<Map<String, Object>> teacherInfoResponse = teacherController.getTeacherInfo(request);
            System.out.println(teacherInfoResponse);
            if (teacherInfoResponse.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = teacherInfoResponse.getBody();
                if (responseBody != null && "success".equals(responseBody.get("status"))) {
                    Teacher teacher = (Teacher) responseBody.get("data");
                    Integer teacherId = teacher.getTeacher_id();

                    // 获取老师对应班级的学生总人数
                    ResponseEntity<Map<String, Object>> studentCountResponse = teacherController.getClassStudentCountByTeacherId(teacherId);
                    System.out.println("完整的studentCountResponse: " + studentCountResponse);
                    System.out.println("studentCountResponse.getBody(): " + studentCountResponse.getBody());

                    // 默认学生数为0
                    Integer studentCount = 0;

                    if (studentCountResponse.getBody() != null) {
                        // 获取data字段
                        Map<String, Object> responseData = (Map<String, Object>) studentCountResponse.getBody().get("data");
                        System.out.println("解析出的responseData: " + responseData);

                        if (responseData != null) {
                            // 直接从responseData获取studentCount
                            Object countObject = responseData.get("studentCount");
                            System.out.println("解析出的studentCount对象: " + countObject);

                            if (countObject != null) {
                                // 根据实际类型处理计数
                                if (countObject instanceof Integer) {
                                    studentCount = (Integer) countObject;
                                } else if (countObject instanceof Long) {
                                    studentCount = ((Long) countObject).intValue();
                                } else if (countObject instanceof String) {
                                    try {
                                        studentCount = Integer.parseInt((String) countObject);
                                    } catch (NumberFormatException e) {
                                        System.out.println("无法将studentCount转换为整数: " + countObject);
                                    }
                                }
                            }
                        }
                    }

                    System.out.println("最终处理的studentCount: " + studentCount);

                    // 获取当前老师的实验列表，使用老师ID查询
                    List<Experiment> experiments = experimentService.findExperimentsByTeacherId(String.valueOf(teacherId));
                    System.out.println("当前老师Experiments: " + experiments);
                    // 将实验转换为 TeacherExperiment 格式
                    List<TeacherExperiment> teacherExperiments = new ArrayList<>();

                    // 创建小数格式化对象，保留两位小数
                    DecimalFormat df = new DecimalFormat("#.##");

                    for (Experiment exp : experiments) {
                        TeacherExperiment teacherExp = new TeacherExperiment(
                            exp.getExperiment_id(),
                            exp.getName(),
                            exp.getDeadline(),
                            exp.getCreatedAt()
                        );

                        // 获取该实验的所有成绩
                        List<Score> experimentScores = scoreService.findByExperimentId(exp.getExperiment_id());
                        System.out.println("Experiment Scores: " + experimentScores);
                        // 计算完成人数（兼容历史数据：status 可能未维护，score>0 也视为已完成）
                        long submissionCount = experimentScores.stream()
                            .filter(score -> {
                                String status = score.getStatus();
                                Integer scoreVal = score.getScore();
                                return "completed".equalsIgnoreCase(status)
                                        || (scoreVal != null && scoreVal > 0);
                            })
                            .map(Score::getUsername)
                            .distinct()
                            .count();
                        teacherExp.setSubmissionCount((int) submissionCount);

                        // 计算平均分 - 修复计算逻辑
                        double totalScore = experimentScores.stream()
                            .filter(score -> score.getScore() != null && score.getScore() > 0)
                            .mapToInt(Score::getScore)
                            .sum();

                        // 使用实际提交成绩的人数作为除数，而不是班级总人数
                        long scoredSubmissionCount = experimentScores.stream()
                            .filter(score -> score.getScore() != null && score.getScore() > 0)
                            .map(Score::getUsername)
                            .distinct()
                            .count();

                        // 如果有有效分数的提交，才计算平均分
                        if (scoredSubmissionCount > 0) {
                            double avgScore = totalScore / studentCount;
                            // 格式化为两位小数
                            avgScore = Double.parseDouble(df.format(avgScore));
                            System.out.println("实验ID: " + exp.getExperiment_id() + ", 总分: " + totalScore +
                                ", 有效提交数: " + scoredSubmissionCount + ", 平均分: " + avgScore);
                            teacherExp.setAverageScore(avgScore);
                        } else {
                            System.out.println("实验ID: " + exp.getExperiment_id() + ", 没有有效分数提交");
                            teacherExp.setAverageScore(0.0);
                        }

                        teacherExperiments.add(teacherExp);
                    }

                    // 构建响应体
                    Map<String, Object> response = new HashMap<>();

                    response.put("status", "success");
                    response.put("data", teacherExperiments);
                    response.put("total", teacherExperiments.size());
                    response.put("studentCount", studentCount);
                    response.put("teacherInfo", teacher);

                    return ResponseEntity.ok(response);
                }
            }

            // 如果无法获取老师信息，返回错误
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "无法获取老师信息");
            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取实验列表失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 教师获取所有学生的实验列表
     * @param request HTTP请求
     * @return 包含所有学生实验详情的响应
     */
    @GetMapping("/allStudentExperiments")
    public ResponseEntity<Map<String, Object>> getAllStudentExperiments(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        System.out.println("教师获取所有学生实验列表方法已启动");
        try {
            // 验证教师身份
            HttpSession session = request.getSession(false);
            String currentUsername;
            if (session != null) {
                currentUsername = (String) session.getAttribute("username");
            } else {
                currentUsername = null;
            }

            // 如果用户未登录，返回错误信息
            if (currentUsername == null) {
                response.put("success", false);
                response.put("message", "用户未登录或会话已过期");
                return ResponseEntity.ok(response);
            }

            // 验证是否为教师
            ResponseEntity<Map<String, Object>> teacherInfoResponse = teacherController.getTeacherInfo(request);
            if (!teacherInfoResponse.getStatusCode().is2xxSuccessful() ||
                teacherInfoResponse.getBody() == null ||
                !"success".equals(teacherInfoResponse.getBody().get("status"))) {

                response.put("success", false);
                response.put("message", "无法验证教师身份或权限不足");
                return ResponseEntity.ok(response);
            }

            Teacher teacher = (Teacher) teacherInfoResponse.getBody().get("data");
            Integer teacherId = teacher.getTeacher_id();

            // 获取该教师所有的班级学生
            List<Student> students = studentDao.getStudentsByTeacherId(teacherId);

            if (students == null || students.isEmpty()) {
                response.put("success", true);
                response.put("data", new ArrayList<>());
                response.put("message", "未找到任何学生数据");
                return ResponseEntity.ok(response);
            }

            // 获取所有实验
            List<Experiment> experiments = experimentService.findExperimentsByTeacherId(String.valueOf(teacherId));

            // 学生实验数据列表
            List<Map<String, Object>> studentExperimentDataList = new ArrayList<>();

            // 为每个学生收集实验数据
            for (Student student : students) {
                Integer studentId = student.getStudent_id();
                // score表中username字段存的是student_id数字，不是登录用户名
                String scoreUsername = String.valueOf(studentId);

                // 获取当前学生的所有成绩记录（用student_id作为score表的username查询）
                List<Score> studentScores = scoreService.findPerExperimentSumScoresByUsername(scoreUsername);
                // 如果用student_id查不到，尝试用登录用户名查
                if ((studentScores == null || studentScores.isEmpty()) && student.getUsername() != null) {
                    studentScores = scoreService.findPerExperimentSumScoresByUsername(student.getUsername());
                }
                Map<Integer, Score> scoresByExperimentId = studentScores.stream()
                        .collect(Collectors.toMap(Score::getExperiment_id, score -> score, (existing, replacement) -> existing));

                // 为每个实验创建数据条目
                for (Experiment experiment : experiments) {
                    Map<String, Object> experimentData = new HashMap<>();
                    int experimentId = experiment.getExperiment_id();

                    // 基本学生信息
                    experimentData.put("studentId", studentId);
                    experimentData.put("studentName", student.getName());
                    experimentData.put("studentUsername", student.getUsername() != null ? student.getUsername() : scoreUsername);
                    experimentData.put("className", student.getClass_name());

                    // 基本实验信息
                    experimentData.put("experimentId", experimentId);
                    experimentData.put("experimentName", experiment.getName());
                    experimentData.put("deadline", experiment.getDeadline());

                    // 获取学生该实验的成绩信息
                    Score score = scoresByExperimentId.get(experimentId);

                    if (score != null) {
                        experimentData.put("status", "completed");
                        experimentData.put("submitTime", score.getSubmit_time());
                        experimentData.put("score", score.getScore());

                        // 计算平均查重率
                        String plagiarismRateStr = scoreService.getexperimentPlagiarismRate(studentId, experimentId);
                        double avgPlagiarismRate = calculateAveragePlagiarismRate(plagiarismRateStr);
                        avgPlagiarismRate = Math.round(avgPlagiarismRate * 100) / 100.0;
                        experimentData.put("plagiarismRate", avgPlagiarismRate);
                    } else {
                        experimentData.put("status", "not_started");
                        experimentData.put("submitTime", null);
                        experimentData.put("score", 0);
                        experimentData.put("plagiarismRate", 0.0);
                    }

                    studentExperimentDataList.add(experimentData);
                }
            }

            // 按班级和学生ID排序
            studentExperimentDataList.sort((a, b) -> {
                String classA = (String) a.get("className");
                String classB = (String) b.get("className");
                int classCompare = classA.compareTo(classB);

                if (classCompare != 0) {
                    return classCompare;
                } else {
                    Integer studentIdA = (Integer) a.get("studentId");
                    Integer studentIdB = (Integer) b.get("studentId");
                    return studentIdA.compareTo(studentIdB);
                }
            });

            response.put("success", true);
            response.put("data", studentExperimentDataList);
            response.put("total", studentExperimentDataList.size());
            response.put("teacherInfo", teacher);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "获取学生实验列表失败: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

//    /**
//     * 根据特定实验ID获取所有学生的该实验数据
//     * @param experimentId 实验ID
//     * @param request HTTP请求
//     * @return 特定实验的所有学生数据
//     */
//    @GetMapping("/experiment/{experimentId}/students")
//    public ResponseEntity<Map<String, Object>> getExperimentStudentDetails(
//            @PathVariable int experimentId,
//            HttpServletRequest request) {
//
//        Map<String, Object> response = new HashMap<>();
//
//        try {
//            // 验证教师身份
//            ResponseEntity<Map<String, Object>> teacherInfoResponse = teacherController.getTeacherInfo(request);
//            if (!teacherInfoResponse.getStatusCode().is2xxSuccessful() ||
//                teacherInfoResponse.getBody() == null ||
//                !"success".equals(teacherInfoResponse.getBody().get("status"))) {
//
//                response.put("success", false);
//                response.put("message", "无法验证教师身份或权限不足");
//                return ResponseEntity.ok(response);
//            }
//
//            Teacher teacher = (Teacher) teacherInfoResponse.getBody().get("data");
//            Integer teacherId = teacher.getTeacher_id();
//
//            // 验证实验是否属于该教师
//            Experiment experiment = experimentService.findExperimentById(experimentId);
//            if (experiment == null) {
//                response.put("success", false);
//                response.put("message", "未找到指定ID的实验");
//                return ResponseEntity.ok(response);
//            }
//
//            // 获取该教师所有的班级学生
//            List<Student> students = studentDao.getStudentsByTeacherId(teacherId);
//
//            if (students == null || students.isEmpty()) {
//                response.put("success", true);
//                response.put("data", new ArrayList<>());
//                response.put("message", "未找到任何学生数据");
//                return ResponseEntity.ok(response);
//            }
//
//            List<Map<String, Object>> studentExperimentDetails = new ArrayList<>();
//
//            for (Student student : students) {
//                String studentUsername = student.getUsername();
//                Integer studentId = student.getStudent_id();
//
//                // 获取该学生的这个实验的成绩
//                Score score = scoreService.findByUsernameAndExperimentId(studentUsername, experimentId);
//
//                Map<String, Object> studentDetail = new HashMap<>();
//                studentDetail.put("studentId", studentId);
//                studentDetail.put("studentName", student.getName());
//                studentDetail.put("studentUsername", studentUsername);
//                studentDetail.put("className", student.getClass_name());
//
//                if (score != null) {
//                    studentDetail.put("status", "completed");
//                    studentDetail.put("submitTime", score.getSubmit_time());
//                    studentDetail.put("score", score.getScore());
//                    studentDetail.put("comments", score.getComments());
//
//                    // 计算平均查重率
//                    String plagiarismRateStr = scoreService.getexperimentPlagiarismRate(studentId, experimentId);
//                    double avgPlagiarismRate = calculateAveragePlagiarismRate(plagiarismRateStr);
//                    avgPlagiarismRate = Math.round(avgPlagiarismRate * 100) / 100.0;
//                    studentDetail.put("plagiarismRate", avgPlagiarismRate);
//                } else {
//                    studentDetail.put("status", "not_started");
//                    studentDetail.put("submitTime", null);
//                    studentDetail.put("score", 0);
//                    studentDetail.put("comments", null);
//                    studentDetail.put("plagiarismRate", 0.0);
//                }
//
//                studentExperimentDetails.add(studentDetail);
//            }
//
//            // 按班级和学生姓名排序
//            studentExperimentDetails.sort((a, b) -> {
//                String classA = (String) a.get("className");
//                String classB = (String) b.get("className");
//                int classCompare = classA.compareTo(classB);
//
//                if (classCompare != 0) {
//                    return classCompare;
//                } else {
//                    String nameA = (String) a.get("studentName");
//                    String nameB = (String) b.get("studentName");
//                    return nameA.compareTo(nameB);
//                }
//            });
//
//            // 计算统计信息
//            int totalStudents = studentExperimentDetails.size();
//            int submittedCount = (int) studentExperimentDetails.stream()
//                    .filter(s -> "completed".equals(s.get("status")))
//                    .count();
//
//            double averageScore = studentExperimentDetails.stream()
//                    .filter(s -> s.get("score") instanceof Integer && (Integer)s.get("score") > 0)
//                    .mapToInt(s -> (Integer)s.get("score"))
//                    .average()
//                    .orElse(0);
//
//            // 将平均分四舍五入到两位小数
//            averageScore = Math.round(averageScore * 100) / 100.0;
//
//            response.put("success", true);
//            response.put("data", studentExperimentDetails);
//            response.put("experimentInfo", experiment);
//            response.put("statistics", Map.of(
//                    "totalStudents", totalStudents,
//                    "submittedCount", submittedCount,
//                    "submissionRate", totalStudents > 0 ? (double)submittedCount/totalStudents : 0,
//                    "averageScore", averageScore
//            ));
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            response.put("success", false);
//            response.put("message", "获取实验学生详情失败: " + e.getMessage());
//        }
//
//        return ResponseEntity.ok(response);
//    }

    /**
     * 计算平均查重率
     * @param plagiarismRates 查重率字符串，以逗号分隔
     * @return 平均查重率
     */
    private double calculateAveragePlagiarismRate(String plagiarismRates) {
        if (plagiarismRates == null || plagiarismRates.isEmpty()) {
            return 0.0;
        }

        String[] rates = plagiarismRates.split(",");
        double sum = 0.0;
        int count = 0;

        for (String rate : rates) {
            // 跳过"-"值
            if (!rate.trim().equals("-")) {
                try {
                    // 移除百分号并转换为double
                    String cleanRate = rate.replace("%", "").trim();
                    sum += Double.parseDouble(cleanRate);
                    count++;
                } catch (NumberFormatException e) {
                    // 忽略无法解析的值
                    continue;
                }
            }
        }

        return count > 0 ? sum / count : 0.0;
    }


    @GetMapping("/class")
    public ResponseEntity<Map<String, Object>> getClass(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        response.put("id", "C2023001");
        response.put("name", "计算机科学与技术1班");
        response.put("grade", "2023级");
        response.put("studentCount", 49);
        response.put("teacherId", "20001");
        response.put("teacherName", "王老师");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/studentList")
    public ResponseEntity<Map<String, Object>> getStudentList() {
        Map<String, Object> response = new HashMap<>();
        List<Student> students = studentDao.findAllStudents();
        response.put("students", students);
        return ResponseEntity.ok(response);
    }
}
