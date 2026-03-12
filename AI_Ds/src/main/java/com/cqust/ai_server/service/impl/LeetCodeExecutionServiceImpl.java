package com.cqust.ai_server.service.impl;

import com.cqust.ai_server.entity.LeetCodeProblem;
import com.cqust.ai_server.service.LeetCodeExecutionService;
import com.cqust.ai_server.service.LeetCodeProblemService;
import com.cqust.ai_server.service.StudentSkillProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * LeetCode代码执行服务实现
 */
@Service
public class LeetCodeExecutionServiceImpl implements LeetCodeExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(LeetCodeExecutionServiceImpl.class);

    @Autowired
    private LeetCodeProblemService problemService;

    @Autowired
    private StudentSkillProfileService skillProfileService;

    // 支持的编程语言配置
    private static final Map<String, LanguageConfig> LANGUAGE_CONFIGS = new HashMap<>();
    
    static {
        LANGUAGE_CONFIGS.put("java", new LanguageConfig("java", ".java", "javac", "java"));
        LANGUAGE_CONFIGS.put("python", new LanguageConfig("python", ".py", "python", "python"));
        LANGUAGE_CONFIGS.put("cpp", new LanguageConfig("cpp", ".cpp", "g++", "./"));
        LANGUAGE_CONFIGS.put("javascript", new LanguageConfig("javascript", ".js", "node", "node"));
    }

    @Override
    public Map<String, Object> runCode(Long problemId, String code, String language, String testInput) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取题目信息
            LeetCodeProblem problem = problemService.findById(problemId);
            if (problem == null) {
                result.put("status", "error");
                result.put("output", "题目不存在");
                return result;
            }

            // 验证语言支持
            LanguageConfig config = LANGUAGE_CONFIGS.get(language);
            if (config == null) {
                result.put("status", "error");
                result.put("output", "不支持的编程语言: " + language);
                return result;
            }

            // 执行代码
            ExecutionResult execResult = executeCode(code, language, testInput, false);
            
            result.put("status", execResult.success ? "success" : "error");
            result.put("output", execResult.output);
            result.put("error", execResult.error);
            result.put("runtime", execResult.runtime + "ms");
            
        } catch (Exception e) {
            logger.error("运行代码失败", e);
            result.put("status", "error");
            result.put("output", "运行失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> submitSolution(Integer studentId, Long problemId, String code, String language) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取题目信息
            LeetCodeProblem problem = problemService.findById(problemId);
            if (problem == null) {
                result.put("accepted", false);
                result.put("message", "题目不存在");
                return result;
            }

            // 执行完整测试
            List<TestCase> testCases = generateTestCases(problem);
            ExecutionSummary summary = runAllTestCases(code, language, testCases);
            
            // 计算得分
            int score = calculateScore(summary);
            boolean accepted = summary.passedCases == summary.totalCases && summary.totalCases > 0;
            
            // 生成AI反馈
            String aiFeedback = generateAIFeedback(problem, code, language, summary, accepted);
            
            // 更新学生技能画像
            if (accepted) {
                updateStudentSkillProfile(studentId, problem, true);
            } else {
                updateStudentSkillProfile(studentId, problem, false);
            }

            // 构建返回结果
            result.put("accepted", accepted);
            result.put("score", score);
            result.put("aiFeedback", aiFeedback);
            
            Map<String, Object> details = new HashMap<>();
            details.put("passedCases", summary.passedCases);
            details.put("totalCases", summary.totalCases);
            details.put("runtime", summary.avgRuntime + "ms");
            details.put("memory", "N/A"); // 暂不实现内存统计
            if (!summary.errors.isEmpty()) {
                details.put("error", String.join("\n", summary.errors));
            }
            result.put("details", details);
            
            // 技能提升建议
            result.put("skillSuggestions", generateSkillSuggestions(problem, accepted));
            
        } catch (Exception e) {
            logger.error("提交解答失败", e);
            result.put("accepted", false);
            result.put("message", "提交失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 执行代码
     */
    private ExecutionResult executeCode(String code, String language, String input, boolean isFullTest) {
        ExecutionResult result = new ExecutionResult();
        
        try {
            LanguageConfig config = LANGUAGE_CONFIGS.get(language);
            
            // 创建临时目录
            Path tempDir = Files.createTempDirectory("leetcode_exec_");
            Path sourceFile = tempDir.resolve("Solution" + config.extension);
            
            // 写入源代码
            Files.write(sourceFile, code.getBytes());
            
            long startTime = System.currentTimeMillis();
            
            // 编译（如果需要）
            if (config.needsCompilation()) {
                ProcessBuilder compileBuilder = new ProcessBuilder();
                compileBuilder.directory(tempDir.toFile());
                
                if ("java".equals(language)) {
                    compileBuilder.command("javac", sourceFile.getFileName().toString());
                } else if ("cpp".equals(language)) {
                    compileBuilder.command("g++", "-o", "solution", sourceFile.getFileName().toString());
                }
                
                Process compileProcess = compileBuilder.start();
                int compileExitCode = compileProcess.waitFor();
                
                if (compileExitCode != 0) {
                    result.success = false;
                    result.error = readStream(compileProcess.getErrorStream());
                    return result;
                }
            }
            
            // 执行
            ProcessBuilder runBuilder = new ProcessBuilder();
            runBuilder.directory(tempDir.toFile());
            
            if ("java".equals(language)) {
                runBuilder.command("java", "Solution");
            } else if ("python".equals(language)) {
                runBuilder.command("python", sourceFile.getFileName().toString());
            } else if ("cpp".equals(language)) {
                runBuilder.command("./solution");
            } else if ("javascript".equals(language)) {
                runBuilder.command("node", sourceFile.getFileName().toString());
            }
            
            Process runProcess = runBuilder.start();
            
            // 提供输入
            if (input != null && !input.trim().isEmpty()) {
                try (PrintWriter writer = new PrintWriter(runProcess.getOutputStream())) {
                    writer.println(input);
                    writer.flush();
                }
            }
            
            // 等待执行完成（设置超时）
            boolean finished = runProcess.waitFor(5, TimeUnit.SECONDS);
            
            if (!finished) {
                runProcess.destroyForcibly();
                result.success = false;
                result.error = "执行超时";
                return result;
            }
            
            long endTime = System.currentTimeMillis();
            result.runtime = endTime - startTime;
            
            int exitCode = runProcess.exitValue();
            if (exitCode == 0) {
                result.success = true;
                result.output = readStream(runProcess.getInputStream());
            } else {
                result.success = false;
                result.error = readStream(runProcess.getErrorStream());
            }
            
            // 清理临时文件
            deleteDirectory(tempDir.toFile());
            
        } catch (Exception e) {
            logger.error("执行代码异常", e);
            result.success = false;
            result.error = "执行异常: " + e.getMessage();
        }
        
        return result;
    }

    /**
     * 生成测试用例
     */
    private List<TestCase> generateTestCases(LeetCodeProblem problem) {
        List<TestCase> testCases = new ArrayList<>();
        
        // 这里简化处理，实际应该从题目描述中解析或从数据库获取
        testCases.add(new TestCase("示例输入1", "期望输出1"));
        testCases.add(new TestCase("示例输入2", "期望输出2"));
        testCases.add(new TestCase("边界情况1", "边界输出1"));
        
        return testCases;
    }

    /**
     * 运行所有测试用例
     */
    private ExecutionSummary runAllTestCases(String code, String language, List<TestCase> testCases) {
        ExecutionSummary summary = new ExecutionSummary();
        summary.totalCases = testCases.size();
        
        long totalRuntime = 0;
        
        for (TestCase testCase : testCases) {
            ExecutionResult result = executeCode(code, language, testCase.input, true);
            
            if (result.success) {
                // 简化的输出比较，实际应该更智能
                if (result.output.trim().equals(testCase.expectedOutput.trim())) {
                    summary.passedCases++;
                }
            } else {
                summary.errors.add(result.error);
            }
            
            totalRuntime += result.runtime;
        }
        
        summary.avgRuntime = summary.totalCases > 0 ? totalRuntime / summary.totalCases : 0;
        
        return summary;
    }

    /**
     * 计算得分
     */
    private int calculateScore(ExecutionSummary summary) {
        if (summary.totalCases == 0) return 0;
        
        double passRate = (double) summary.passedCases / summary.totalCases;
        int baseScore = (int) (passRate * 80); // 基础分80%
        
        // 性能加分
        if (summary.avgRuntime < 100) {
            baseScore += 20;
        } else if (summary.avgRuntime < 500) {
            baseScore += 10;
        }
        
        return Math.min(100, baseScore);
    }

    /**
     * 生成AI反馈 - 优化提示词设计
     */
    private String generateAIFeedback(LeetCodeProblem problem, String code, String language, 
                                    ExecutionSummary summary, boolean accepted) {
        StringBuilder feedback = new StringBuilder();
        
        // 分析代码特征
        CodeAnalysis analysis = analyzeCode(code, language);
        
        feedback.append("## 🤖 AI代码评测报告\n\n");
        
        if (accepted) {
            feedback.append("### 🎉 恭喜通过！\n");
            feedback.append("你的解答**完全正确**，所有测试用例都通过了！\n\n");
            
            feedback.append("### 📊 代码质量分析\n");
            feedback.append(String.format("- ✅ **正确性**: 完美 (%d/%d 测试用例通过)\n", 
                summary.passedCases, summary.totalCases));
            
            // 性能评估
            if (summary.avgRuntime < 50) {
                feedback.append("- ⚡ **执行效率**: 优秀 (平均 ").append(summary.avgRuntime).append("ms)\n");
            } else if (summary.avgRuntime < 200) {
                feedback.append("- 🚀 **执行效率**: 良好 (平均 ").append(summary.avgRuntime).append("ms)\n");
            } else {
                feedback.append("- 🐌 **执行效率**: 可优化 (平均 ").append(summary.avgRuntime).append("ms)\n");
            }
            
            // 代码风格评估
            feedback.append("- 📝 **代码风格**: ").append(analysis.getStyleRating()).append("\n");
            feedback.append("- 🧠 **算法复杂度**: ").append(analysis.getComplexityEstimate()).append("\n\n");
            
        } else {
            feedback.append("### ❌ 需要改进\n");
            feedback.append(String.format("你的解答通过了 **%d/%d** 个测试用例 (%.1f%%)\n\n", 
                summary.passedCases, summary.totalCases, 
                (double) summary.passedCases / summary.totalCases * 100));
            
            feedback.append("### 🔍 问题诊断\n");
            
            if (summary.passedCases == 0) {
                feedback.append("- 🚨 **基础逻辑错误**: 代码可能存在语法错误或基本逻辑问题\n");
            } else if (summary.passedCases < summary.totalCases / 2) {
                feedback.append("- ⚠️ **算法思路问题**: 核心算法可能需要重新思考\n");
            } else {
                feedback.append("- 🎯 **边界情况处理**: 大部分逻辑正确，注意特殊情况\n");
            }
            
            if (!summary.errors.isEmpty()) {
                feedback.append("- 💥 **主要错误类型**:\n");
                Set<String> uniqueErrors = new HashSet<>(summary.errors);
                for (String error : uniqueErrors) {
                    feedback.append("  - ").append(categorizeError(error)).append("\n");
                }
            }
            feedback.append("\n");
        }
        
        // 个性化建议
        feedback.append("### 💡 个性化建议\n");
        feedback.append(generatePersonalizedSuggestions(problem, code, language, analysis, accepted));
        
        // 学习路径
        feedback.append("\n### 📚 推荐学习\n");
        feedback.append(generateLearningPath(problem, accepted));
        
        return feedback.toString();
    }

    /**
     * 代码分析
     */
    private CodeAnalysis analyzeCode(String code, String language) {
        CodeAnalysis analysis = new CodeAnalysis();
        
        // 分析代码长度和复杂度
        int lineCount = code.split("\n").length;
        analysis.lineCount = lineCount;
        
        // 检查注释
        analysis.hasComments = code.contains("//") || code.contains("/*");
        
        // 检查变量命名
        analysis.hasGoodNaming = checkVariableNaming(code, language);
        
        // 估算时间复杂度
        analysis.estimatedComplexity = estimateTimeComplexity(code);
        
        return analysis;
    }

    /**
     * 生成个性化建议
     */
    private String generatePersonalizedSuggestions(LeetCodeProblem problem, String code, 
                                                 String language, CodeAnalysis analysis, boolean accepted) {
        StringBuilder suggestions = new StringBuilder();
        
        if (accepted) {
            suggestions.append("🌟 **进阶挑战**:\n");
            suggestions.append("- 尝试优化算法的时间复杂度\n");
            suggestions.append("- 考虑空间复杂度的优化方案\n");
            suggestions.append("- 用不同的算法思路重新实现\n");
            
            if (!analysis.hasComments) {
                suggestions.append("- 添加注释提高代码可读性\n");
            }
            
            if (analysis.lineCount > 50) {
                suggestions.append("- 考虑将复杂逻辑拆分成多个函数\n");
            }
        } else {
            suggestions.append("🎯 **改进方向**:\n");
            suggestions.append("- 先在纸上画出算法流程图\n");
            suggestions.append("- 用简单的例子手动验证算法逻辑\n");
            suggestions.append("- 添加调试输出跟踪程序执行过程\n");
            suggestions.append("- 仔细检查边界条件和特殊情况\n");
            
            if (!analysis.hasGoodNaming) {
                suggestions.append("- 使用更有意义的变量名\n");
            }
        }
        
        return suggestions.toString();
    }

    /**
     * 生成学习路径
     */
    private String generateLearningPath(LeetCodeProblem problem, boolean accepted) {
        StringBuilder path = new StringBuilder();
        
        String difficulty = problem.getDifficulty();
        
        if (accepted) {
            path.append("继续挑战相关题目:\n");
            if ("Easy".equalsIgnoreCase(difficulty)) {
                path.append("- 尝试同类型的中等难度题目\n");
                path.append("- 学习更高效的算法和数据结构\n");
            } else if ("Medium".equalsIgnoreCase(difficulty)) {
                path.append("- 挑战困难级别的相关题目\n");
                path.append("- 深入学习算法优化技巧\n");
            } else {
                path.append("- 你已经掌握了高难度题目！\n");
                path.append("- 可以尝试参加编程竞赛\n");
            }
        } else {
            path.append("建议学习顺序:\n");
            path.append("1. 复习相关的基础算法和数据结构\n");
            path.append("2. 练习类似的简单题目\n");
            path.append("3. 逐步提高难度\n");
            path.append("4. 重新挑战这道题\n");
        }
        
        return path.toString();
    }

    /**
     * 错误分类
     */
    private String categorizeError(String error) {
        if (error.contains("compile") || error.contains("syntax")) {
            return "语法错误 - 检查代码语法";
        } else if (error.contains("timeout") || error.contains("超时")) {
            return "执行超时 - 算法效率需要优化";
        } else if (error.contains("null") || error.contains("NullPointer")) {
            return "空指针异常 - 检查变量初始化";
        } else if (error.contains("index") || error.contains("bounds")) {
            return "数组越界 - 检查索引范围";
        } else {
            return "运行时错误 - " + error.substring(0, Math.min(50, error.length()));
        }
    }

    /**
     * 检查变量命名
     */
    private boolean checkVariableNaming(String code, String language) {
        // 简单的命名检查，实际可以更复杂
        return !code.matches(".*\\b[a-z]\\b.*") && // 避免单字母变量
               code.matches(".*[a-zA-Z]{2,}.*"); // 包含有意义的变量名
    }

    /**
     * 估算时间复杂度
     */
    private String estimateTimeComplexity(String code) {
        if (code.contains("for") && code.indexOf("for", code.indexOf("for") + 1) != -1) {
            return "O(n²) - 嵌套循环";
        } else if (code.contains("for") || code.contains("while")) {
            return "O(n) - 线性时间";
        } else {
            return "O(1) - 常数时间";
        }
    }

    /**
     * 代码分析结果类
     */
    private static class CodeAnalysis {
        int lineCount = 0;
        boolean hasComments = false;
        boolean hasGoodNaming = false;
        String estimatedComplexity = "O(1)";
        
        String getStyleRating() {
            int score = 0;
            if (hasComments) score++;
            if (hasGoodNaming) score++;
            if (lineCount < 30) score++;
            
            switch (score) {
                case 3: return "优秀 ⭐⭐⭐";
                case 2: return "良好 ⭐⭐";
                case 1: return "一般 ⭐";
                default: return "需改进";
            }
        }
        
        String getComplexityEstimate() {
            return estimatedComplexity;
        }
    }

    /**
     * 生成改进建议
     */
    private String generateImprovementSuggestions(LeetCodeProblem problem, String code, 
                                                String language, boolean accepted) {
        StringBuilder suggestions = new StringBuilder();
        
        if (accepted) {
            suggestions.append("- 考虑是否可以进一步优化时间复杂度\n");
            suggestions.append("- 检查代码的可读性和注释\n");
            suggestions.append("- 尝试用其他算法思路解决同一问题\n");
        } else {
            suggestions.append("- 仔细检查边界条件的处理\n");
            suggestions.append("- 确认算法逻辑是否正确\n");
            suggestions.append("- 可以先在纸上画出算法流程图\n");
            suggestions.append("- 建议添加调试输出来跟踪程序执行\n");
        }
        
        return suggestions.toString();
    }

    /**
     * 更新学生技能画像
     */
    private void updateStudentSkillProfile(Integer studentId, LeetCodeProblem problem, boolean success) {
        try {
            // 这里应该调用技能画像服务更新学生的掌握情况
            // skillProfileService.updateSkillByProblem(studentId, problem, success);
            logger.info("更新学生 {} 的技能画像，题目：{}，结果：{}", studentId, problem.getTitleMain(), success);
        } catch (Exception e) {
            logger.error("更新学生技能画像失败", e);
        }
    }

    /**
     * 生成技能提升建议
     */
    private List<String> generateSkillSuggestions(LeetCodeProblem problem, boolean accepted) {
        List<String> suggestions = new ArrayList<>();
        
        if (accepted) {
            suggestions.add("数组操作");
            suggestions.add("算法优化");
        } else {
            suggestions.add("基础语法");
            suggestions.add("逻辑思维");
            suggestions.add("边界处理");
        }
        
        return suggestions;
    }

    /**
     * 读取流内容
     */
    private String readStream(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    // 内部类
    private static class LanguageConfig {
        String name;
        String extension;
        String compiler;
        String runner;
        
        LanguageConfig(String name, String extension, String compiler, String runner) {
            this.name = name;
            this.extension = extension;
            this.compiler = compiler;
            this.runner = runner;
        }
        
        boolean needsCompilation() {
            return "java".equals(name) || "cpp".equals(name);
        }
    }

    private static class ExecutionResult {
        boolean success = false;
        String output = "";
        String error = "";
        long runtime = 0;
    }

    private static class TestCase {
        String input;
        String expectedOutput;
        
        TestCase(String input, String expectedOutput) {
            this.input = input;
            this.expectedOutput = expectedOutput;
        }
    }

    private static class ExecutionSummary {
        int totalCases = 0;
        int passedCases = 0;
        long avgRuntime = 0;
        List<String> errors = new ArrayList<>();
    }
}