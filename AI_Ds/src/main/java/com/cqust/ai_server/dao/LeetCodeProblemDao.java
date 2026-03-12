package com.cqust.ai_server.dao;

import com.cqust.ai_server.entity.LeetCodeProblem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * LeetCode题目数据访问接口
 */
@Mapper
public interface LeetCodeProblemDao {

    /**
     * 根据ID查询题目
     */
    LeetCodeProblem findById(@Param("id") Long id);

    /**
     * 根据sourceKey查询题目
     */
    LeetCodeProblem findBySourceKey(@Param("sourceKey") String sourceKey);

    /**
     * 查询所有题目
     */
    List<LeetCodeProblem> findAll();

    /**
     * 根据难度查询题目
     */
    List<LeetCodeProblem> findByDifficulty(@Param("difficulty") String difficulty);

    /**
     * 根据质量分数范围查询题目
     */
    List<LeetCodeProblem> findByQualityRange(@Param("minQuality") Double minQuality, @Param("maxQuality") Double maxQuality);

    /**
     * 插入题目（支持重复键更新）
     */
    int insertOrUpdate(LeetCodeProblem problem);

    /**
     * 批量插入题目
     */
    int batchInsert(@Param("problems") List<LeetCodeProblem> problems);

    /**
     * 更新题目
     */
    int update(LeetCodeProblem problem);

    /**
     * 删除题目
     */
    int deleteById(@Param("id") Long id);

    /**
     * 统计题目总数
     */
    int count();

    /**
     * 根据标签查询题目
     */
    List<LeetCodeProblem> findByTag(@Param("tagName") String tagName);

    /**
     * 根据多个标签查询题目（交集）
     */
    List<LeetCodeProblem> findByTags(@Param("tagNames") List<String> tagNames);
}