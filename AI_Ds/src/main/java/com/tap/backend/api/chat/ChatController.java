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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tap-chat")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final String aiBaseUrl;
    private final String aiApiKey;
    private final ObjectMapper objectMapper;
    private final String model;
    private final QuotaService quotaService;
    private final PrincipalResolver principalResolver;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .build();

    public ChatController(AiProperties props, ObjectMapper objectMapper, QuotaService quotaService,
                           PrincipalResolver principalResolver) {
        this.objectMapper = objectMapper;
        this.quotaService = quotaService;
        this.principalResolver = principalResolver;
        AiProperties.OpenAi oa = props.openai();
        String apiKey = oa == null ? null : oa.apiKey();
        if (apiKey == null || apiKey.isBlank()) apiKey = System.getenv("OPENAI_API_KEY");
        this.aiApiKey = apiKey == null ? "" : apiKey.trim();
        String baseUrl = oa == null ? null : oa.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.openai.com/v1";
        this.aiBaseUrl = baseUrl;
        this.model = oa == null || oa.model() == null ? "deepseek-chat" : oa.model();
    }

    public record ChatRequest(
            @NotBlank @Size(max = 4000) String message,
            List<MessageItem> history
    ) {}

    public record MessageItem(String role, String content) {}

    @PostMapping
    public ApiResponse<Map<String, Object>> chat(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChatRequest req
    ) {
        var resolved = principalResolver.resolve(principal);
        quotaService.consumeAiRequests(resolved.userId(), 1);

        // Search arXiv if the message looks like a paper search
        String arxivContext = "";
        List<Map<String, String>> papers = List.of();
        if (looksLikeSearch(req.message())) {
            papers = searchArxiv(req.message());
            if (!papers.isEmpty()) {
                StringBuilder sb = new StringBuilder("\n\n[arXiv 搜索结果]\n");
                for (int i = 0; i < papers.size(); i++) {
                    var p = papers.get(i);
                    sb.append(String.format("%d. **%s**\n   作者: %s\n   链接: %s\n   摘要: %s\n\n",
                            i + 1, p.get("title"), p.get("authors"), p.get("link"),
                            truncate(p.get("summary"), 200)));
                }
                arxivContext = sb.toString();
            }
        }

        String systemPrompt = buildSystemPrompt(arxivContext);
        List<ObjectNode> messages = new ArrayList<>();
        messages.add(msg("system", systemPrompt));
        if (req.history() != null) {
            for (var h : req.history()) {
                if (h.role() != null && h.content() != null) {
                    messages.add(msg(h.role(), h.content()));
                }
            }
        }
        messages.add(msg("user", req.message()));

        String reply = callAi(messages);
        return ApiResponse.of(Maps.of(
                "reply", reply,
                "papers", papers,
                "model", model
        ));
    }

    private String buildSystemPrompt(String arxivContext) {
        return "你是「教师教辅平台」的 AI 助手，专门帮助大学教师进行学术研究。你的能力包括：\n"
                + "1. 论文检索：当用户想找论文时，基于 arXiv 搜索结果回答，给出论文标题、作者、链接\n"
                + "2. 论文解读：解释论文的核心方法、贡献和局限性\n"
                + "3. 学术问答：回答学术相关问题\n"
                + "4. 写作辅助：帮助润色学术文本\n\n"
                + "回答规则：\n"
                + "- 使用 Markdown 格式\n"
                + "- 论文链接使用 [标题](url) 格式，用户可以点击跳转\n"
                + "- 回答要专业但易懂\n"
                + "- 如果有搜索结果，优先基于搜索结果回答\n"
                + arxivContext;
    }

    private boolean looksLikeSearch(String msg) {
        String m = msg.toLowerCase();
        return m.contains("论文") || m.contains("paper") || m.contains("搜索") || m.contains("search")
                || m.contains("找") || m.contains("检索") || m.contains("arxiv") || m.contains("推荐")
                || m.contains("有哪些") || m.contains("最新") || m.contains("相关研究")
                || m.contains("文献") || m.contains("综述") || m.contains("survey");
    }

    private List<Map<String, String>> searchArxiv(String query) {
        try {
            // Extract search keywords using simple heuristic
            String keywords = query.replaceAll("[，。？！,\\.\\?!]", " ").trim();
            String encoded = URLEncoder.encode(keywords, StandardCharsets.UTF_8);
            String url = "https://export.arxiv.org/api/query?search_query=all:" + encoded
                    + "&start=0&max_results=5&sortBy=relevance&sortOrder=descending";

            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("User-Agent", "TAP/1.0").GET().build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) return List.of();

            var dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            var doc = dbf.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(resp.body()));
            var entries = doc.getElementsByTagName("entry");

            List<Map<String, String>> results = new ArrayList<>();
            for (int i = 0; i < entries.getLength() && i < 5; i++) {
                var entry = (org.w3c.dom.Element) entries.item(i);
                String title = xmlText(entry, "title").replaceAll("\\s+", " ").trim();
                String summary = xmlText(entry, "summary").replaceAll("\\s+", " ").trim();
                String id = xmlText(entry, "id").trim();
                // Extract authors
                var authorNodes = entry.getElementsByTagName("author");
                List<String> authors = new ArrayList<>();
                for (int j = 0; j < authorNodes.getLength() && j < 5; j++) {
                    authors.add(xmlText((org.w3c.dom.Element) authorNodes.item(j), "name"));
                }
                if (authorNodes.getLength() > 5) authors.add("et al.");
                String authorsStr = String.join(", ", authors);
                // Convert id to abstract page link
                String link = id.replace("http://", "https://");
                if (!title.isBlank()) {
                    results.add(Map.of("title", title, "authors", authorsStr,
                            "link", link, "summary", summary));
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("arXiv search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String callAi(List<ObjectNode> messages) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.set("messages", objectMapper.valueToTree(messages));
            body.put("temperature", 0.5);

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(aiBaseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + aiApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.error("AI chat HTTP {}: {}", resp.statusCode(), resp.body().substring(0, Math.min(500, resp.body().length())));
                return "抱歉，AI 服务返回错误码 " + resp.statusCode();
            }

            JsonNode root = objectMapper.readTree(resp.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("AI chat failed: {}", e.getMessage(), e);
            return "抱歉，AI 服务暂时不可用，请稍后重试。错误：" + e.getMessage();
        }
    }

    private ObjectNode msg(String role, String content) {
        return objectMapper.createObjectNode().put("role", role).put("content", content);
    }

    private String xmlText(org.w3c.dom.Element el, String tag) {
        var nl = el.getElementsByTagName(tag);
        return nl.getLength() == 0 ? "" : nl.item(0).getTextContent() == null ? "" : nl.item(0).getTextContent();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
