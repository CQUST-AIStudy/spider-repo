package com.cqust.ai_server.service.impl;

import com.cqust.ai_server.dao.LeetCodeProblemDao;
import com.cqust.ai_server.dao.LeetCodeProblemTagDao;
import com.cqust.ai_server.dao.LeetCodeRecommendDao;
import com.cqust.ai_server.dao.LeetCodeFeedbackDao;
import com.cqust.ai_server.dao.StudentSkillStateDao;
import com.cqust.ai_server.entity.*;
import com.cqust.ai_server.service.LeetCodeRecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LeetCode推荐服务实现
 */
@Service
public class LeetCodeRecommendationServiceImpl implements LeetCodeRecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(LeetCodeRecommendationServiceImpl.class);

    @Autowired
    private LeetCodeProblemDao problemDao;

    @Autowired
    private LeetCodeProblemTagDao problemTagDao;

    @Autowired
    private StudentSkillStateDao skillStateDao;

    @Autowired
    private LeetCodeRecommendDao recommendDao;

    @Autowired
    private LeetCodeFeedbackDao feedbackDao;

    // 推荐算法权重配置
    private static final double WEIGHT_NEED_MATCH = 0.45;
    private static final double WEIGHT_DIFFICULTY_FIT = 0.20;
    private static final double WEIGHT_SUCCESS_PROB = 0.15;
    private static final double WEIGHT_NOVELTY = 0.10;
    private static final double WEIGHT_QUALITY = 0.10;
    private static final double PENALTY_REPEAT = 0.15;

    @Override
    @Transactional
    public String generateRecommendation(Integer studentId, Integer limit, String scene) {
        try {
            // 生成请求ID
            String requestId = UUID.randomUUID().toString();
            
            // 创建推荐请求记录
            LeetCodeRecommendRequest request = new LeetCodeRecommendRequest(requestId, studentId, scene, limit);
            recommendDao.insertRequest(request);

            // 异步生成推荐结果（这里简化为同步处理）
            List<LeetCodeRecommendItem> items = generateRecommendationSync(studentId, limit);
            
            // 保存推荐结果
            for (int i = 0; i < items.size(); i++) {
                LeetCodeRecommendItem item = items.get(i);
                item.setRequestId(requestId);
                item.setStudentId(studentId);
                item.setRankNo(i + 1);
                recommendDao.insertItem(item);
            }

            // 更新请求状态
            recommendDao.updateRequestStatus(requestId, LeetCodeRecommendRequest.STATUS_COMPLETED, null);
            
            logger.info("为学生 {} 生成推荐完成，requestId: {}, 推荐数量: {}", studentId, requestId, items.size());
            return requestId;
            
        } catch (Exception e) {
            logger.error("生成推荐失败，学生ID: " + studentId, e);
            throw new RuntimeException("生成推荐失败: " + e.getMessage(), e);
        }
    }

    @Override
    public LeetCodeRecommendRequest getRecommendationResult(String requestId) {
        return recommendDao.findRequestById(requestId);
    }

    @Override
    public List<LeetCodeRecommendItem> getRecommendationItems(String requestId) {
        return recommendDao.findItemsByRequestId(requestId);
    }
    @Override
    public List<LeetCodeRecommendItem> generateRecommendationSync(Integer studentId, Integer limit) {
        try {
            logger.info("开始为学生 {} 生成同步推荐，数量限制: {}", studentId, limit);

            // 1. 获取学生技能画像
            List<StudentSkillState> weakSkills = skillStateDao.findWeakSkills(studentId, 10);
            
            // 2. 召回候选题目
            List<LeetCodeProblem> candidateProblems = recallCandidateProblems(studentId, weakSkills, limit * 3);
            
            if (candidateProblems.isEmpty()) {
                logger.warn("学生 {} 没有找到候选题目，返回默认推荐", studentId);
                return getDefaultRecommendations(limit);
            }

            // 3. 计算推荐分数并排序
            List<LeetCodeRecommendItem> scoredItems = scoreAndRankProblems(studentId, candidateProblems, weakSkills);
            
            // 4. 多样性重排
            List<LeetCodeRecommendItem> diversifiedItems = diversifyRecommendations(scoredItems, limit);
            
            logger.info("为学生 {} 生成推荐完成，实际推荐数量: {}", studentId, diversifiedItems.size());
            return diversifiedItems;
            
        } catch (Exception e) {
            logger.error("生成同步推荐失败，学生ID: " + studentId, e);
            return getDefaultRecommendations(limit);
        }
    }

    @Override
    public boolean recordFeedback(String requestId, Integer studentId, Long problemId, String action, String sessionId) {
        try {
            LeetCodeRecommendFeedback feedback = new LeetCodeRecommendFeedback(requestId, studentId, problemId, action);
            feedback.setSessionId(sessionId);
            
            int result = feedbackDao.insertFeedback(feedback);
            
            logger.info("记录推荐反馈: requestId={}, studentId={}, problemId={}, action={}, result={}", 
                       requestId, studentId, problemId, action, result > 0 ? "成功" : "失败");
            
            return result > 0;
        } catch (Exception e) {
            logger.error("记录推荐反馈失败", e);
            return false;
        }
    }

    // 召回候选题目
    private List<LeetCodeProblem> recallCandidateProblems(Integer studentId, List<StudentSkillState> weakSkills, int candidateLimit) {
        Set<LeetCodeProblem> candidates = new HashSet<>();
        
        // 弱项标签召回（60%）
        int weakSkillLimit = (int) (candidateLimit * 0.6);
        if (!weakSkills.isEmpty()) {
            for (StudentSkillState skill : weakSkills.subList(0, Math.min(3, weakSkills.size()))) {
                List<LeetCodeProblem> tagProblems = problemDao.findByTag(skill.getTagName());
                candidates.addAll(tagProblems.subList(0, Math.min(weakSkillLimit / 3, tagProblems.size())));
            }
        }
        
        // 难度进阶召回（25%）
        int difficultyLimit = (int) (candidateLimit * 0.25);
        List<LeetCodeProblem> easyProblems = problemDao.findByDifficulty("Easy");
        List<LeetCodeProblem> mediumProblems = problemDao.findByDifficulty("Medium");
        candidates.addAll(easyProblems.subList(0, Math.min(difficultyLimit / 2, easyProblems.size())));
        candidates.addAll(mediumProblems.subList(0, Math.min(difficultyLimit / 2, mediumProblems.size())));
        
        // 探索召回（15%）
        int exploreLimit = (int) (candidateLimit * 0.15);
        List<LeetCodeProblem> allProblems = problemDao.findAll();
        Collections.shuffle(allProblems);
        candidates.addAll(allProblems.subList(0, Math.min(exploreLimit, allProblems.size())));
        
        // 过滤最近推荐过的题目
        List<Long> recentRecommended = recommendDao.findRecentRecommendedProblemIds(studentId, 7);
        Set<Long> recentSet = new HashSet<>(recentRecommended);
        
        return candidates.stream()
                .filter(p -> !recentSet.contains(p.getId()))
                .collect(Collectors.toList());
    }

    // 计算推荐分数并排序
    private List<LeetCodeRecommendItem> scoreAndRankProblems(Integer studentId, List<LeetCodeProblem> candidates, List<StudentSkillState> weakSkills) {
        List<LeetCodeRecommendItem> items = new ArrayList<>();
        
        // 构建技能需求映射
        Map<String, Double> skillNeedMap = new HashMap<>();
        for (StudentSkillState skill : weakSkills) {
            double need = 0.60 * (1 - skill.getMasteryNorm()) + 
                         0.25 * skill.getForgettingNorm() + 
                         0.15 * 1.0; // 课程权重默认1.0
            skillNeedMap.put(skill.getTagName(), need);
        }
        
        for (LeetCodeProblem problem : candidates) {
            LeetCodeRecommendItem item = new LeetCodeRecommendItem();
            item.setProblemId(problem.getId());
            item.setProblem(problem);
            
            // 计算各项分数
            double needMatch = calculateNeedMatch(problem, skillNeedMap);
            double difficultyFit = calculateDifficultyFit(problem, studentId);
            double successProb = calculateSuccessProb(problem, studentId);
            double novelty = calculateNovelty(problem, studentId);
            double quality = problem.getQualityScore().doubleValue();
            double repeatPenalty = calculateRepeatPenalty(problem, studentId);
            
            // 计算总分
            double totalScore = WEIGHT_NEED_MATCH * needMatch +
                               WEIGHT_DIFFICULTY_FIT * difficultyFit +
                               WEIGHT_SUCCESS_PROB * successProb +
                               WEIGHT_NOVELTY * novelty +
                               WEIGHT_QUALITY * quality -
                               PENALTY_REPEAT * repeatPenalty;
            
            // 设置分数
            item.setScoreTotal(BigDecimal.valueOf(totalScore).setScale(4, RoundingMode.HALF_UP));
            item.setScoreNeedMatch(BigDecimal.valueOf(needMatch).setScale(4, RoundingMode.HALF_UP));
            item.setScoreDifficultyFit(BigDecimal.valueOf(difficultyFit).setScale(4, RoundingMode.HALF_UP));
            item.setScoreSuccessProb(BigDecimal.valueOf(successProb).setScale(4, RoundingMode.HALF_UP));
            item.setScoreNovelty(BigDecimal.valueOf(novelty).setScale(4, RoundingMode.HALF_UP));
            item.setScoreQuality(BigDecimal.valueOf(quality).setScale(4, RoundingMode.HALF_UP));
            
            // 生成推荐理由
            item.setReasonText(generateReasonText(problem, needMatch, difficultyFit));
            
            items.add(item);
        }
        
        // 按总分排序
        items.sort((a, b) -> b.getScoreTotal().compareTo(a.getScoreTotal()));
        
        return items;
    }

    // 多样性重排
    private List<LeetCodeRecommendItem> diversifyRecommendations(List<LeetCodeRecommendItem> items, int limit) {
        List<LeetCodeRecommendItem> result = new ArrayList<>();
        Set<String> usedDifficulties = new HashSet<>();
        
        // 确保难度多样性
        int easyCount = 0, mediumCount = 0, hardCount = 0;
        int maxEasy = Math.max(2, limit / 5);
        int maxMedium = Math.max(4, limit * 2 / 3);
        int maxHard = Math.max(1, limit / 5);
        
        for (LeetCodeRecommendItem item : items) {
            if (result.size() >= limit) break;
            
            String difficulty = item.getProblem().getDifficulty();
            
            boolean canAdd = false;
            if ("Easy".equals(difficulty) && easyCount < maxEasy) {
                easyCount++;
                canAdd = true;
            } else if ("Medium".equals(difficulty) && mediumCount < maxMedium) {
                mediumCount++;
                canAdd = true;
            } else if ("Hard".equals(difficulty) && hardCount < maxHard) {
                hardCount++;
                canAdd = true;
            } else if (result.size() < limit - 2) {
                // 如果还有空位，放宽限制
                canAdd = true;
            }
            
            if (canAdd) {
                result.add(item);
            }
        }
        
        // 如果数量不够，补充剩余的
        for (LeetCodeRecommendItem item : items) {
            if (result.size() >= limit) break;
            if (!result.contains(item)) {
                result.add(item);
            }
        }
        
        return result.subList(0, Math.min(limit, result.size()));
    }

    // 辅助计算方法
    private double calculateNeedMatch(LeetCodeProblem problem, Map<String, Double> skillNeedMap) {
        try {
            // 获取题目的标签
            List<LeetCodeProblemTag> tags = problemTagDao.findByProblemId(problem.getId());
            if (tags.isEmpty()) {
                return 0.5; // 默认值
            }
            
            double totalNeed = 0.0;
            double totalRelevance = 0.0;
            
            for (LeetCodeProblemTag tag : tags) {
                double need = skillNeedMap.getOrDefault(tag.getTagName(), 0.5);
                double relevance = tag.getRelevanceScore().doubleValue();
                
                totalNeed += need * relevance;
                totalRelevance += relevance;
            }
            
            return totalRelevance > 0 ? totalNeed / totalRelevance : 0.5;
        } catch (Exception e) {
            logger.warn("计算需求匹配度失败: {}", e.getMessage());
            return 0.5;
        }
    }

    private double calculateDifficultyFit(LeetCodeProblem problem, Integer studentId) {
        // 简化处理：根据难度返回适配度
        String difficulty = problem.getDifficulty();
        switch (difficulty) {
            case "Easy": return 0.8;
            case "Medium": return 0.6;
            case "Hard": return 0.4;
            default: return 0.5;
        }
    }

    private double calculateSuccessProb(LeetCodeProblem problem, Integer studentId) {
        // 简化处理：根据难度估算成功概率
        String difficulty = problem.getDifficulty();
        switch (difficulty) {
            case "Easy": return 0.75;
            case "Medium": return 0.50;
            case "Hard": return 0.25;
            default: return 0.60;
        }
    }

    private double calculateNovelty(LeetCodeProblem problem, Integer studentId) {
        // 简化处理：新题目新颖度高
        return 0.8;
    }

    private double calculateRepeatPenalty(LeetCodeProblem problem, Integer studentId) {
        // 简化处理：暂不实现重复惩罚
        return 0.0;
    }

    private String generateReasonText(LeetCodeProblem problem, double needMatch, double difficultyFit) {
        try {
            List<LeetCodeProblemTag> tags = problemTagDao.findByProblemId(problem.getId());
            String mainTag = tags.isEmpty() ? "算法" : tags.get(0).getTagName();
            
            return String.format("推荐理由：该题目难度为%s，主要考查%s技能，适合当前水平练习，匹配度%.1f%%", 
                               problem.getDifficulty(), mainTag, needMatch * 100);
        } catch (Exception e) {
            return String.format("推荐理由：该题目难度为%s，适合当前水平练习", problem.getDifficulty());
        }
    }

    private List<LeetCodeRecommendItem> getDefaultRecommendations(int limit) {
        // 返回默认推荐（高质量的简单题目）
        List<LeetCodeProblem> defaultProblems = problemDao.findByDifficulty("Easy");
        return defaultProblems.stream()
                .limit(limit)
                .map(p -> {
                    LeetCodeRecommendItem item = new LeetCodeRecommendItem();
                    item.setProblemId(p.getId());
                    item.setProblem(p);
                    item.setScoreTotal(BigDecimal.valueOf(0.6));
                    item.setReasonText("默认推荐：适合入门练习的基础题目");
                    return item;
                })
                .collect(Collectors.toList());
    }
}