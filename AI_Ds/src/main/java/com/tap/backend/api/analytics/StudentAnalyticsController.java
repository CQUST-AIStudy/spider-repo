package com.tap.backend.api.analytics;

import com.tap.common.api.ApiResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 学生视角的班级对比分析 API
 * 返回聚合统计数据（均分、中位数、百分位），不暴露其他同学的具体成绩
 */
@RestController
@RequestMapping("/api/analytics/student")
@CrossOrigin(origins = "*")
public class StudentAnalyticsController {

    @PersistenceContext
    private EntityManager em;

    /**
     * 学生在所有实验中的班级对比概览
     * 返回每个实验: 我的总分、班级均分、班级中位数、我的排名百分位、班级人数
     */
    @GetMapping("/{studentId}/overview")
    public ApiResponse<Map<String, Object>> getStudentOverview(@PathVariable String studentId) {
        // 获取该学生参与的所有实验
        @SuppressWarnings("unchecked")
        List<Object[]> expRows = em.createNativeQuery(
            "SELECT DISTINCT s.experiment_id, e.name " +
            "FROM score s JOIN experiment e ON s.experiment_id = e.experiment_id " +
            "WHERE s.username = ?1 ORDER BY s.experiment_id"
        ).setParameter(1, studentId).getResultList();

        List<Map<String, Object>> experiments = new ArrayList<>();
        double myTotalScore = 0, classTotalAvg = 0;
        int expCount = 0;

        for (Object[] er : expRows) {
            int eid = ((Number) er[0]).intValue();
            String ename = (String) er[1];

            // 我的分数
            @SuppressWarnings("unchecked")
            List<Object[]> myRows = em.createNativeQuery(
                "SELECT score FROM score WHERE experiment_id = ?1 AND username = ?2 AND score IS NOT NULL"
            ).setParameter(1, eid).setParameter(2, studentId).getResultList();
            if (myRows.isEmpty() || myRows.get(0)[0] == null) continue;
            double myScore = ((Number) myRows.get(0)[0]).doubleValue();

            // 班级统计
            @SuppressWarnings("unchecked")
            List<Object[]> classRows = em.createNativeQuery(
                "SELECT AVG(score), COUNT(*), " +
                "  SUM(CASE WHEN score > ?2 THEN 1 ELSE 0 END), " +
                "  SUM(CASE WHEN score = ?2 THEN 1 ELSE 0 END) " +
                "FROM score WHERE experiment_id = ?1 AND score IS NOT NULL"
            ).setParameter(1, eid).setParameter(2, myScore).getResultList();

            double classAvg = 0; int total = 0; double percentile = 50;
            if (!classRows.isEmpty() && classRows.get(0)[0] != null) {
                classAvg = ((Number) classRows.get(0)[0]).doubleValue();
                total = ((Number) classRows.get(0)[1]).intValue();
                int above = ((Number) classRows.get(0)[2]).intValue();
                int equal = ((Number) classRows.get(0)[3]).intValue();
                // 百分位: 超过了多少比例的同学
                percentile = total > 0 ? round2((1.0 - (above + equal * 0.5) / total) * 100) : 50;
            }

            // 中位数
            @SuppressWarnings("unchecked")
            List<Object[]> medianRows = em.createNativeQuery(
                "SELECT score FROM score WHERE experiment_id = ?1 AND score IS NOT NULL ORDER BY score"
            ).setParameter(1, eid).getResultList();
            double median = 0;
            if (!medianRows.isEmpty()) {
                int n = medianRows.size();
                if (n % 2 == 0) {
                    median = (((Number) medianRows.get(n/2-1)[0]).doubleValue() +
                              ((Number) medianRows.get(n/2)[0]).doubleValue()) / 2.0;
                } else {
                    median = ((Number) medianRows.get(n/2)[0]).doubleValue();
                }
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("experimentId", eid);
            m.put("name", ename);
            m.put("myScore", round2(myScore));
            m.put("classAvg", round2(classAvg));
            m.put("classMedian", round2(median));
            m.put("percentile", percentile);
            m.put("totalStudents", total);
            m.put("diff", round2(myScore - classAvg));
            experiments.add(m);

            myTotalScore += myScore;
            classTotalAvg += classAvg;
            expCount++;
        }

        // 汇总
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("avgMyScore", expCount > 0 ? round2(myTotalScore / expCount) : 0);
        summary.put("avgClassScore", expCount > 0 ? round2(classTotalAvg / expCount) : 0);
        summary.put("experimentCount", expCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("experiments", experiments);
        return ApiResponse.of(result);
    }

    /**
     * 单个实验的每题对比: 我的得分 vs 班级均分
     */
    @GetMapping("/{studentId}/experiments/{experimentId}")
    public ApiResponse<Map<String, Object>> getStudentExperimentDetail(
            @PathVariable String studentId, @PathVariable int experimentId) {

        // 我的每题得分
        @SuppressWarnings("unchecked")
        List<Object[]> myRows = em.createNativeQuery(
            "SELECT problem_label, problem_type, max_score, actual_score " +
            "FROM problem_score_detail WHERE experiment_id = ?1 AND student_id = ?2 " +
            "ORDER BY problem_label"
        ).setParameter(1, experimentId).setParameter(2, studentId).getResultList();

        // 班级每题均分
        @SuppressWarnings("unchecked")
        List<Object[]> classRows = em.createNativeQuery(
            "SELECT problem_label, AVG(actual_score) as avg_score, MAX(max_score) as full_score " +
            "FROM problem_score_detail WHERE experiment_id = ?1 " +
            "GROUP BY problem_label ORDER BY problem_label"
        ).setParameter(1, experimentId).getResultList();

        Map<String, double[]> classMap = new LinkedHashMap<>();
        for (Object[] r : classRows) {
            classMap.put((String) r[0], new double[]{
                ((Number) r[1]).doubleValue(), ((Number) r[2]).doubleValue()
            });
        }

        List<Map<String, Object>> problems = new ArrayList<>();
        for (Object[] r : myRows) {
            String label = (String) r[0];
            double maxScore = ((Number) r[2]).doubleValue();
            double myScore = ((Number) r[3]).doubleValue();
            double[] cls = classMap.getOrDefault(label, new double[]{0, maxScore});

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", label);
            m.put("type", r[1]);
            m.put("fullScore", round2(maxScore));
            m.put("myScore", round2(myScore));
            m.put("classAvg", round2(cls[0]));
            m.put("diff", round2(myScore - cls[0]));
            problems.add(m);
        }

        // 我的总分 & 班级统计
        @SuppressWarnings("unchecked")
        List<Object[]> totalRows = em.createNativeQuery(
            "SELECT score FROM score WHERE experiment_id = ?1 AND username = ?2"
        ).setParameter(1, experimentId).setParameter(2, studentId).getResultList();
        double myTotal = (!totalRows.isEmpty() && totalRows.get(0)[0] != null)
            ? ((Number) totalRows.get(0)[0]).doubleValue() : 0;

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

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
