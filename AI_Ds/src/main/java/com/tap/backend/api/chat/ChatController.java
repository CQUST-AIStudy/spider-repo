package com.tap.backend.api.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tap.backend.ai.AiProperties;
import com.tap.backend.quota.QuotaService;
import com.tap.backend.security.PrincipalResolver;
import com.tap.backend.security.UserPrincipal;
import com.tap.common.api.ApiResponse;
import com.tap.common.api.Maps;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/tap-chat")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final String PAPERS_MARKER_PREFIX = "\n\n<!--PAPERS:";
    private static final String PAPERS_MARKER_SUFFIX = "-->";
    private static final int MAX_HISTORY = 10;
    private static final int MAX_PAPERS = 5;
    private static final Duration ARXIV_REQUEST_TIMEOUT = Duration.ofSeconds(6);
    private static final Duration AI_CHAT_REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration AI_STREAM_REQUEST_TIMEOUT = Duration.ofMinutes(5);

    private final String aiBaseUrl;
    private final String aiApiKey;
    private final String provider;
    private final ObjectMapper objectMapper;
    private final String model;
    private final QuotaService quotaService;
    private final PrincipalResolver principalResolver;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public ChatController(AiProperties props,
                          ObjectMapper objectMapper,
                          QuotaService quotaService,
                          PrincipalResolver principalResolver) {
        this.objectMapper = objectMapper;
        this.quotaService = quotaService;
        this.principalResolver = principalResolver;
        this.provider = props.provider() == null ? "mock" : props.provider().trim().toLowerCase();

        AiProperties.OpenAi openAi = props.openai();
        String apiKey = openAi == null ? null : openAi.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        this.aiApiKey = apiKey == null ? "" : apiKey.trim();

        String baseUrl = openAi == null ? null : openAi.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com/v1";
        }
        this.aiBaseUrl = baseUrl;
        this.model = openAi == null || openAi.model() == null ? "deepseek-chat" : openAi.model();
    }

    public record ChatRequest(
            @NotBlank @Size(max = 4000) String message,
            List<MessageItem> history
    ) {}

    public record MessageItem(String role, String content) {}

    private record ChatContext(List<ObjectNode> messages, List<Map<String, String>> papers) {}

    @PostMapping
    public ApiResponse<Map<String, Object>> chat(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChatRequest req
    ) {
        var resolved = principalResolver.resolve(principal);
        quotaService.consumeAiRequests(resolved.userId(), 1);

        ChatContext context = buildChatContext(req);
        String reply = callAi(context.messages());
        return ApiResponse.of(Maps.of(
                "reply", reply,
                "papers", context.papers(),
                "model", model
        ));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatStream(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChatRequest req
    ) {
        var resolved = principalResolver.resolve(principal);
        quotaService.consumeAiRequests(resolved.userId(), 1);

        ChatContext context = buildChatContext(req);
        StreamingResponseBody body = outputStream -> {
            streamAi(context.messages(), outputStream);
            writePapersMarker(outputStream, context.papers());
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    private ChatContext buildChatContext(ChatRequest req) {
        List<Map<String, String>> papers = looksLikeSearch(req.message()) ? searchArxiv(req.message()) : List.of();
        String systemPrompt = buildSystemPrompt(buildArxivContext(papers));

        List<ObjectNode> messages = new ArrayList<>();
        messages.add(msg("system", systemPrompt));
        if (req.history() != null) {
            int start = Math.max(0, req.history().size() - MAX_HISTORY);
            for (int i = start; i < req.history().size(); i++) {
                MessageItem item = req.history().get(i);
                if (item == null || item.role() == null || item.content() == null) {
                    continue;
                }
                messages.add(msg(item.role(), item.content()));
            }
        }
        messages.add(msg("user", req.message()));
        return new ChatContext(messages, papers);
    }

    private String buildSystemPrompt(String arxivContext) {
        return """
                你是“教师教学辅助平台”的 AI 助手，服务对象是高校教师。
                你的职责包括：
                1. 教学问答：帮助教师设计课堂讲解、实验安排、讨论题和作业反馈。
                2. 学术检索：如果问题涉及论文、综述或最新研究，优先参考 arXiv 检索结果。
                3. 写作辅助：润色教学文案、课程说明、评语和研究计划。

                回答要求：
                - 使用 Markdown
                - 内容专业、准确、可直接用于教学场景
                - 如果提供了论文检索结果，优先基于检索结果回答
                """
                + arxivContext;
    }

    private String buildArxivContext(List<Map<String, String>> papers) {
        if (papers == null || papers.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("\n\n[arXiv 搜索结果]\n");
        for (int i = 0; i < papers.size(); i++) {
            Map<String, String> paper = papers.get(i);
            sb.append(i + 1)
                    .append(". **").append(paper.getOrDefault("title", "")).append("**\n")
                    .append("   作者: ").append(paper.getOrDefault("authors", "")).append("\n")
                    .append("   链接: ").append(paper.getOrDefault("link", "")).append("\n")
                    .append("   摘要: ").append(truncate(paper.getOrDefault("summary", ""), 200)).append("\n\n");
        }
        return sb.toString();
    }

    private boolean looksLikeSearch(String msg) {
        if (msg == null || msg.isBlank()) {
            return false;
        }
        String normalized = msg.toLowerCase();
        return normalized.contains("论文")
                || normalized.contains("paper")
                || normalized.contains("arxiv")
                || normalized.contains("arivx")
                || normalized.contains("search")
                || normalized.contains("搜索")
                || normalized.contains("检索")
                || normalized.contains("综述")
                || normalized.contains("survey")
                || normalized.contains("sota")
                || normalized.contains("state of the art")
                || normalized.contains("最新")
                || normalized.contains("相关研究");
    }

    private List<Map<String, String>> searchArxiv(String query) {
        try {
            String keywords = query.replaceAll("[，。！？,.?!]", " ").trim();
            if (keywords.isBlank()) {
                return List.of();
            }
            String encoded = URLEncoder.encode(keywords, StandardCharsets.UTF_8);
            String url = "https://export.arxiv.org/api/query?search_query=all:" + encoded
                    + "&start=0&max_results=" + MAX_PAPERS
                    + "&sortBy=relevance&sortOrder=descending";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "TAP/1.0")
                    .timeout(ARXIV_REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                return List.of();
            }

            var dbf = DocumentBuilderFactory.newInstance();
            var doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(resp.body()));
            var entries = doc.getElementsByTagName("entry");

            List<Map<String, String>> results = new ArrayList<>();
            for (int i = 0; i < entries.getLength() && i < MAX_PAPERS; i++) {
                var entry = (org.w3c.dom.Element) entries.item(i);
                String title = xmlText(entry, "title").replaceAll("\\s+", " ").trim();
                if (title.isBlank()) {
                    continue;
                }

                String summary = xmlText(entry, "summary").replaceAll("\\s+", " ").trim();
                String id = xmlText(entry, "id").trim();
                var authorNodes = entry.getElementsByTagName("author");
                List<String> authors = new ArrayList<>();
                for (int j = 0; j < authorNodes.getLength() && j < MAX_PAPERS; j++) {
                    authors.add(xmlText((org.w3c.dom.Element) authorNodes.item(j), "name"));
                }
                if (authorNodes.getLength() > MAX_PAPERS) {
                    authors.add("et al.");
                }

                results.add(Map.of(
                        "title", title,
                        "authors", String.join(", ", authors),
                        "link", id.replace("http://", "https://"),
                        "summary", summary
                ));
            }
            return results;
        } catch (Exception e) {
            log.warn("arXiv search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String callAi(List<ObjectNode> messages) {
        String userPrompt = extractLastUserPrompt(messages);
        if ("mock".equals(provider) || aiApiKey.isBlank()) {
            return buildMockReply(userPrompt);
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(buildRequestBody(messages, false));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(aiBaseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + aiApiKey)
                    .header("Content-Type", "application/json")
                    .timeout(AI_CHAT_REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.error("AI chat HTTP {}: {}", resp.statusCode(),
                        resp.body().substring(0, Math.min(500, resp.body().length())));
                return "抱歉，AI 服务返回错误码 " + resp.statusCode();
            }

            JsonNode root = objectMapper.readTree(resp.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("AI chat failed: {}", e.getMessage(), e);
            return "抱歉，AI 服务暂时不可用，请稍后重试。错误：" + e.getMessage();
        }
    }

    private void streamAi(List<ObjectNode> messages, OutputStream outputStream) {
        String userPrompt = extractLastUserPrompt(messages);
        if ("mock".equals(provider) || aiApiKey.isBlank()) {
            streamText(outputStream, buildMockReply(userPrompt));
            return;
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(buildRequestBody(messages, true));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(aiBaseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + aiApiKey)
                    .header("Content-Type", "application/json")
                    .timeout(AI_STREAM_REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                String errBody;
                try (InputStream errStream = resp.body()) {
                    errBody = new String(errStream.readAllBytes(), StandardCharsets.UTF_8);
                }
                log.error("AI stream HTTP {}: {}", resp.statusCode(),
                        errBody.substring(0, Math.min(500, errBody.length())));
                streamText(outputStream, "抱歉，AI 服务返回错误码 " + resp.statusCode());
                return;
            }

            try (InputStream body = resp.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) {
                        continue;
                    }
                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    try {
                        JsonNode root = objectMapper.readTree(data);
                        String delta = extractDeltaContent(root);
                        if (!delta.isEmpty()) {
                            outputStream.write(delta.getBytes(StandardCharsets.UTF_8));
                            outputStream.flush();
                        }
                    } catch (Exception ignored) {
                        // Skip malformed chunks and keep the stream alive.
                    }
                }
            }
        } catch (Exception e) {
            log.error("AI stream failed: {}", e.getMessage(), e);
            streamText(outputStream, "抱歉，AI 服务暂时不可用，请稍后重试。");
        }
    }

    private ObjectNode buildRequestBody(List<ObjectNode> messages, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.set("messages", objectMapper.valueToTree(messages));
        body.put("temperature", 0.5);
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    private String extractDeltaContent(JsonNode root) {
        JsonNode choice = root.path("choices").path(0);
        JsonNode delta = choice.path("delta");
        if (delta.isTextual()) {
            return delta.asText("");
        }

        JsonNode content = delta.path("content");
        if (content.isTextual()) {
            return content.asText("");
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : content) {
                if ("text".equals(item.path("type").asText())) {
                    sb.append(item.path("text").asText(""));
                }
            }
            return sb.toString();
        }
        return "";
    }

    private String extractLastUserPrompt(List<ObjectNode> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode item = messages.get(i);
            if ("user".equals(item.path("role").asText())) {
                return item.path("content").asText("");
            }
        }
        return "";
    }

    private String buildMockReply(String prompt) {
        String normalized = prompt == null ? "" : prompt.toLowerCase();

        if (normalized.contains("ppt") || normalized.contains("课件")) {
            return """
                    ## 课件建议
                    1. 开场先明确教学目标、先修知识和课堂产出。
                    2. 中段用 1 个核心例题拆解概念、流程和边界条件。
                    3. 结尾安排 1 个课堂练习和 1 个课后延伸任务。

                    ## 页面结构
                    - 第 1 页：主题、目标、适用班级
                    - 第 2 页：核心概念与术语
                    - 第 3 页：示例分析
                    - 第 4 页：常见错误
                    - 第 5 页：练习与总结
                    """;
        }

        if (looksLikeSearch(normalized)) {
            return """
                    ## 检索说明
                    已按你的问题补充论文检索结果。你可以继续指定：
                    - 研究主题
                    - 时间范围
                    - 希望得到的输出形式，例如综述、课堂应用或实验设计
                    """;
        }

        return """
                ## 教学助手回复
                我可以继续帮你处理这些任务：
                - 设计课堂讲解提纲
                - 生成实验或作业说明
                - 润色教学反馈
                - 整理论文检索结果

                如果你希望回答更贴近场景，请补充课程名称、学生层次和期望输出形式。
                """;
    }

    private void streamText(OutputStream outputStream, String text) {
        try {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (Exception ignored) {
            // Client may already be disconnected.
        }
    }

    private void writePapersMarker(OutputStream outputStream, List<Map<String, String>> papers) {
        try {
            String marker = PAPERS_MARKER_PREFIX + objectMapper.writeValueAsString(papers) + PAPERS_MARKER_SUFFIX;
            outputStream.write(marker.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (Exception e) {
            log.warn("write papers marker failed: {}", e.getMessage());
        }
    }

    private ObjectNode msg(String role, String content) {
        return objectMapper.createObjectNode().put("role", role).put("content", content);
    }

    private String xmlText(org.w3c.dom.Element el, String tag) {
        var nl = el.getElementsByTagName(tag);
        if (nl.getLength() == 0 || nl.item(0).getTextContent() == null) {
            return "";
        }
        return nl.item(0).getTextContent();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
