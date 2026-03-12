package com.cqust.ai_server.dao;

import com.cqust.ai_server.entity.LeetCodeProblemTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * LeetCode题目标签数据访问接口
 */
@Mapper
public interface LeetCodeProblemTagDao {

    /**
     * 根据题目ID查询标签
     */
    List<LeetCodeProblemTag> findByProblemId(@Param("problemId") Long problemId);

    /**
     * 根据标签名查询题目标签
     */
    List<LeetCodeProblemTag> findByTagName(@Param("tagName") String tagName);

    /**
     * 插入标签（支持重复键更新）
     */
    int insertOrUpdate(LeetCodeProblemTag tag);

    /**
     * 批量插入标签
     */
    int batchInsert(@Param("tags") List<LeetCodeProblemTag> tags);

    /**
     * 删除题目的所有标签
     */
    int deleteByProblemId(@Param("problemId") Long problemId);

    /**
     * 删除特定标签
     */
    int deleteByProblemIdAndTag(@Param("problemId") Long problemId, @Param("tagName") String tagName);

    /**
     * 获取所有标签名称
     */
    List<String> getAllTagNames();

    /**
     * 统计标签使用次数
     */
    List<Object> getTagUsageStats();
}