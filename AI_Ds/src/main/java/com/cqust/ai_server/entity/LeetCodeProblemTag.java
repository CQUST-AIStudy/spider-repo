package com.cqust.ai_server.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LeetCode题目标签实体类
 */
public class LeetCodeProblemTag {
    private Long id;
    private Long problemId;
    private String tagName;
    private String tagCategory;
    private BigDecimal relevanceScore;
    private Boolean isPrimary;
    private LocalDateTime createdAt;

    // 构造函数
    public LeetCodeProblemTag() {}

    public LeetCodeProblemTag(Long problemId, String tagName, String tagCategory) {
        this.problemId = problemId;
        this.tagName = tagName;
        this.tagCategory = tagCategory;
        this.relevanceScore = new BigDecimal("1.0000");
        this.isPrimary = false;
    }

    public LeetCodeProblemTag(Long problemId, String tagName, String tagCategory, boolean isPrimary) {
        this(problemId, tagName, tagCategory);
        this.isPrimary = isPrimary;
    }

    // Getter和Setter方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProblemId() { return problemId; }
    public void setProblemId(Long problemId) { this.problemId = problemId; }

    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }

    public String getTagCategory() { return tagCategory; }
    public void setTagCategory(String tagCategory) { this.tagCategory = tagCategory; }

    public BigDecimal getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(BigDecimal relevanceScore) { this.relevanceScore = relevanceScore; }

    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "LeetCodeProblemTag{" +
                "id=" + id +
                ", problemId=" + problemId +
                ", tagName='" + tagName + '\'' +
                ", tagCategory='" + tagCategory + '\'' +
                ", isPrimary=" + isPrimary +
                '}';
    }
}