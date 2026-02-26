package com.cqust.ai_server.controller;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 学生AI学习助手 — 调用 DeepSeek API (流式)
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class DeepSeekChatController {

    @Value("${tap.ai.openai.api-key:}")
    private String apiKey;

    @Value("${tap.ai.openai.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${tap.ai.openai.model:deepseek-chat}")
    private String model;

    private static final Gson gson = new Gson();

    private static final String SYSTEM_PROMPT =
        "你是一个专业的数据结构与算法学习助手，专注于帮助大学生解决数据结构课程中的学习问题。" +
        "你擅长：解释数据结构概念（链表、栈、队列、树、图、哈希表等）、分析算法复杂度、" +
        "帮助调试C/C++代码、提供解题思路和最佳实践建议。" +
        "回答要简洁、准确、有针对性，适当使用代码示例。使用中文回答。";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chat(@RequestBody Map<String, String> request) {
        String userInput = request.get("userInput");
        System.out.println("[DeepSeek] 收到请求: " + userInput);

        if (userInput == null || userInput.trim().isEmpty()) {
            return ResponseEntity.ok().body(out -> {
                out.write("请输入有效的问题".getBytes(StandardCharsets.UTF_8));
                out.flush();
            });
        }

        StreamingResponseBody body = outputStream -> {
            try {
                // 构建请求体
                JsonObject reqBody = new JsonObject();
                reqBody.addProperty("model", model);
                reqBody.addProperty("stream", true);

                JsonArray messages = new JsonArray();
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", SYSTEM_PROMPT);
                messages.add(sysMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", userInput);
                messages.add(userMsg);

                reqBody.add("messages", messages);

                okhttp3.RequestBody okBody = okhttp3.RequestBody.create(
                    reqBody.toString(),
                    okhttp3.MediaType.parse("application/json; charset=utf-8")
                );

                Request okReq = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(okBody)
                    .build();

                try (Response response = httpClient.newCall(okReq).execute()) {
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "unknown";
                        System.err.println("[DeepSeek] API错误: " + response.code() + " " + errBody);
                        outputStream.write(("AI服务暂时不可用，错误码: " + response.code()).getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                        return;
                    }

                    ResponseBody respBody = response.body();
                    if (respBody == null) return;

                    boolean inThinking = false;
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(respBody.byteStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data: ")) continue;
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) break;

                            try {
                                JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();
                                JsonArray choices = chunk.getAsJsonArray("choices");
                                if (choices == null || choices.isEmpty()) continue;
                                JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
                                if (delta == null) continue;

                                // 处理 reasoning_content（思考过程，跳过）
                                if (delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull()) {
                                    // 跳过思考内容，不输出给前端
                                    continue;
                                }

                                // 输出正式内容
                                if (delta.has("content") && !delta.get("content").isJsonNull()) {
                                    String content = delta.get("content").getAsString();
                                    outputStream.write(content.getBytes(StandardCharsets.UTF_8));
                                    outputStream.flush();
                                }
                            } catch (Exception e) {
                                // 解析单行失败，跳过
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[DeepSeek] 异常: " + e.getMessage());
                try {
                    outputStream.write("抱歉，AI助手暂时无法回答，请稍后再试。".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } catch (Exception ignored) {}
            }
        };

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .header("Cache-Control", "no-cache")
            .header("X-Accel-Buffering", "no")
            .body(body);
    }
}
