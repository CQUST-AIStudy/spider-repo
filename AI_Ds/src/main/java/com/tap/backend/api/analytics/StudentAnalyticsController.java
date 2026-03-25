package com.tap.backend.api.analytics;

import com.cqust.ai_server.security.StudentSessionResolver;
import com.tap.common.api.ApiResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics/student")
public class StudentAnalyticsController {

    private final StudentSessionResolver studentSessionResolver;

    @PersistenceContext
    private EntityManager em;

    public StudentAnalyticsController(StudentSessionResolver studentSessionResolver) {
        this.studentSessionResolver = studentSessionResolver;
    }

    @GetMapping("/{studentId}/overview")
    public ApiResponse<Map<String, Object>> getStudentOverview(
            @PathVariable String studentId,
            HttpServletRequest request
    ) {
        String authorizedStudentId = studentSessionResolver.requireAuthorizedStudentId(studentId, request);

        @SuppressWarnings("unchecked")
        List<Object[]> experimentRows = em.createNativeQuery(
                "SELECT DISTINCT s.experiment_id, e.name " +
                        "FROM score s JOIN experiment e ON s.experiment_id = e.experiment_id " +
                        "WHERE s.username = ?1 ORDER BY s.experiment_id"
        ).setParameter(1, authorizedStudentId).getResultList();

        List<Map<String, Object>> experiments = new ArrayList<>();
        double myTotalScore = 0;
        double classTotalAvg = 0;
        int experimentCount = 0;

        for (Object[] experimentRow : experimentRows) {
            int experimentId = ((Number) experimentRow[0]).intValue();
            String experimentName = (String) experimentRow[1];

            @SuppressWarnings("unchecked")
            List<Object[]> myRows = em.createNativeQuery(
                    "SELECT score FROM score WHERE experiment_id = ?1 AND username = ?2 AND score IS NOT NULL"
            ).setParameter(1, experimentId).setParameter(2, authorizedStudentId).getResultList();
            if (myRows.isEmpty() || myRows.get(0)[0] == null) {
                continue;
            }
            double myScore = ((Number) myRows.get(0)[0]).doubleValue();

            @SuppressWarnings("unchecked")
            List<Object[]> classRows = em.createNativeQuery(
                    "SELECT AVG(score), COUNT(*), " +
                            "SUM(CASE WHEN score > ?2 THEN 1 ELSE 0 END), " +
                            "SUM(CASE WHEN score = ?2 THEN 1 ELSE 0 END) " +
                            "FROM score WHERE experiment_id = ?1 AND score IS NOT NULL"
            ).setParameter(1, experimentId).setParameter(2, myScore).getResultList();

            double classAvg = 0;
            int totalStudents = 0;
            double percentile = 50;
            if (!classRows.isEmpty() && classRows.get(0)[0] != null) {
                classAvg = ((Number) classRows.get(0)[0]).doubleValue();
                totalStudents = ((Number) classRows.get(0)[1]).intValue();
                int above = ((Number) classRows.get(0)[2]).intValue();
                int equal = ((Number) classRows.get(0)[3]).intValue();
                percentile = totalStudents > 0
                        ? round2((1.0 - (above + equal * 0.5) / totalStudents) * 100)
                        : 50;
            }

            @SuppressWarnings("unchecked")
            List<Object[]> medianRows = em.createNativeQuery(
                    "SELECT score FROM score WHERE experiment_id = ?1 AND score IS NOT NULL ORDER BY score"
            ).setParameter(1, experimentId).getResultList();
            double median = 0;
            if (!medianRows.isEmpty()) {
                int size = medianRows.size();
                if (size % 2 == 0) {
                    median = (((Number) medianRows.get(size / 2 - 1)[0]).doubleValue()
                            + ((Number) medianRows.get(size / 2)[0]).doubleValue()) / 2.0;
                } else {
                    median = ((Number) medianRows.get(size / 2)[0]).doubleValue();
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("experimentId", experimentId);
            item.put("name", experimentName);
            item.put("myScore", round2(myScore));
            item.put("classAvg", round2(classAvg));
            item.put("classMedian", round2(median));
            item.put("percentile", percentile);
            item.put("totalStudents", totalStudents);
            item.put("diff", round2(myScore - classAvg));
            experiments.add(item);

            myTotalScore += myScore;
            classTotalAvg += classAvg;
            experimentCount++;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("avgMyScore", experimentCount > 0 ? round2(myTotalScore / experimentCount) : 0);
        summary.put("avgClassScore", experimentCount > 0 ? round2(classTotalAvg / experimentCount) : 0);
        summary.put("experimentCount", experimentCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("experiments", experiments);
        return ApiResponse.of(result);
    }

    @GetMapping("/{studentId}/experiments/{experimentId}")
    public ApiResponse<Map<String, Object>> getStudentExperimentDetail(
            @PathVariable String studentId,
            @PathVariable int experimentId,
            HttpServletRequest request
    ) {
        String authorizedStudentId = studentSessionResolver.requireAuthorizedStudentId(studentId, request);

        @SuppressWarnings("unchecked")
        List<Object[]> myRows = em.createNativeQuery(
                "SELECT problem_label, problem_type, max_score, actual_score " +
                        "FROM problem_score_detail WHERE experiment_id = ?1 AND student_id = ?2 " +
                        "ORDER BY problem_label"
        ).setParameter(1, experimentId).setParameter(2, authorizedStudentId).getResultList();

        @SuppressWarnings("unchecked")
        List<Object[]> classRows = em.createNativeQuery(
                "SELECT problem_label, AVG(actual_score) as avg_score, MAX(max_score) as full_score " +
                        "FROM problem_score_detail WHERE experiment_id = ?1 " +
                        "GROUP BY problem_label ORDER BY problem_label"
        ).setParameter(1, experimentId).getResultList();

        Map<String, double[]> classMap = new LinkedHashMap<>();
        for (Object[] row : classRows) {
            classMap.put((String) row[0], new double[]{
                    ((Number) row[1]).doubleValue(),
                    ((Number) row[2]).doubleValue()
            });
        }

        List<Map<String, Object>> problems = new ArrayList<>();
        for (Object[] row : myRows) {
            String label = (String) row[0];
            double maxScore = ((Number) row[2]).doubleValue();
            double myScore = ((Number) row[3]).doubleValue();
            double[] classStats = classMap.getOrDefault(label, new double[]{0, maxScore});

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", label);
            item.put("type", row[1]);
            item.put("fullScore", round2(maxScore));
            item.put("myScore", round2(myScore));
            item.put("classAvg", round2(classStats[0]));
            item.put("diff", round2(myScore - classStats[0]));
            problems.add(item);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> totalRows = em.createNativeQuery(
                "SELECT score FROM score WHERE experiment_id = ?1 AND username = ?2"
        ).setParameter(1, experimentId).setParameter(2, authorizedStudentId).getResultList();
        double myTotal = (!totalRows.isEmpty() && totalRows.get(0)[0] != null)
                ? ((Number) totalRows.get(0)[0]).doubleValue()
                : 0;

        @SuppressWarnings("unchecked")
        List<Object[]> statsRows = em.createNativeQuery(
                "SELECT AVG(score), MAX(score), MIN(score), COUNT(*) " +
                        "FROM score WHERE experiment_id = ?1 AND score IS NOT NULL"
        ).setParameter(1, experimentId).getResultList();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("myScore", round2(myTotal));
        if (!statsRows.isEmpty() && statsRows.get(0)[0] != null) {
            overview.put("classAvg", round2(((Number) statsRows.get(0)[0]).doubleValue()));
            overview.put("classMax", round2(((Number) statsRows.get(0)[1]).doubleValue()));
            overview.put("classMin", round2(((Number) statsRows.get(0)[2]).doubleValue()));
            overview.put("totalStudents", ((Number) statsRows.get(0)[3]).intValue());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", overview);
        result.put("problems", problems);
        return ApiResponse.of(result);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
