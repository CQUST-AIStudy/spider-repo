package com.cqust.ai_server.service.teacherexperiment;

import com.cqust.ai_server.dao.StudentDao;
import com.cqust.ai_server.dao.teacherexperiment.TeacherExperimentQueryDao;
import com.cqust.ai_server.entity.Experiment;
import com.cqust.ai_server.entity.Student;
import com.cqust.ai_server.entity.teacher.TeacherExperiment;
import com.cqust.ai_server.service.ExperimentService;
import com.cqust.ai_server.teacherexperiment.TeacherExperimentListResult;
import com.cqust.ai_server.teacherexperiment.TeacherExperimentPlagiarismRow;
import com.cqust.ai_server.teacherexperiment.TeacherExperimentScoreAggregate;
import com.cqust.ai_server.teacherexperiment.TeacherExperimentScoreRow;
import com.cqust.ai_server.teacherexperiment.TeacherStudentExperimentResult;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TeacherExperimentQueryServiceImpl implements TeacherExperimentQueryService {

    @Autowired
    private ExperimentService experimentService;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private TeacherExperimentQueryDao teacherExperimentQueryDao;

    @Override
    public TeacherExperimentListResult getTeacherExperimentList(Integer teacherId) {
        int studentCount = getStudentCount(teacherId);
        List<Experiment> experiments = findTeacherExperiments(teacherId);
        if (experiments.isEmpty()) {
            return new TeacherExperimentListResult(Collections.emptyList(), studentCount);
        }

        List<Integer> experimentIds = experiments.stream()
                .map(Experiment::getExperiment_id)
                .collect(Collectors.toList());
        Map<Integer, TeacherExperimentScoreAggregate> aggregateByExperimentId = teacherExperimentQueryDao
                .summarizeByExperimentIds(experimentIds)
                .stream()
                .filter(aggregate -> aggregate.getExperimentId() != null)
                .collect(Collectors.toMap(
                        TeacherExperimentScoreAggregate::getExperimentId,
                        aggregate -> aggregate,
                        (left, right) -> left
                ));

        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        List<TeacherExperiment> teacherExperiments = new ArrayList<>();
        for (Experiment experiment : experiments) {
            TeacherExperiment teacherExperiment = new TeacherExperiment(
                    experiment.getExperiment_id(),
                    experiment.getName(),
                    experiment.getDeadline(),
                    experiment.getCreatedAt()
            );

            TeacherExperimentScoreAggregate aggregate = aggregateByExperimentId.get(experiment.getExperiment_id());
            int submissionCount = aggregate == null || aggregate.getSubmissionCount() == null
                    ? 0
                    : aggregate.getSubmissionCount();
            teacherExperiment.setSubmissionCount(submissionCount);

            int totalPositiveScore = aggregate == null || aggregate.getTotalPositiveScore() == null
                    ? 0
                    : aggregate.getTotalPositiveScore();
            double averageScore = studentCount > 0
                    ? Double.parseDouble(decimalFormat.format((double) totalPositiveScore / studentCount))
                    : 0.0;
            teacherExperiment.setAverageScore(averageScore);
            teacherExperiments.add(teacherExperiment);
        }

        return new TeacherExperimentListResult(teacherExperiments, studentCount);
    }

    @Override
    public TeacherStudentExperimentResult getAllStudentExperiments(Integer teacherId) {
        List<Student> students = studentDao.getStudentsByTeacherId(teacherId);
        if (students == null || students.isEmpty()) {
            return new TeacherStudentExperimentResult(false, Collections.emptyList());
        }

        List<Experiment> experiments = findTeacherExperiments(teacherId);
        if (experiments.isEmpty()) {
            return new TeacherStudentExperimentResult(true, Collections.emptyList());
        }

        List<String> lookupUsernames = collectLookupUsernames(students);
        List<String> studentIds = students.stream()
                .map(student -> String.valueOf(student.getStudent_id()))
                .collect(Collectors.toList());
        List<Integer> experimentIds = experiments.stream()
                .map(Experiment::getExperiment_id)
                .collect(Collectors.toList());

        Map<String, Map<Integer, TeacherExperimentScoreRow>> scoresByUsername = buildScoresByUsername(lookupUsernames);
        Map<String, TeacherExperimentPlagiarismRow> plagiarismByKey = buildPlagiarismByKey(studentIds, experimentIds);

        List<Map<String, Object>> rows = new ArrayList<>(students.size() * experiments.size());
        for (Student student : students) {
            int studentId = student.getStudent_id();
            String studentIdLookup = String.valueOf(studentId);
            String usernameLookup = hasText(student.getUsername()) ? student.getUsername() : null;
            Map<Integer, TeacherExperimentScoreRow> scoresByExperimentId = pickPreferredScores(
                    scoresByUsername,
                    studentIdLookup,
                    usernameLookup
            );

            for (Experiment experiment : experiments) {
                int experimentId = experiment.getExperiment_id();
                Map<String, Object> experimentData = new LinkedHashMap<>();
                experimentData.put("studentId", studentId);
                experimentData.put("studentName", student.getName());
                experimentData.put("studentUsername", hasText(student.getUsername()) ? student.getUsername() : studentIdLookup);
                experimentData.put("className", student.getClass_name());
                experimentData.put("experimentId", experimentId);
                experimentData.put("experimentName", experiment.getName());
                experimentData.put("deadline", experiment.getDeadline());

                TeacherExperimentScoreRow score = scoresByExperimentId.get(experimentId);
                if (score != null) {
                    experimentData.put("status", "completed");
                    experimentData.put("submitTime", score.getSubmitTime());
                    experimentData.put("score", score.getScore());
                    TeacherExperimentPlagiarismRow plagiarism = plagiarismByKey.get(buildKey(studentIdLookup, experimentId));
                    experimentData.put("plagiarismRate", roundTwoDecimals(calculateAveragePlagiarismRate(
                            plagiarism == null ? null : plagiarism.getPlagiarismRate()
                    )));
                } else {
                    experimentData.put("status", "not_started");
                    experimentData.put("submitTime", null);
                    experimentData.put("score", 0);
                    experimentData.put("plagiarismRate", 0.0);
                }
                rows.add(experimentData);
            }
        }

        rows.sort((left, right) -> {
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

        return new TeacherStudentExperimentResult(true, rows);
    }

    private List<Experiment> findTeacherExperiments(Integer teacherId) {
        if (teacherId == null) {
            return Collections.emptyList();
        }
        List<Experiment> experiments = experimentService.findExperimentsByTeacherId(String.valueOf(teacherId));
        return experiments == null ? Collections.emptyList() : experiments;
    }

    private int getStudentCount(Integer teacherId) {
        Integer studentCount = teacherId == null ? null : studentDao.getStudentCountByTeacherId(teacherId);
        return studentCount == null ? 0 : studentCount;
    }

    private List<String> collectLookupUsernames(List<Student> students) {
        List<String> usernames = new ArrayList<>();
        for (Student student : students) {
            usernames.add(String.valueOf(student.getStudent_id()));
            if (hasText(student.getUsername())) {
                usernames.add(student.getUsername());
            }
        }
        return usernames.stream().distinct().collect(Collectors.toList());
    }

    private Map<String, Map<Integer, TeacherExperimentScoreRow>> buildScoresByUsername(List<String> usernames) {
        if (usernames.isEmpty()) {
            return Collections.emptyMap();
        }
        return teacherExperimentQueryDao.findPerExperimentSumScoresByUsernames(usernames)
                .stream()
                .filter(row -> hasText(row.getUsername()) && row.getExperimentId() != null)
                .collect(Collectors.groupingBy(
                        TeacherExperimentScoreRow::getUsername,
                        HashMap::new,
                        Collectors.toMap(
                                TeacherExperimentScoreRow::getExperimentId,
                                row -> row,
                                (left, right) -> left,
                                HashMap::new
                        )
                ));
    }

    private Map<String, TeacherExperimentPlagiarismRow> buildPlagiarismByKey(
            List<String> studentIds,
            List<Integer> experimentIds
    ) {
        if (studentIds.isEmpty() || experimentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return teacherExperimentQueryDao.findPlagiarismRates(studentIds, experimentIds)
                .stream()
                .filter(row -> hasText(row.getStudentId()) && row.getExperimentId() != null)
                .collect(Collectors.toMap(
                        row -> buildKey(row.getStudentId(), row.getExperimentId()),
                        row -> row,
                        (left, right) -> left
                ));
    }

    private Map<Integer, TeacherExperimentScoreRow> pickPreferredScores(
            Map<String, Map<Integer, TeacherExperimentScoreRow>> scoresByUsername,
            String studentIdLookup,
            String usernameLookup
    ) {
        Map<Integer, TeacherExperimentScoreRow> byStudentId = scoresByUsername.get(studentIdLookup);
        if (byStudentId != null && !byStudentId.isEmpty()) {
            return byStudentId;
        }
        if (hasText(usernameLookup)) {
            Map<Integer, TeacherExperimentScoreRow> byUsername = scoresByUsername.get(usernameLookup);
            if (byUsername != null) {
                return byUsername;
            }
        }
        return Collections.emptyMap();
    }

    private String buildKey(String studentId, Integer experimentId) {
        return studentId + "#" + experimentId;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private double calculateAveragePlagiarismRate(String plagiarismRates) {
        if (!hasText(plagiarismRates)) {
            return 0.0;
        }

        String[] rates = plagiarismRates.split(",");
        double sum = 0.0;
        int count = 0;
        for (String rate : rates) {
            if (Objects.equals("-", rate.trim())) {
                continue;
            }
            try {
                sum += Double.parseDouble(rate.replace("%", "").trim());
                count++;
            } catch (NumberFormatException ignored) {
                // Ignore malformed fragments and keep valid percentages.
            }
        }
        return count > 0 ? sum / count : 0.0;
    }
}
