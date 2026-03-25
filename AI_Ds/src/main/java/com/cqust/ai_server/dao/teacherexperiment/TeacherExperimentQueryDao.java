package com.cqust.ai_server.dao.teacherexperiment;

import com.cqust.ai_server.teacherexperiment.TeacherExperimentPlagiarismRow;
import com.cqust.ai_server.teacherexperiment.TeacherExperimentScoreAggregate;
import com.cqust.ai_server.teacherexperiment.TeacherExperimentScoreRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TeacherExperimentQueryDao {

    @Select({
            "<script>",
            "SELECT",
            "  experiment_id AS experimentId,",
            "  COUNT(DISTINCT CASE",
            "    WHEN LOWER(COALESCE(status, '')) = 'completed' OR (score IS NOT NULL AND score &gt; 0)",
            "    THEN username",
            "  END) AS submissionCount,",
            "  COALESCE(SUM(CASE WHEN score IS NOT NULL AND score &gt; 0 THEN score ELSE 0 END), 0) AS totalPositiveScore",
            "FROM score",
            "WHERE experiment_id IN",
            "<foreach item='experimentId' collection='experimentIds' open='(' separator=',' close=')'>",
            "  #{experimentId}",
            "</foreach>",
            "GROUP BY experiment_id",
            "</script>"
    })
    List<TeacherExperimentScoreAggregate> summarizeByExperimentIds(@Param("experimentIds") List<Integer> experimentIds);

    @Select({
            "<script>",
            "SELECT",
            "  username AS username,",
            "  experiment_id AS experimentId,",
            "  SUM(score) AS score,",
            "  MAX(submit_time) AS submitTime,",
            "  MAX(status) AS status",
            "FROM score",
            "WHERE username IN",
            "<foreach item='username' collection='usernames' open='(' separator=',' close=')'>",
            "  #{username}",
            "</foreach>",
            "GROUP BY username, experiment_id",
            "</script>"
    })
    List<TeacherExperimentScoreRow> findPerExperimentSumScoresByUsernames(@Param("usernames") List<String> usernames);

    @Select({
            "<script>",
            "SELECT",
            "  student_id AS studentId,",
            "  experiment_id AS experimentId,",
            "  Plagiarism_Rate AS plagiarismRate",
            "FROM Plagiarism_Check_Table",
            "WHERE student_id IN",
            "<foreach item='studentId' collection='studentIds' open='(' separator=',' close=')'>",
            "  #{studentId}",
            "</foreach>",
            "AND experiment_id IN",
            "<foreach item='experimentId' collection='experimentIds' open='(' separator=',' close=')'>",
            "  #{experimentId}",
            "</foreach>",
            "</script>"
    })
    List<TeacherExperimentPlagiarismRow> findPlagiarismRates(
            @Param("studentIds") List<String> studentIds,
            @Param("experimentIds") List<Integer> experimentIds
    );
}
