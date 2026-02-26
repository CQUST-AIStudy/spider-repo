package com.tap.backend.rag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tap.backend.ai.AiProperties;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class IntentClassifyService {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifyService.class);
    private static final Set<String> VALID_INTENTS = Set.of("debug", "procedure", "concept", "summary", "paper");
    private static final Set<String> INTEGRITY_KEYWORDS = Set.of(
            "帮我写完整代码", "帮我写代码", "帮我完成作业", "帮我写实验报告",
            "直接给我答案", "帮我做", "替我写", "帮我交作业"
    );

    private final AiProperties aiProps;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public IntentClassifyService(AiProperties aiProps) {
        this.aiProps = aiProps;
    }

    public record IntentResult(String intentType, boolean academicIntegrityViolation) {}

    public IntentResult classify(String query) {
        // Quick academic integrity check via keywords
        boolean integrityViolation = INTEGRITY_KEYWORDS.stream()
                .anyMatch(kw -> query.contains(kw));

        try {
            String prompt = buildClassifyPrompt(query);
            JsonObject reqBody = new JsonObject();
            reqBody.addProperty("model", aiProps.openai().model());
            reqBody.addProperty("temperature", 0.1);

            JsonArray messages = new JsonArray();
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", prompt);
            messages.add(sysMsg);
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", query);
            messages.add(userMsg);
            reqBody.add("messages", messages);

            RequestBody okBody = RequestBody.create(
                    reqBody.toString(), MediaType.parse("application/json; charset=utf-8"));
            Request okReq = new Request.Builder()
                    .url(aiProps.openai().baseUrl() + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + aiProps.openai().apiKey())
                    .post(okBody).build();

            try (Response resp = httpClient.newCall(okReq).execute()) {
                if (!resp.isSuccessful()) {
                    log.warn("[IntentClassify] API error: {}", resp.code());
                    return new IntentResult("concept", integrityViolation);
                }
                String respStr = resp.body().string();
                JsonObject json = JsonParser.parseString(respStr).getAsJsonObject();
                String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                        .getAsJsonObject("message").get("content").getAsString().trim().toLowerCase();

                // Parse intent from response
                for (String intent : VALID_INTENTS) {
                    if (content.contains(intent)) {
                        if (content.contains("academic_integrity") || content.contains("代写")) {
                            integrityViolation = true;
                        }
                        return new IntentResult(intent, integrityViolation);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[IntentClassify] failed, defaulting to concept", e);
        }
        return new IntentResult("concept", integrityViolation);
    }

    private String buildClassifyPrompt(String query) {
        return """
                你是一个意图分类器。请将学生的提问分类为以下意图之一，只输出意图名称：
                - debug: 调试代码、修复bug、代码报错
                - procedure: 操作步骤、实验流程、如何实现
                - concept: 概念理解、定义解释、原理说明
                - summary: 总结归纳、章节复习、知识梳理
                - paper: 论文相关、学术文献
                
                如果检测到学生要求代写（如"帮我写完整代码"、"帮我写实验报告"），请在意图后追加 academic_integrity 标记。
                
                示例：
                "链表和数组有什么区别？" → concept
                "我的代码运行报空指针异常" → debug
                "实验三的步骤是什么？" → procedure
                "帮我总结第五章的内容" → summary
                "帮我写完整的排序算法代码" → debug academic_integrity
                
                只输出分类结果，不要解释。
                """;
    }
}
