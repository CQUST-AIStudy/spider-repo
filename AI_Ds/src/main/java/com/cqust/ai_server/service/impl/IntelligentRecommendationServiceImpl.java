package com.cqust.ai_server.service.impl;

import com.cqust.ai_server.dao.LeetCodeProblemDao;
import com.cqust.ai_server.dao.LeetCodeProblemTagDao;
import com.cqust.ai_server.dao.LeetCodeFeedbackDao;
import com.cqust.ai_server.dao.StudentSkillStateDao;
import com.cqust.ai_server.entity.*;
import com.cqust.ai_server.service.LeetCodeRecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 智能推荐服务实现 - 基于学生能力画像和题目标签的精准匹配
 */
@Service("intelligentRecommendationService")
public class IntelligentRecommendationServiceImpl implements LeetCodeRecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(IntelligentRecommendationServiceImpl.class);

    @Autowired
    private LeetCodeProblemDao problemDao;
    
    @Autowired
    private LeetCodeProblemTagDao problemTagDao;
    
    @Autowired
    private StudentSkillStateDao skillStateDao;

    @Autowired
    private LeetCodeFeedbackDao feedbackDao;

    // 推荐策略权重配置
    private static final double WEAKNESS_WEIGHT = 0.35;      // 薄弱点匹配权重
    private static final double DIFFICULTY_WEIGHT = 0.25;    // 难度匹配权重
    private static final double NOVELTY_WEIGHT = 0.15;       // 新颖性权重
    private static final double DIVERSITY_WEIGHT = 0.15;     // 多样性权重
    private static final double QUALITY_WEIGHT = 0.10;       // 质量权重
    private static final double MIN_TOTAL_SCORE = 0.0;
    private static final double MAX_TOTAL_SCORE = 1.2;

    private static final Set<String> VALID_FEEDBACK_ACTIONS = Set.of(
        LeetCodeRecommendFeedback.ACTION_EXPOSURE,
        LeetCodeRecommendFeedback.ACTION_CLICK,
        LeetCodeRecommendFeedback.ACTION_START,
        LeetCodeRecommendFeedback.ACTION_COMPLETE,
        LeetCodeRecommendFeedback.ACTION_SKIP,
        LeetCodeRecommendFeedback.ACTION_DISLIKE
    );

    private final Map<String, LeetCodeRecommendRequest> requestStore = new ConcurrentHashMap<>();
    private final Map<String, List<LeetCodeRecommendItem>> itemStore = new ConcurrentHashMap<>();

    private static class FeedbackContext {
        private final Map<Long, Double> scoreAdjustments = new HashMap<>();
        private final Set<Long> dislikedProblemIds = new HashSet<>();
        private final Set<Long> completedProblemIds = new HashSet<>();
    }

    @Override
    public String generateRecommendation(Integer studentId, Integer limit, String scene) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId cannot be null");
        }

        int actualLimit = normalizeLimit(limit);
        String actualScene = (scene == null || scene.isBlank()) ? "default" : scene;

        String requestId = UUID.randomUUID().toString();
        logger.info("生成推荐请求ID: {} for 学生: {}", requestId, studentId);

        LeetCodeRecommendRequest request = new LeetCodeRecommendRequest(requestId, studentId, actualScene, actualLimit);
        request.setCreatedAt(LocalDateTime.now());
        request.setStatus(LeetCodeRecommendRequest.STATUS_PENDING);
        requestStore.put(requestId, request);

        try {
            List<LeetCodeRecommendItem> items = generateRecommendationSync(studentId, actualLimit);
            if (items == null || items.isEmpty()) {
                request.setStatus(LeetCodeRecommendRequest.STATUS_FAILED);
                request.setErrorMessage("未生成推荐结果");
                request.setFinishedAt(LocalDateTime.now());
                itemStore.put(requestId, Collections.emptyList());
            } else {
                request.setStatus(LeetCodeRecommendRequest.STATUS_COMPLETED);
                request.setFinishedAt(LocalDateTime.now());
                itemStore.put(requestId, items);
            }
        } catch (Exception ex) {
            request.setStatus(LeetCodeRecommendRequest.STATUS_FAILED);
            request.setErrorMessage(ex.getMessage());
            request.setFinishedAt(LocalDateTime.now());
            itemStore.put(requestId, Collections.emptyList());
            logger.error("异步推荐失败，requestId={}, studentId={}", requestId, studentId, ex);
        }

        return requestId;
    }

    @Override
    public LeetCodeRecommendRequest getRecommendationResult(String requestId) {
        return requestStore.get(requestId);
    }

    @Override
    public List<LeetCodeRecommendItem> getRecommendationItems(String requestId) {
        return new ArrayList<>(itemStore.getOrDefault(requestId, Collections.emptyList()));
    }
    @Override
    public List<LeetCodeRecommendItem> generateRecommendationSync(Integer studentId, Integer limit) {
        try {
            int actualLimit = normalizeLimit(limit);
            logger.info("开始为学生 {} 生成智能推荐，数量限制: {}", studentId, actualLimit);

            // 1. 获取学生技能画像
            Map<String, StudentSkillState> skillProfile = getStudentSkillProfile(studentId);
            
            // 2. 多路召回策略（按problemId去重）
            Map<Long, LeetCodeProblem> candidateById = new LinkedHashMap<>();
            addCandidates(candidateById, recallByWeakness(skillProfile, Math.max(1, (int) (actualLimit * 0.4))));
            addCandidates(candidateById, recallByDifficulty(skillProfile, Math.max(1, (int) (actualLimit * 0.4))));
            addCandidates(candidateById, recallByExploration(skillProfile, Math.max(1, (int) (actualLimit * 0.15))));
            addCandidates(candidateById, recallByPopularity(Math.max(1, (int) (actualLimit * 0.05))));

            FeedbackContext feedbackContext = buildFeedbackContext(studentId);
            candidateById = applyHistoryFilter(candidateById, feedbackContext, actualLimit);
            int minCandidateCount = Math.max(actualLimit, Math.min(60, actualLimit * 3));
            if (candidateById.size() < minCandidateCount) {
                supplementCandidates(candidateById, minCandidateCount);
            }

            logger.info("召回候选题目数量(过滤后): {}", candidateById.size());

            if (candidateById.isEmpty()) {
                logger.warn("智能召回为空，使用降级推荐。studentId={}", studentId);
                return getFallbackRecommendations(studentId, actualLimit);
            }

            // 3. 智能排序和打分
            List<LeetCodeRecommendItem> rankedItems = rankAndScore(
                new ArrayList<>(candidateById.values()), skillProfile, studentId, feedbackContext.scoreAdjustments);
            
            // 4. 多样性重排
            List<LeetCodeRecommendItem> diversifiedItems = diversityRerank(rankedItems, actualLimit);
            
            // 5. 截取最终结果
            List<LeetCodeRecommendItem> finalItems = diversifiedItems.stream()
                .limit(actualLimit)
                .collect(Collectors.toList());
            
            // 6. 更新排名
            for (int i = 0; i < finalItems.size(); i++) {
                finalItems.get(i).setRankNo(i + 1);
            }
            
            logger.info("为学生 {} 生成智能推荐完成，最终推荐数量: {}", studentId, finalItems.size());
            return finalItems;
            
        } catch (Exception e) {
            logger.error("生成智能推荐失败，学生ID: " + studentId, e);
            return getFallbackRecommendations(studentId, normalizeLimit(limit));
        }
    }

    /**
     * 获取学生技能画像
     */
    private Map<String, StudentSkillState> getStudentSkillProfile(Integer studentId) {
        List<StudentSkillState> skillStates = skillStateDao.findByStudentId(studentId);
        if (skillStates == null || skillStates.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return skillStates.stream()
            .collect(Collectors.toMap(
                StudentSkillState::getTagName,
                skill -> skill,
                (existing, replacement) -> existing,
                LinkedHashMap::new
            ));
    }

    /**
     * 薄弱点召回 - 针对学生掌握度低的技能推荐题目
     */
    private List<LeetCodeProblem> recallByWeakness(Map<String, StudentSkillState> skillProfile, int limit) {
        // 找出掌握度最低的几个技能
        List<String> weakSkills = skillProfile.values().stream()
            .filter(skill -> getMasteryValue(skill) < 60.0) // 掌握度低于60分
            .sorted(Comparator.comparing(this::getMasteryValue))
            .limit(5)
            .map(StudentSkillState::getTagName)
            .collect(Collectors.toList());
        
        if (weakSkills.isEmpty()) {
            // 如果没有明显薄弱技能，选择练习次数最少的技能
            weakSkills = skillProfile.values().stream()
                .sorted(Comparator.comparing(this::getAttemptCountValue))
                .limit(3)
                .map(StudentSkillState::getTagName)
                .collect(Collectors.toList());
        }
        
        logger.debug("学生薄弱技能: {}", weakSkills);
        
        // 根据薄弱技能查找相关题目
        return findProblemsByTags(weakSkills, limit);
    }

    /**
     * 难度梯度召回 - 根据学生整体水平推荐合适难度的题目
     */
    private List<LeetCodeProblem> recallByDifficulty(Map<String, StudentSkillState> skillProfile, int limit) {
        // 计算学生整体掌握度
        double avgMastery = skillProfile.values().stream()
            .mapToDouble(this::getMasteryValue)
            .average()
            .orElse(50.0);
        
        String targetDifficulty;
        if (avgMastery < 40) {
            targetDifficulty = "Easy";
        } else if (avgMastery < 70) {
            targetDifficulty = "Medium";
        } else {
            targetDifficulty = "Hard";
        }
        
        logger.debug("学生平均掌握度: {}, 目标难度: {}", avgMastery, targetDifficulty);
        
        List<LeetCodeProblem> problems = problemDao.findByDifficulty(targetDifficulty);
        if (problems == null) {
            return new ArrayList<>();
        }
        if (problems.isEmpty()) {
            // 兼容 difficulty 存储大小写不一致的情况
            String target = targetDifficulty.toLowerCase(Locale.ROOT);
            problems = problemDao.findAll().stream()
                .filter(p -> Optional.ofNullable(p.getDifficulty())
                    .map(d -> d.toLowerCase(Locale.ROOT))
                    .orElse("")
                    .equals(target))
                .collect(Collectors.toList());
        }

        return problems.stream()
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * 探索性召回 - 推荐学生未接触过的新技能领域
     */
    private List<LeetCodeProblem> recallByExploration(Map<String, StudentSkillState> skillProfile, int limit) {
        // 获取所有算法标签
        Set<String> allTags = Set.of("动态规划", "贪心", "回溯", "图", "树", "堆", "并查集", "位运算");
        
        // 找出学生未接触或接触很少的技能
        Set<String> exploredTags = skillProfile.keySet();
        List<String> unexploredTags = allTags.stream()
            .filter(tag -> {
                StudentSkillState state = skillProfile.get(tag);
                return state == null || getAttemptCountValue(state) < 3;
            })
            .collect(Collectors.toList());
        
        logger.debug("探索性技能: {}", unexploredTags);
        
        return findProblemsByTags(unexploredTags, limit);
    }

    /**
     * 热门题召回 - 推荐高质量的经典题目
     */
    private List<LeetCodeProblem> recallByPopularity(int limit) {
        // 简单实现：返回质量分数最高的题目
        return problemDao.findAll().stream()
            .sorted((p1, p2) -> {
                BigDecimal score1 = p1.getQualityScore() != null ? p1.getQualityScore() : BigDecimal.ZERO;
                BigDecimal score2 = p2.getQualityScore() != null ? p2.getQualityScore() : BigDecimal.ZERO;
                return score2.compareTo(score1);
            })
            .limit(limit)
            .collect(Collectors.toList());
    }
    /**
     * 根据标签查找题目 - 使用标签表进行精确匹配
     */
    private List<LeetCodeProblem> findProblemsByTags(List<String> tags, int limit) {
        if (tags.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            // 使用标签表查找题目ID
            List<Long> problemIds = problemTagDao.findProblemIdsByTags("algorithm", tags);
            
            if (problemIds.isEmpty()) {
                // 如果没有找到，回退到文本匹配
                return findProblemsByTagsTextMatch(tags, limit);
            }

            int fetchLimit = Math.max(limit, limit * 3);
            List<Long> limitedProblemIds = problemIds.stream()
                .filter(Objects::nonNull)
                .limit(fetchLimit)
                .collect(Collectors.toList());
            if (limitedProblemIds.isEmpty()) {
                return new ArrayList<>();
            }

            // 批量查询，避免N+1
            List<LeetCodeProblem> batchProblems = problemDao.findByIds(limitedProblemIds);
            if (batchProblems == null || batchProblems.isEmpty()) {
                return findProblemsByTagsTextMatch(tags, limit);
            }

            Map<Long, LeetCodeProblem> problemById = batchProblems.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(LeetCodeProblem::getId, p -> p, (a, b) -> a));

            List<LeetCodeProblem> orderedProblems = new ArrayList<>();
            for (Long problemId : limitedProblemIds) {
                LeetCodeProblem problem = problemById.get(problemId);
                if (problem != null) {
                    orderedProblems.add(problem);
                }
                if (orderedProblems.size() >= limit) {
                    break;
                }
            }

            return orderedProblems;
            
        } catch (Exception e) {
            logger.warn("使用标签查找题目失败，回退到文本匹配: {}", e.getMessage());
            return findProblemsByTagsTextMatch(tags, limit);
        }
    }

    /**
     * 文本匹配方式查找题目（备用方案）
     */
    private List<LeetCodeProblem> findProblemsByTagsTextMatch(List<String> tags, int limit) {
        List<LeetCodeProblem> allProblems = problemDao.findAll();
        List<LeetCodeProblem> matchedProblems = new ArrayList<>();
        
        for (LeetCodeProblem problem : allProblems) {
            String problemText = buildProblemText(problem);
            for (String tag : tags) {
                String normalizedTag = tag.toLowerCase(Locale.ROOT);
                String englishTag = getEnglishTag(tag).toLowerCase(Locale.ROOT);
                if (problemText.contains(normalizedTag) || problemText.contains(englishTag)) {
                    matchedProblems.add(problem);
                    break;
                }
            }
            if (matchedProblems.size() >= limit * 2) { // 获取更多候选，后续筛选
                break;
            }
        }
        
        Collections.shuffle(matchedProblems);
        return matchedProblems.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * 获取中文标签对应的英文标签
     */
    private String getEnglishTag(String chineseTag) {
        Map<String, String> tagMap = new HashMap<>();
        tagMap.put("动态规划", "dynamic programming");
        tagMap.put("贪心", "greedy");
        tagMap.put("回溯", "backtrack");
        tagMap.put("图", "graph");
        tagMap.put("树", "tree");
        tagMap.put("堆", "heap");
        tagMap.put("并查集", "union find");
        tagMap.put("位运算", "bit manipulation");
        tagMap.put("数组", "array");
        tagMap.put("字符串", "string");
        tagMap.put("链表", "linked list");
        tagMap.put("哈希表", "hash");
        
        return tagMap.getOrDefault(chineseTag, chineseTag);
    }

    /**
     * 智能排序和打分
     */
    private List<LeetCodeRecommendItem> rankAndScore(List<LeetCodeProblem> problems,
                                                   Map<String, StudentSkillState> skillProfile,
                                                   Integer studentId,
                                                   Map<Long, Double> feedbackAdjustments) {
        List<LeetCodeRecommendItem> items = new ArrayList<>();
        
        for (LeetCodeProblem problem : problems) {
            LeetCodeRecommendItem item = new LeetCodeRecommendItem();
            item.setProblemId(problem.getId());
            item.setProblem(problem);
            item.setStudentId(studentId);
            
            // 计算各维度分数
            double weaknessScore = calculateWeaknessMatchScore(problem, skillProfile);
            double difficultyScore = calculateDifficultyMatchScore(problem, skillProfile);
            double noveltyScore = calculateNoveltyScore(problem, skillProfile);
            double diversityScore = 0.8; // 多样性分数在重排阶段计算
            double qualityScore = problem.getQualityScore() != null ? 
                problem.getQualityScore().doubleValue() : 0.8;
            
            // 计算总分
            double totalScore = WEAKNESS_WEIGHT * weaknessScore +
                              DIFFICULTY_WEIGHT * difficultyScore +
                              NOVELTY_WEIGHT * noveltyScore +
                              DIVERSITY_WEIGHT * diversityScore +
                              QUALITY_WEIGHT * qualityScore;

            if (problem.getId() != null) {
                totalScore += feedbackAdjustments.getOrDefault(problem.getId(), 0.0);
            }
            totalScore = clamp(totalScore, MIN_TOTAL_SCORE, MAX_TOTAL_SCORE);
            
            // 设置分数
            item.setScoreTotal(BigDecimal.valueOf(totalScore).setScale(4, RoundingMode.HALF_UP));
            item.setScoreNeedMatch(BigDecimal.valueOf(weaknessScore).setScale(4, RoundingMode.HALF_UP));
            item.setScoreDifficultyFit(BigDecimal.valueOf(difficultyScore).setScale(4, RoundingMode.HALF_UP));
            item.setScoreSuccessProb(BigDecimal.valueOf(difficultyScore).setScale(4, RoundingMode.HALF_UP));
            item.setScoreNovelty(BigDecimal.valueOf(noveltyScore).setScale(4, RoundingMode.HALF_UP));
            item.setScoreQuality(BigDecimal.valueOf(qualityScore).setScale(4, RoundingMode.HALF_UP));
            
            // 生成推荐理由
            item.setReasonText(generateReasonText(problem, skillProfile, weaknessScore, difficultyScore));
            
            items.add(item);
        }
        
        // 按总分排序
        items.sort((a, b) -> b.getScoreTotal().compareTo(a.getScoreTotal()));
        
        return items;
    }

    /**
     * 计算薄弱点匹配分数
     */
    private double calculateWeaknessMatchScore(LeetCodeProblem problem, Map<String, StudentSkillState> skillProfile) {
        String problemText = buildProblemText(problem);
        
        double maxWeaknessScore = 0.0;
        for (StudentSkillState skill : skillProfile.values()) {
            String rawTagName = Optional.ofNullable(skill.getTagName()).orElse("");
            String tagName = rawTagName.toLowerCase(Locale.ROOT);
            String englishTag = getEnglishTag(rawTagName).toLowerCase(Locale.ROOT);
            if ((!tagName.isEmpty() && problemText.contains(tagName)) || (!englishTag.isEmpty() && problemText.contains(englishTag))) {
                // 掌握度越低，推荐分数越高
                double masteryScore = getMasteryValue(skill);
                double weaknessScore = Math.max(0, (100 - masteryScore) / 100.0);
                maxWeaknessScore = Math.max(maxWeaknessScore, weaknessScore);
            }
        }
        
        return maxWeaknessScore;
    }

    /**
     * 计算难度匹配分数
     */
    private double calculateDifficultyMatchScore(LeetCodeProblem problem, Map<String, StudentSkillState> skillProfile) {
        // 计算学生整体水平
        double avgMastery = skillProfile.values().stream()
            .mapToDouble(this::getMasteryValue)
            .average()
            .orElse(50.0);
        
        String difficulty = Optional.ofNullable(problem.getDifficulty())
            .orElse("medium")
            .toLowerCase(Locale.ROOT);
        
        // 根据学生水平和题目难度计算匹配度
        if (avgMastery < 40) {
            return "easy".equals(difficulty) ? 1.0 : ("medium".equals(difficulty) ? 0.3 : 0.1);
        } else if (avgMastery < 70) {
            return "medium".equals(difficulty) ? 1.0 : ("easy".equals(difficulty) ? 0.7 : 0.4);
        } else {
            return "hard".equals(difficulty) ? 1.0 : ("medium".equals(difficulty) ? 0.8 : 0.2);
        }
    }

    /**
     * 计算新颖性分数
     */
    private double calculateNoveltyScore(LeetCodeProblem problem, Map<String, StudentSkillState> skillProfile) {
        String problemText = buildProblemText(problem);
        
        // 检查题目涉及的技能是否是学生较少接触的
        int totalAttempts = 0;
        int matchedSkills = 0;
        
        for (StudentSkillState skill : skillProfile.values()) {
            String rawTagName = Optional.ofNullable(skill.getTagName()).orElse("");
            String tagName = rawTagName.toLowerCase(Locale.ROOT);
            String englishTag = getEnglishTag(rawTagName).toLowerCase(Locale.ROOT);
            if ((!tagName.isEmpty() && problemText.contains(tagName)) || (!englishTag.isEmpty() && problemText.contains(englishTag))) {
                totalAttempts += getAttemptCountValue(skill);
                matchedSkills++;
            }
        }
        
        if (matchedSkills == 0) {
            return 1.0; // 完全新的技能领域
        }
        
        double avgAttempts = (double) totalAttempts / matchedSkills;
        return Math.max(0.1, 1.0 - (avgAttempts / 10.0)); // 尝试次数越少，新颖性越高
    }
    /**
     * 多样性重排 - 确保推荐结果的多样性
     */
    private List<LeetCodeRecommendItem> diversityRerank(List<LeetCodeRecommendItem> items, int limit) {
        if (items.size() <= limit) {
            return items;
        }
        
        List<LeetCodeRecommendItem> result = new ArrayList<>();
        Set<String> usedDifficulties = new HashSet<>();
        Set<String> usedTags = new HashSet<>();
        
        // 第一轮：选择不同难度和标签的题目
        for (LeetCodeRecommendItem item : items) {
            if (result.size() >= limit) break;
            
            String difficulty = Optional.ofNullable(item.getProblem())
                .map(LeetCodeProblem::getDifficulty)
                .orElse("unknown");
            String problemText = buildProblemText(item.getProblem());
            
            // 检查是否增加了多样性
            boolean addsDiversity = false;
            if (!usedDifficulties.contains(difficulty)) {
                addsDiversity = true;
                usedDifficulties.add(difficulty);
            }
            
            // 简单的标签检查（基于关键词）
            List<String> tags = Arrays.asList("数组", "字符串", "动态规划", "贪心", "图", "树");
            for (String tag : tags) {
                if (problemText.contains(tag.toLowerCase()) && !usedTags.contains(tag)) {
                    addsDiversity = true;
                    usedTags.add(tag);
                    break;
                }
            }
            
            if (addsDiversity || result.size() < limit / 2) {
                result.add(item);
            }
        }
        
        // 第二轮：填充剩余位置
        for (LeetCodeRecommendItem item : items) {
            if (result.size() >= limit) break;
            if (!result.contains(item)) {
                result.add(item);
            }
        }
        
        return result;
    }

    /**
     * 生成推荐理由
     */
    private String generateReasonText(LeetCodeProblem problem, Map<String, StudentSkillState> skillProfile, 
                                    double weaknessScore, double difficultyScore) {
        StringBuilder reason = new StringBuilder();
        
        // 基于薄弱点的推荐理由
        if (weaknessScore > 0.6) {
            reason.append("针对你的薄弱技能点进行强化练习。");
        }
        
        // 基于难度的推荐理由
        String difficulty = Optional.ofNullable(problem.getDifficulty())
            .orElse("medium")
            .toLowerCase(Locale.ROOT);
        if ("easy".equals(difficulty)) {
            reason.append("适合基础巩固，建议先掌握基本思路。");
        } else if ("medium".equals(difficulty)) {
            reason.append("中等难度，适合提升解题能力。");
        } else if ("hard".equals(difficulty)) {
            reason.append("高难度挑战，有助于突破技能瓶颈。");
        }
        
        // 预计用时
        Integer estimatedMinutes = problem.getEstimatedMinutes();
        if (estimatedMinutes != null) {
            reason.append(String.format(" 预计用时 %d 分钟。", estimatedMinutes));
        }
        
        return reason.toString();
    }

    /**
     * 降级推荐 - 当智能推荐失败时的备选方案
     */
    private List<LeetCodeRecommendItem> getFallbackRecommendations(Integer studentId, Integer limit) {
        logger.warn("使用降级推荐策略 for 学生: {}", studentId);
        
        try {
            int actualLimit = normalizeLimit(limit);
            List<LeetCodeProblem> problems = problemDao.findByPage(0, actualLimit);
            List<LeetCodeRecommendItem> items = new ArrayList<>();
            
            for (int i = 0; i < problems.size(); i++) {
                LeetCodeProblem problem = problems.get(i);
                LeetCodeRecommendItem item = new LeetCodeRecommendItem();
                
                item.setProblemId(problem.getId());
                item.setProblem(problem);
                item.setStudentId(studentId);
                item.setRankNo(i + 1);
                item.setScoreTotal(BigDecimal.valueOf(0.6).setScale(4, RoundingMode.HALF_UP));
                item.setScoreNeedMatch(BigDecimal.valueOf(0.5).setScale(4, RoundingMode.HALF_UP));
                item.setScoreDifficultyFit(BigDecimal.valueOf(0.6).setScale(4, RoundingMode.HALF_UP));
                item.setScoreSuccessProb(BigDecimal.valueOf(0.6).setScale(4, RoundingMode.HALF_UP));
                item.setScoreNovelty(BigDecimal.valueOf(0.7).setScale(4, RoundingMode.HALF_UP));
                item.setScoreQuality(BigDecimal.valueOf(0.8).setScale(4, RoundingMode.HALF_UP));
                item.setReasonText("系统推荐的优质题目，适合当前学习阶段。");
                
                items.add(item);
            }
            
            return items;
        } catch (Exception e) {
            logger.error("降级推荐也失败了", e);
            return new ArrayList<>();
        }
    }

    private void addCandidates(Map<Long, LeetCodeProblem> candidateById, List<LeetCodeProblem> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (LeetCodeProblem problem : candidates) {
            if (problem == null || problem.getId() == null) {
                continue;
            }
            candidateById.putIfAbsent(problem.getId(), problem);
        }
    }

    private void supplementCandidates(Map<Long, LeetCodeProblem> candidateById, int targetCount) {
        if (candidateById == null || targetCount <= 0) {
            return;
        }

        try {
            int batchSize = Math.max(targetCount * 2, 40);
            List<LeetCodeProblem> pageProblems = problemDao.findByPage(0, batchSize);
            addCandidates(candidateById, pageProblems);
            if (candidateById.size() >= targetCount) {
                return;
            }
        } catch (Exception e) {
            logger.warn("分页补充候选失败: {}", e.getMessage());
        }

        try {
            List<LeetCodeProblem> allProblems = problemDao.findAll();
            if (allProblems != null && !allProblems.isEmpty()) {
                Collections.shuffle(allProblems);
                addCandidates(candidateById, allProblems);
            }
        } catch (Exception e) {
            logger.warn("全量补充候选失败: {}", e.getMessage());
        }
    }

    private FeedbackContext buildFeedbackContext(Integer studentId) {
        FeedbackContext context = new FeedbackContext();
        if (studentId == null) {
            return context;
        }
        try {
            List<LeetCodeRecommendFeedback> feedbackList = feedbackDao.findByStudentId(studentId, 300);
            if (feedbackList == null || feedbackList.isEmpty()) {
                return context;
            }

            // exposure, click, start, complete, skip, dislike
            Map<Long, int[]> counters = new HashMap<>();
            for (int i = 0; i < feedbackList.size(); i++) {
                LeetCodeRecommendFeedback feedback = feedbackList.get(i);
                Long problemId = feedback.getProblemId();
                if (problemId == null) {
                    continue;
                }

                String action = normalizeAction(feedback.getAction());
                if (action == null || !VALID_FEEDBACK_ACTIONS.contains(action)) {
                    continue;
                }

                int[] stat = counters.computeIfAbsent(problemId, k -> new int[6]);
                switch (action) {
                    case LeetCodeRecommendFeedback.ACTION_EXPOSURE:
                        stat[0]++;
                        break;
                    case LeetCodeRecommendFeedback.ACTION_CLICK:
                        stat[1]++;
                        break;
                    case LeetCodeRecommendFeedback.ACTION_START:
                        stat[2]++;
                        break;
                    case LeetCodeRecommendFeedback.ACTION_COMPLETE:
                        stat[3]++;
                        break;
                    case LeetCodeRecommendFeedback.ACTION_SKIP:
                        stat[4]++;
                        break;
                    case LeetCodeRecommendFeedback.ACTION_DISLIKE:
                        stat[5]++;
                        break;
                    default:
                        break;
                }

                double recencyFactor = Math.max(0.30, 1.0 - (i * 0.008));
                context.scoreAdjustments.merge(problemId, getFeedbackScoreDelta(action) * recencyFactor, Double::sum);
            }

            for (Map.Entry<Long, int[]> entry : counters.entrySet()) {
                Long problemId = entry.getKey();
                int[] stat = entry.getValue();
                int exposureCount = stat[0];
                int clickCount = stat[1];
                int startCount = stat[2];
                int completeCount = stat[3];
                int skipCount = stat[4];
                int dislikeCount = stat[5];
                int engagedCount = clickCount + startCount + completeCount;

                if (completeCount > 0) {
                    context.completedProblemIds.add(problemId);
                }
                if (dislikeCount > 0) {
                    context.dislikedProblemIds.add(problemId);
                }

                int idleExposure = Math.max(0, exposureCount - engagedCount);
                if (idleExposure >= 2) {
                    double repeatExposurePenalty = -Math.min(0.25, (idleExposure - 1) * 0.05);
                    context.scoreAdjustments.merge(problemId, repeatExposurePenalty, Double::sum);
                }

                if (skipCount > 1) {
                    double repeatedSkipPenalty = -Math.min(0.20, (skipCount - 1) * 0.04);
                    context.scoreAdjustments.merge(problemId, repeatedSkipPenalty, Double::sum);
                }
            }

            context.scoreAdjustments.replaceAll((k, v) -> clamp(v, -0.55, 0.25));
            return context;
        } catch (Exception e) {
            logger.warn("读取反馈数据失败，忽略反馈加权。studentId={}, error={}", studentId, e.getMessage());
            return context;
        }
    }

    private Map<Long, LeetCodeProblem> applyHistoryFilter(
        Map<Long, LeetCodeProblem> candidateById,
        FeedbackContext feedbackContext,
        int limit
    ) {
        if (candidateById == null || candidateById.isEmpty() || feedbackContext == null) {
            return candidateById;
        }

        Map<Long, LeetCodeProblem> dislikedFiltered = new LinkedHashMap<>(candidateById);
        dislikedFiltered.keySet().removeIf(feedbackContext.dislikedProblemIds::contains);

        Map<Long, LeetCodeProblem> doneFiltered = new LinkedHashMap<>(dislikedFiltered);
        doneFiltered.keySet().removeIf(feedbackContext.completedProblemIds::contains);

        int minKeep = Math.max(3, Math.max(1, limit / 2));
        if (doneFiltered.size() >= minKeep) {
            return doneFiltered;
        }
        if (!dislikedFiltered.isEmpty()) {
            return dislikedFiltered;
        }
        return candidateById;
    }

    private double getFeedbackScoreDelta(String action) {
        if (action == null) {
            return 0.0;
        }
        switch (action) {
            case LeetCodeRecommendFeedback.ACTION_EXPOSURE:
                return -0.01;
            case LeetCodeRecommendFeedback.ACTION_CLICK:
                return 0.04;
            case LeetCodeRecommendFeedback.ACTION_START:
                return 0.06;
            case LeetCodeRecommendFeedback.ACTION_COMPLETE:
                return -0.28;
            case LeetCodeRecommendFeedback.ACTION_SKIP:
                return -0.12;
            case LeetCodeRecommendFeedback.ACTION_DISLIKE:
                return -0.35;
            default:
                return 0.0;
        }
    }

    private String normalizeAction(String action) {
        return action == null ? null : action.toLowerCase(Locale.ROOT).trim();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double getMasteryValue(StudentSkillState skill) {
        if (skill == null || skill.getMasteryScore() == null) {
            return 50.0;
        }
        return skill.getMasteryScore().doubleValue();
    }

    private int getAttemptCountValue(StudentSkillState skill) {
        if (skill == null || skill.getAttemptCount() == null) {
            return 0;
        }
        return skill.getAttemptCount();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 20;
        }
        return Math.max(1, Math.min(limit, 50));
    }

    private String buildProblemText(LeetCodeProblem problem) {
        if (problem == null) {
            return "";
        }
        String title = Optional.ofNullable(problem.getTitleMain()).orElse("");
        String text = Optional.ofNullable(problem.getProblemText()).orElse("");
        String solution = Optional.ofNullable(problem.getSolutionText()).orElse("");
        return (title + " " + text + " " + solution).toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean recordFeedback(String requestId, Integer studentId, Long problemId, String action, String sessionId) {
        try {
            String normalizedAction = normalizeAction(action);
            if (studentId == null || problemId == null || normalizedAction == null || !VALID_FEEDBACK_ACTIONS.contains(normalizedAction)) {
                logger.warn("忽略非法反馈: requestId={}, studentId={}, problemId={}, action={}",
                    requestId, studentId, problemId, action);
                return false;
            }

            LeetCodeRecommendFeedback feedback = new LeetCodeRecommendFeedback();
            feedback.setRequestId(requestId);
            feedback.setStudentId(studentId);
            feedback.setProblemId(problemId);
            feedback.setSessionId(sessionId);
            feedback.setAction(normalizedAction);
            feedback.setActionAt(LocalDateTime.now());

            int affected = feedbackDao.insertFeedback(feedback);
            boolean success = affected > 0;
            logger.info("记录推荐反馈: requestId={}, studentId={}, problemId={}, action={}, success={}",
                requestId, studentId, problemId, normalizedAction, success);
            return success;
        } catch (Exception e) {
            logger.error("记录推荐反馈失败", e);
            return false;
        }
    }
}
