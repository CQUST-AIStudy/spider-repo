package com.tap.backend.api.analytics;

import com.tap.common.api.ApiResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 实验分析 API — 从 problem_score_detail / score 表计算统计指标
 * 提供: 总览统计、每题正答率、分数分布、分段分析
 */
@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class ExperimentAnalyticsController {

    @PersistenceContext
    private EntityManager em;

    /**
     * 获取所有实验的概览列表（用于选择器）
     */
    @GetMapping("/experiments")
    public ApiResponse<List<Map<String, Object>>> listExperiments() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "SELECT experiment_id, name, topic_sum FROM experiment ORDER BY experiment_id"
        ).getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("experimentId", r[0]);
            m.put("name", r[1]);
            m.put("topicSum", r[2]);
            result.add(m);
        }
        return ApiResponse.of(result);
    }

    /**
     * 单个实验的完整分析数据
     * 包含: overview(总人数/最高分/最低分/平均分/中位线/高位平均/低位平均/难度系数/区分度)
     *       scoreDistribution(分段人数)
     *       problemAccuracy(每题正答率)
     */
    @GetMapping("/experiments/{experimentId}")
    public ApiResponse<Map<String, Object>> getExperimentAnalytics(@PathVariable int experimentId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 总览统计 — 从 score 表
        result.put("overview", computeOverview(experimentId));

        // 2. 分数分布 — 5 段
        result.put("scoreDistribution", computeScoreDistribution(experimentId));

        // 3. 每题正答率 — 从 problem_score_detail
        result.put("problemAccuracy", computeProblemAccuracy(experimentId));

        // 4. 实验名称
        @SuppressWarnings("unchecked")
        List<Object[]> nameRows = em.createNativeQuery(
            "SELECT name FROM experiment WHERE experiment_id = ?1"
        ).setParameter(1, experimentId).getResultList();
        result.put("experimentName", nameRows.isEmpty() ? "" : nameRows.get(0)[0]);

        return ApiResponse.of(result);
    }

    /**
     * 所有实验的横向对比（每个实验的平均分、难度系数、区分度）
     */
    @GetMapping("/comparison")
    public ApiResponse<List<Map<String, Object>>> getComparison() {
        @SuppressWarnings("unchecked")
        List<Object[]> expRows = em.createNativeQuery(
            "SELECT experiment_id, name FROM experiment ORDER BY experiment_id"
        ).getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] er : expRows) {
            int eid = ((Number) er[0]).intValue();
            Map<String, Object> overview = computeOverview(eid);
            if (((Number) overview.getOrDefault("totalStudents", 0)).intValue() == 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("experimentId", eid);
            m.put("name", er[1]);
            m.put("avgScore", overview.get("avgScore"));
            m.put("difficulty", overview.get("difficulty"));
            m.put("discrimination", overview.get("discrimination"));
            m.put("totalStudents", overview.get("totalStudents"));
            result.add(m);
        }
        return ApiResponse.of(result);
    }

    // ==================== 计算逻辑 ====================

    private Map<String, Object> computeOverview(int experimentId) {
        // 从 score 表获取该实验所有学生的总分
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "SELECT score FROM score WHERE experiment_id = ?1 AND score IS NOT NULL ORDER BY score DESC"
        ).setParameter(1, experimentId).getResultList();

        Map<String, Object> ov = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            ov.put("totalStudents", 0);
            return ov;
        }

        List<Double> scores = new ArrayList<>();
        for (Object[] r : rows) {
            if (r[0] != null) scores.add(((Number) r[0]).doubleValue());
        }
        if (scores.isEmpty()) {
            ov.put("totalStudents", 0);
            return ov;
        }

        scores.sort(Collections.reverseOrder());
        int n = scores.size();
        double max = scores.get(0);
        double min = scores.get(n - 1);
        double sum = scores.stream().mapToDouble(Double::doubleValue).sum();
        double avg = sum / n;

        // 中位数
        double median;
        if (n % 2 == 0) {
            median = (scores.get(n / 2 - 1) + scores.get(n / 2)) / 2.0;
        } else {
            median = scores.get(n / 2);
        }

        // 高位平均（前27%）和低位平均（后27%）
        int topCount = Math.max(1, (int) Math.round(n * 0.27));
        int bottomCount = Math.max(1, (int) Math.round(n * 0.27));
        double topAvg = scores.subList(0, topCount).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double bottomAvg = scores.subList(n - bottomCount, n).stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // 获取满分（从 problem_score_detail 的 max_score 总和，或用 score 表最高分近似）
        @SuppressWarnings("unchecked")
        List<Object[]> maxScoreRows = em.createNativeQuery(
            "SELECT SUM(DISTINCT max_score) FROM problem_score_detail WHERE experiment_id = ?1 " +
            "AND problem_label IN (SELECT DISTINCT problem_label FROM problem_score_detail WHERE experiment_id = ?1 LIMIT 1)"
        ).setParameter(1, experimentId).getResultList();

        // 更准确: 每题满分之和
        @SuppressWarnings("unchecked")
        List<Object[]> fullScoreRows = em.createNativeQuery(
            "SELECT SUM(ms) FROM (SELECT problem_label, MAX(max_score) as ms " +
            "FROM problem_score_detail WHERE experiment_id = ?1 GROUP BY problem_label) t"
        ).setParameter(1, experimentId).getResultList();

        double fullScore = max; // fallback
        if (!fullScoreRows.isEmpty() && fullScoreRows.get(0)[0] != null) {
            fullScore = ((Number) fullScoreRows.get(0)[0]).doubleValue();
        }
        if (fullScore <= 0) fullScore = max > 0 ? max : 100;

        // 难度系数 = 1 - (平均分 / 满分)  越小越难
        double difficulty = fullScore > 0 ? round2(1.0 - avg / fullScore) : 0;

        // 区分度 = (高位平均 - 低位平均) / 满分
        double discrimination = fullScore > 0 ? round2((topAvg - bottomAvg) / fullScore) : 0;

        ov.put("totalStudents", n);
        ov.put("maxScore", round2(max));
        ov.put("minScore", round2(min));
        ov.put("avgScore", round2(avg));
        ov.put("median", round2(median));
        ov.put("topAvg", round2(topAvg));
        ov.put("bottomAvg", round2(bottomAvg));
        ov.put("fullScore", round2(fullScore));
        ov.put("difficulty", difficulty);
        ov.put("discrimination", discrimination);

        return ov;
    }

    private Map<String, Object> computeScoreDistribution(int experimentId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "SELECT " +
            "  SUM(CASE WHEN score >= 90 THEN 1 ELSE 0 END) as s90, " +
            "  SUM(CASE WHEN score >= 80 AND score < 90 THEN 1 ELSE 0 END) as s80, " +
            "  SUM(CASE WHEN score >= 70 AND score < 80 THEN 1 ELSE 0 END) as s70, " +
            "  SUM(CASE WHEN score >= 60 AND score < 70 THEN 1 ELSE 0 END) as s60, " +
            "  SUM(CASE WHEN score < 60 THEN 1 ELSE 0 END) as s0 " +
            "FROM score WHERE experiment_id = ?1 AND score IS NOT NULL"
        ).setParameter(1, experimentId).getResultList();

        Map<String, Object> dist = new LinkedHashMap<>();
        if (!rows.isEmpty() && rows.get(0) != null) {
            Object[] r = rows.get(0);
            dist.put("90-100", toInt(r[0]));
            dist.put("80-89", toInt(r[1]));
            dist.put("70-79", toInt(r[2]));
            dist.put("60-69", toInt(r[3]));
            dist.put("<60", toInt(r[4]));
        }
        return dist;
    }

    private List<Map<String, Object>> computeProblemAccuracy(int experimentId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "SELECT problem_label, problem_type, MAX(max_score) as full_score, " +
            "  AVG(actual_score) as avg_score, " +
            "  COUNT(*) as student_count, " +
            "  SUM(CASE WHEN actual_score >= max_score THEN 1 ELSE 0 END) as full_mark_count, " +
            "  SUM(CASE WHEN actual_score = 0 THEN 1 ELSE 0 END) as zero_count " +
            "FROM problem_score_detail WHERE experiment_id = ?1 " +
            "GROUP BY problem_label, problem_type ORDER BY problem_label"
        ).setParameter(1, experimentId).getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            double fullScore = ((Number) r[2]).doubleValue();
            double avgScore = ((Number) r[3]).doubleValue();
            int studentCount = ((Number) r[4]).intValue();
            int fullMarkCount = ((Number) r[5]).intValue();
            int zeroCount = ((Number) r[6]).intValue();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", r[0]);
            m.put("type", r[1]);
            m.put("fullScore", round2(fullScore));
            m.put("avgScore", round2(avgScore));
            m.put("accuracyRate", fullScore > 0 ? round2(avgScore / fullScore * 100) : 0);
            m.put("studentCount", studentCount);
            m.put("fullMarkCount", fullMarkCount);
            m.put("zeroCount", zeroCount);
            result.add(m);
        }
        return result;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static int toInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }
}
