package com.cqust.ai_server.service.impl;

import com.cqust.ai_server.entity.LeetCodeProblem;
import com.cqust.ai_server.service.LeetCodeExecutionService;
import com.cqust.ai_server.service.LeetCodeProblemService;
import com.cqust.ai_server.service.StudentSkillProfileService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class LeetCodeExecutionServiceImpl implements LeetCodeExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(LeetCodeExecutionServiceImpl.class);
    private static final int AI_ESTIMATED_TOTAL_CASES = 10;
    private static final int DEFAULT_AI_CONNECT_TIMEOUT_MS = 8000;
    private static final int DEFAULT_AI_READ_TIMEOUT_MS = 60000;
    private static final int DEFAULT_AI_EVAL_TIMEOUT_SECONDS = 55;
    private static final ExecutorService AI_EVAL_EXECUTOR = Executors.newFixedThreadPool(2);

    private static final Map<String, LanguageConfig> LANGUAGE_CONFIGS = new HashMap<>();

    static {
        LANGUAGE_CONFIGS.put("java", new LanguageConfig("java", ".java", "Solution.java", true));
        LANGUAGE_CONFIGS.put("python", new LanguageConfig("python", ".py", "solution.py", false));
        LANGUAGE_CONFIGS.put("c", new LanguageConfig("c", ".c", "solution.c", true));
        LANGUAGE_CONFIGS.put("cpp", new LanguageConfig("cpp", ".cpp", "solution.cpp", true));
        LANGUAGE_CONFIGS.put("javascript", new LanguageConfig("javascript", ".js", "solution.js", false));
    }

    private final LeetCodeProblemService problemService;
    @SuppressWarnings("unused")
    private final StudentSkillProfileService skillProfileService;
    private final ObjectMapper objectMapper;
    private final RestClient aiRestClient;
    private final String aiModel;
    private final int aiConnectTimeoutMs;
    private final int aiReadTimeoutMs;
    private final int aiEvalTimeoutSeconds;

    @Autowired
    public LeetCodeExecutionServiceImpl(
            LeetCodeProblemService problemService,
            StudentSkillProfileService skillProfileService,
            ObjectMapper objectMapper,
            @Value("${tap.ai.openai.base-url:https://api.deepseek.com/v1}") String aiBaseUrl,
            @Value("${tap.ai.openai.api-key:}") String aiApiKey,
            @Value("${tap.ai.openai.model:deepseek-chat}") String aiModel,
            @Value("${leetcode.ai.connect-timeout-ms:" + DEFAULT_AI_CONNECT_TIMEOUT_MS + "}") int aiConnectTimeoutMs,
            @Value("${leetcode.ai.read-timeout-ms:" + DEFAULT_AI_READ_TIMEOUT_MS + "}") int aiReadTimeoutMs,
            @Value("${leetcode.ai.eval-timeout-seconds:" + DEFAULT_AI_EVAL_TIMEOUT_SECONDS + "}") int aiEvalTimeoutSeconds) {
        this.problemService = problemService;
        this.skillProfileService = skillProfileService;
        this.objectMapper = objectMapper;
        this.aiModel = (aiModel == null || aiModel.isBlank()) ? "deepseek-chat" : aiModel.trim();
        this.aiConnectTimeoutMs = aiConnectTimeoutMs > 0 ? aiConnectTimeoutMs : DEFAULT_AI_CONNECT_TIMEOUT_MS;
        this.aiReadTimeoutMs = aiReadTimeoutMs > 0 ? aiReadTimeoutMs : DEFAULT_AI_READ_TIMEOUT_MS;
        this.aiEvalTimeoutSeconds = aiEvalTimeoutSeconds > 0 ? aiEvalTimeoutSeconds : DEFAULT_AI_EVAL_TIMEOUT_SECONDS;

        String effectiveApiKey = (aiApiKey == null || aiApiKey.isBlank())
                ? System.getenv("OPENAI_API_KEY")
                : aiApiKey.trim();
        if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
            this.aiRestClient = null;
        } else {
            this.aiRestClient = RestClient.builder()
                    .baseUrl((aiBaseUrl == null || aiBaseUrl.isBlank()) ? "https://api.deepseek.com/v1" : aiBaseUrl.trim())
                    .defaultHeader("Authorization", "Bearer " + effectiveApiKey)
                    .requestFactory(createAiRequestFactory())
                    .build();
        }
    }

    private SimpleClientHttpRequestFactory createAiRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiConnectTimeoutMs);
        factory.setReadTimeout(aiReadTimeoutMs);
        return factory;
    }

    @Override
    public Map<String, Object> runCode(Long problemId, String code, String language, String testInput) {
        Map<String, Object> result = new HashMap<>();

        try {
            LeetCodeProblem problem = problemService.findById(problemId);
            if (problem == null) {
                result.put("status", "error");
                result.put("output", "Problem not found");
                return result;
            }

            String normalizedLanguage = normalizeLanguage(language);
            LanguageConfig config = LANGUAGE_CONFIGS.get(normalizedLanguage);
            if (config == null) {
                result.put("status", "error");
                result.put("output", "Unsupported language: " + language);
                return result;
            }

            ExecutionResult execResult = executeCode(code, normalizedLanguage, testInput);
            result.put("status", execResult.success ? "success" : "error");
            result.put("output", execResult.output);
            result.put("error", execResult.error);
            result.put("runtime", execResult.runtime + "ms");
        } catch (Exception e) {
            logger.error("Run code failed", e);
            result.put("status", "error");
            result.put("output", "Run failed: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> submitSolution(Integer studentId, Long problemId, String code, String language) {
        Map<String, Object> result = new HashMap<>();

        try {
            String normalizedLanguage = normalizeLanguage(language);
            if (!LANGUAGE_CONFIGS.containsKey(normalizedLanguage)) {
                result.put("accepted", false);
                result.put("status", "failed");
                result.put("message", "Unsupported language: " + language);
                return result;
            }
            if (code == null || code.trim().isEmpty()) {
                result.put("accepted", false);
                result.put("status", "failed");
                result.put("message", "Code cannot be empty");
                return result;
            }

            LeetCodeProblem problem = problemService.findById(problemId);
            if (problem == null) {
                result.put("accepted", false);
                result.put("status", "failed");
                result.put("message", "Problem not found");
                return result;
            }

            // AI-only evaluation path: no internal test-case judging.
            AIEvaluation evaluation = evaluateByAi(problem, code, normalizedLanguage);
            boolean accepted = evaluation.accepted;
            int score = clampScore(evaluation.score);
            int passedCases = (int) Math.round(clamp01(evaluation.estimatedPassRate) * AI_ESTIMATED_TOTAL_CASES);

            if (!evaluation.unavailable) {
                updateStudentSkillProfile(studentId, problem, accepted);
            }

            result.put("accepted", accepted);
            result.put("status", evaluation.unavailable ? "unavailable" : (accepted ? "success" : "failed"));
            result.put("score", evaluation.unavailable ? null : score);
            result.put("aiFeedback", evaluation.feedback);

            Map<String, Object> details = new HashMap<>();
            details.put("passedCases", evaluation.unavailable ? 0 : passedCases);
            details.put("totalCases", evaluation.unavailable ? 0 : AI_ESTIMATED_TOTAL_CASES);
            details.put("runtime", "AI static review");
            details.put("memory", "N/A");
            details.put("confidence", String.format(Locale.ROOT, "%.0f%%", clamp01(evaluation.confidence) * 100));
            if (!evaluation.riskNotes.isEmpty()) {
                details.put("error", String.join("\n", evaluation.riskNotes));
            }
            result.put("details", details);
            result.put("skillSuggestions", evaluation.skillSuggestions.isEmpty()
                    ? defaultSkillSuggestions(accepted)
                    : evaluation.skillSuggestions);

        } catch (Exception e) {
            logger.error("Submit solution failed", e);
            result.put("accepted", false);
            result.put("status", "failed");
            result.put("message", "Submit failed: " + e.getMessage());
        }

        return result;
    }

    private String normalizeLanguage(String language) {
        if (language == null) {
            return "";
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if ("c++".equals(normalized) || "cplusplus".equals(normalized)) {
            return "cpp";
        }
        return normalized;
    }

    private AIEvaluation evaluateByAi(LeetCodeProblem problem, String code, String language) {
        if (aiRestClient == null) {
            return buildFallbackEvaluation("AI service is not configured. Set tap.ai.openai.api-key first.");
        }

        Future<AIEvaluation> future = AI_EVAL_EXECUTOR.submit(() -> {
            String prompt = buildAiEvaluationPrompt(problem, code, language);
            String content = callChatCompletions(prompt);
            return parseAiEvaluation(content);
        });

        try {
            return future.get(aiEvalTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException timeoutException) {
            future.cancel(true);
            logger.warn("AI evaluation timed out after {}s", aiEvalTimeoutSeconds);
            return buildFallbackEvaluation("AI request timed out after " + aiEvalTimeoutSeconds + " seconds");
        } catch (Exception e) {
            future.cancel(true);
            logger.warn("AI evaluation failed, fallback used: {}", e.getMessage());
            return buildFallbackEvaluation("AI request failed: " + e.getMessage());
        }
    }

    private String buildAiEvaluationPrompt(LeetCodeProblem problem, String code, String language) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a strict LeetCode reviewer. Do NOT execute code.\n");
        prompt.append("Use static reasoning only and return JSON only, no markdown.\n");
        prompt.append("Scoring rubric (0-100): correctness 60, edge-cases 20, complexity 10, code quality 10.\n");
        prompt.append("Output schema:\n");
        prompt.append("{");
        prompt.append("\"accepted\":true|false,");
        prompt.append("\"score\":0-100,");
        prompt.append("\"confidence\":0-1,");
        prompt.append("\"estimated_pass_rate\":0-1,");
        prompt.append("\"summary\":\"one sentence\",");
        prompt.append("\"feedback_markdown\":\"detailed markdown feedback in Chinese\",");
        prompt.append("\"strengths\":[\"...\"],");
        prompt.append("\"issues\":[\"...\"],");
        prompt.append("\"skill_suggestions\":[\"...\"],");
        prompt.append("\"risk_notes\":[\"...\"]");
        prompt.append("}\n\n");
        prompt.append("Problem Title: ").append(safeText(problem.getTitleMain(), 200)).append("\n");
        prompt.append("Difficulty: ").append(safeText(problem.getDifficulty(), 40)).append("\n");
        prompt.append("Language: ").append(language).append("\n\n");
        prompt.append("Problem Statement:\n").append(safeText(problem.getProblemText(), 7000)).append("\n\n");
        prompt.append("Official Solution Reference:\n").append(safeText(problem.getSolutionText(), 5000)).append("\n\n");
        prompt.append("Student Code:\n").append(safeText(code, 12000)).append("\n\n");
        prompt.append("Constraints:\n");
        prompt.append("- Prefer concrete bug findings and edge-cases.\n");
        prompt.append("- If confidence < 0.6, keep accepted=false unless code is clearly correct.\n");
        prompt.append("- Use concise Chinese in feedback_markdown.\n");
        return prompt.toString();
    }

    private String callChatCompletions(String prompt) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", aiModel);
        body.put("temperature", 0.2);
        body.put("max_tokens", 800);
        body.set("messages", objectMapper.valueToTree(List.of(
                messageNode("system", "You are a senior programming reviewer. Output valid JSON only."),
                messageNode("user", prompt)
        )));

        String raw = aiRestClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(raw);
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private ObjectNode messageNode(String role, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        node.put("content", content);
        return node;
    }

    private AIEvaluation parseAiEvaluation(String content) {
        if (content == null || content.isBlank()) {
            return buildFallbackEvaluation("AI returned empty content.");
        }

        try {
            String normalized = content.trim();
            if (normalized.startsWith("```")) {
                int firstLine = normalized.indexOf('\n');
                int lastFence = normalized.lastIndexOf("```");
                if (firstLine > 0 && lastFence > firstLine) {
                    normalized = normalized.substring(firstLine + 1, lastFence).trim();
                }
            }
            int start = normalized.indexOf('{');
            int end = normalized.lastIndexOf('}');
            if (start >= 0 && end > start) {
                normalized = normalized.substring(start, end + 1);
            }

            JsonNode n = objectMapper.readTree(normalized);
            AIEvaluation evaluation = new AIEvaluation();
            evaluation.accepted = n.path("accepted").asBoolean(false);
            evaluation.score = clampScore(n.path("score").asInt(evaluation.accepted ? 85 : 55));
            evaluation.confidence = clamp01(n.path("confidence").asDouble(evaluation.accepted ? 0.8 : 0.6));
            evaluation.estimatedPassRate = clamp01(n.path("estimated_pass_rate").asDouble(evaluation.accepted ? 0.85 : 0.5));

            String summary = n.path("summary").asText("");
            String feedback = n.path("feedback_markdown").asText("");
            List<String> strengths = toStringList(n.path("strengths"));
            List<String> issues = toStringList(n.path("issues"));
            List<String> suggestions = toStringList(n.path("skill_suggestions"));
            List<String> risks = toStringList(n.path("risk_notes"));

            if (feedback.isBlank()) {
                StringBuilder autoFeedback = new StringBuilder();
                autoFeedback.append("## AI Review Summary\n");
                autoFeedback.append("- Verdict: ").append(evaluation.accepted ? "Likely accepted (static reasoning)" : "Not accepted (static reasoning)").append("\n");
                if (!summary.isBlank()) {
                    autoFeedback.append("- Summary: ").append(summary).append("\n");
                }
                if (!strengths.isEmpty()) {
                    autoFeedback.append("\n### Strengths\n");
                    strengths.forEach(item -> autoFeedback.append("- ").append(item).append("\n"));
                }
                if (!issues.isEmpty()) {
                    autoFeedback.append("\n### Issues\n");
                    issues.forEach(item -> autoFeedback.append("- ").append(item).append("\n"));
                }
                if (!suggestions.isEmpty()) {
                    autoFeedback.append("\n### Improvement Suggestions\n");
                    suggestions.forEach(item -> autoFeedback.append("- ").append(item).append("\n"));
                }
                feedback = autoFeedback.toString();
            }

            evaluation.feedback = feedback;
            evaluation.skillSuggestions = suggestions;
            evaluation.riskNotes = risks;
            return evaluation;
        } catch (Exception e) {
            return buildFallbackEvaluation("AI JSON parse failed: " + e.getMessage());
        }
    }

    private AIEvaluation buildFallbackEvaluation(String reason) {
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.unavailable = true;
        evaluation.accepted = false;
        evaluation.score = 0;
        evaluation.confidence = 0.0;
        evaluation.estimatedPassRate = 0.0;
        evaluation.feedback = "## AI Review Unavailable\n"
                + "- Reason: " + reason + "\n"
                + "- This is not a final grading result.\n"
                + "- Please retry later or check AI API configuration.";
        evaluation.skillSuggestions = new ArrayList<>(List.of(
                "Please retry once AI service recovers",
                "Verify API key and outbound network policy",
                "Switch to a faster model if needed"
        ));
        evaluation.riskNotes = new ArrayList<>(List.of(reason));
        return evaluation;
    }

    private List<String> toStringList(JsonNode node) {
        List<String> items = new ArrayList<>();
        if (node == null || node.isMissingNode()) {
            return items;
        }
        if (node.isArray()) {
            node.forEach(item -> {
                String text = item.asText("").trim();
                if (!text.isEmpty()) {
                    items.add(text);
                }
            });
        } else {
            String text = node.asText("").trim();
            if (!text.isEmpty()) {
                items.add(text);
            }
        }
        return items;
    }

    private List<String> defaultSkillSuggestions(boolean accepted) {
        if (accepted) {
            return new ArrayList<>(List.of("Algorithm optimization", "Code readability", "Boundary-case verification"));
        }
        return new ArrayList<>(List.of("Language fundamentals", "Boundary-case handling", "Complexity analysis"));
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private String safeText(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }

    private ExecutionResult executeCode(String code, String language, String input) {
        ExecutionResult result = new ExecutionResult();
        LanguageConfig config = LANGUAGE_CONFIGS.get(language);
        if (config == null) {
            result.success = false;
            result.error = "Unsupported language: " + language;
            return result;
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("leetcode_exec_");
            Path sourceFile = tempDir.resolve(config.sourceFileName);
            Files.write(sourceFile, code.getBytes(StandardCharsets.UTF_8));

            long start = System.currentTimeMillis();
            String executableName = isWindows() ? "solution.exe" : "solution";

            if (config.needsCompilation) {
                ProcessBuilder compileBuilder = new ProcessBuilder();
                compileBuilder.directory(tempDir.toFile());
                if ("java".equals(language)) {
                    compileBuilder.command("javac", config.sourceFileName);
                } else if ("c".equals(language)) {
                    compileBuilder.command("gcc", "-std=c11", "-O2", "-o", executableName, config.sourceFileName);
                } else if ("cpp".equals(language)) {
                    compileBuilder.command("g++", "-std=c++17", "-O2", "-o", executableName, config.sourceFileName);
                }

                Process compileProcess = compileBuilder.start();
                int compileExitCode = compileProcess.waitFor();
                if (compileExitCode != 0) {
                    result.success = false;
                    result.error = readStream(compileProcess.getErrorStream());
                    return result;
                }
            }

            ProcessBuilder runBuilder = new ProcessBuilder();
            runBuilder.directory(tempDir.toFile());
            if ("java".equals(language)) {
                runBuilder.command("java", "Solution");
            } else if ("python".equals(language)) {
                runBuilder.command("python", config.sourceFileName);
            } else if ("c".equals(language) || "cpp".equals(language)) {
                runBuilder.command(isWindows() ? executableName : "./" + executableName);
            } else if ("javascript".equals(language)) {
                runBuilder.command("node", config.sourceFileName);
            }

            Process runProcess = runBuilder.start();
            if (input != null && !input.trim().isEmpty()) {
                try (PrintWriter writer = new PrintWriter(runProcess.getOutputStream())) {
                    writer.println(input);
                    writer.flush();
                }
            }

            boolean finished = runProcess.waitFor(6, TimeUnit.SECONDS);
            if (!finished) {
                runProcess.destroyForcibly();
                result.success = false;
                result.error = "Execution timeout";
                return result;
            }

            result.runtime = System.currentTimeMillis() - start;
            int exitCode = runProcess.exitValue();
            if (exitCode == 0) {
                result.success = true;
                result.output = readStream(runProcess.getInputStream());
            } else {
                result.success = false;
                String err = readStream(runProcess.getErrorStream());
                String out = readStream(runProcess.getInputStream());
                result.error = (err == null || err.isBlank()) ? out : err;
            }
        } catch (Exception e) {
            result.success = false;
            result.error = "Execution error: " + e.getMessage();
        } finally {
            if (tempDir != null) {
                deleteDirectory(tempDir.toFile());
            }
        }

        return result;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String readStream(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private void deleteDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        directory.delete();
    }

    private void updateStudentSkillProfile(Integer studentId, LeetCodeProblem problem, boolean success) {
        logger.info("Update skill profile studentId={} problemId={} success={}",
                studentId,
                problem == null ? null : problem.getId(),
                success);
    }

    private static class LanguageConfig {
        final String name;
        final String extension;
        final String sourceFileName;
        final boolean needsCompilation;

        private LanguageConfig(String name, String extension, String sourceFileName, boolean needsCompilation) {
            this.name = name;
            this.extension = extension;
            this.sourceFileName = sourceFileName;
            this.needsCompilation = needsCompilation;
        }
    }

    private static class ExecutionResult {
        boolean success = false;
        String output = "";
        String error = "";
        long runtime = 0;
    }

    private static class AIEvaluation {
        boolean unavailable;
        boolean accepted;
        int score;
        double confidence;
        double estimatedPassRate;
        String feedback = "";
        List<String> skillSuggestions = new ArrayList<>();
        List<String> riskNotes = new ArrayList<>();
    }
}
