package com.tap.backend.service.ziporganize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tap.backend.ai.AiProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ZipOrganizeAiService {
  private final ObjectMapper objectMapper;
  private final String provider;
  private final String model;
  private final RestClient restClient;

  public ZipOrganizeAiService(AiProperties props, ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.provider = props.provider() == null ? "mock" : props.provider().trim().toLowerCase(Locale.ROOT);
    if ("openai".equals(this.provider)) {
      AiProperties.OpenAi oa = props.openai();
      String apiKey = oa == null ? null : oa.apiKey();
      String baseUrl = oa == null ? null : oa.baseUrl();
      this.model = oa == null || oa.model() == null || oa.model().isBlank() ? "deepseek-chat" : oa.model().trim();
      if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.deepseek.com/v1";
      this.restClient = RestClient.builder()
          .baseUrl(baseUrl)
          .defaultHeader("Authorization", "Bearer " + (apiKey == null ? "" : apiKey.trim()))
          .build();
    } else {
      this.model = "mock";
      this.restClient = null;
    }
  }

  public String provider() {
    return provider;
  }

  public String model() {
    return model;
  }

  public ItemAiResult analyzeItem(ItemAiInput input) {
    if (!"openai".equals(provider) || restClient == null) {
      return mockItem(input);
    }
    String prompt = """
        You organize teacher research archives.
        Analyze a single file and output strict JSON only.

        Allowed docType values: paper, slides, courseware, assignment, report, reference, unknown.
        Allowed paperCategory examples: NLP, CV, ML, DL, DataMining, IR, EduTech, Algorithm, System, Other.
        Allowed paperSubtype values: survey, method, experiment, application, teaching, general, unknown.

        Output schema:
        {
          "docType": "...",
          "paperCategory": "...",
          "paperSubtype": "...",
          "titleGuess": "...",
          "authorGuess": ["..."],
          "yearGuess": 2024,
          "keywords": ["...", "..."],
          "summaryZh": "120-180 Chinese characters",
          "suggestedFolder": "...",
          "suggestedFilename": "...",
          "confidence": 0.0
        }

        Constraints:
        - Return valid JSON, no markdown.
        - Preserve the original extension in suggestedFilename.
        - If uncertain, set confidence below 0.6 and use broad categories.
        - summaryZh must be Chinese.

        File path: %s
        File name: %s
        Extension: %s
        Extracted text sample:
        %s
        """.formatted(
        safe(input.path(), 400),
        safe(input.filename(), 200),
        safe(input.extension(), 40),
        safe(input.sampleText(), 8000)
    );
    return parseItem(chat(prompt), input);
  }

  public JobPlanResult planJob(JobPlanInput input) {
    if (!"openai".equals(provider) || restClient == null) {
      return mockPlan(input);
    }
    String prompt = """
        You organize a batch of teacher files into a clean zip structure.
        Based on the file analysis list, produce strict JSON only.

        Output schema:
        {
          "rootTopic": "...",
          "folderPlan": ["..."],
          "finalStructure": [
            {"originalPath":"...", "finalPath":"..."}
          ],
          "readmeSummary": "Chinese summary"
        }

        Constraints:
        - Return valid JSON, no markdown.
        - finalPath must include a filename with extension.
        - Keep files in broad folders if confidence is low.
        - Use concise English-safe path segments if needed.

        File analysis list:
        %s
        """.formatted(toJson(input.items()));
    return parsePlan(chat(prompt), input);
  }

  public record ItemAiInput(String path, String filename, String extension, String sampleText) {}

  public record ItemAiResult(
      String docType,
      String paperCategory,
      String paperSubtype,
      String titleGuess,
      List<String> authorGuess,
      Integer yearGuess,
      List<String> keywords,
      String summaryZh,
      String suggestedFolder,
      String suggestedFilename,
      Double confidence
  ) {}

  public record PlannedFile(String originalPath, String finalPath) {}

  public record JobPlanItem(
      String originalPath,
      String docType,
      String paperCategory,
      String paperSubtype,
      String titleGuess,
      List<String> keywords,
      String summaryZh,
      String suggestedFolder,
      String suggestedFilename,
      Double confidence
  ) {}

  public record JobPlanInput(List<JobPlanItem> items) {}

  public record JobPlanResult(
      String rootTopic,
      List<String> folderPlan,
      List<PlannedFile> finalStructure,
      String readmeSummary
  ) {}

  private String chat(String userPrompt) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("model", model);
      body.set("messages", objectMapper.valueToTree(List.of(
          msg("system", "You are a careful JSON-only assistant."),
          msg("user", userPrompt)
      )));
      body.put("temperature", 0.2);
      String resp = restClient.post()
          .uri("/chat/completions")
          .contentType(MediaType.APPLICATION_JSON)
          .body(objectMapper.writeValueAsString(body))
          .retrieve()
          .body(String.class);
      JsonNode root = objectMapper.readTree(resp);
      return root.path("choices").path(0).path("message").path("content").asText();
    } catch (Exception e) {
      throw new IllegalStateException("zip organize ai call failed", e);
    }
  }

  private ObjectNode msg(String role, String content) {
    return objectMapper.createObjectNode().put("role", role).put("content", content);
  }

  private ItemAiResult parseItem(String content, ItemAiInput input) {
    try {
      JsonNode node = parseJsonObject(content);
      List<String> authors = readStringList(node.get("authorGuess"));
      List<String> keywords = readStringList(node.get("keywords"));
      String filename = ZipOrganizeNaming.sanitizeFilename(
          readText(node, "suggestedFilename"),
          input.extension()
      );
      if (filename.isBlank()) filename = ZipOrganizeNaming.sanitizeFilename(input.filename(), input.extension());
      String folder = ZipOrganizeNaming.sanitizeFolderPath(readText(node, "suggestedFolder"));
      Double confidence = readDouble(node, "confidence");
      if (confidence == null) confidence = 0.5d;
      return new ItemAiResult(
          cleanEnum(readText(node, "docType"), "unknown"),
          cleanText(readText(node, "paperCategory"), "Other"),
          cleanEnum(readText(node, "paperSubtype"), "general"),
          cleanText(readText(node, "titleGuess"), ""),
          authors,
          readInt(node, "yearGuess"),
          keywords,
          cleanText(readText(node, "summaryZh"), ""),
          folder,
          filename,
          Math.max(0d, Math.min(1d, confidence))
      );
    } catch (Exception e) {
      return mockItem(input);
    }
  }

  private JobPlanResult parsePlan(String content, JobPlanInput input) {
    try {
      JsonNode node = parseJsonObject(content);
      List<String> folderPlan = readStringList(node.get("folderPlan"));
      List<PlannedFile> files = new ArrayList<>();
      JsonNode finalStructure = node.get("finalStructure");
      if (finalStructure != null && finalStructure.isArray()) {
        for (JsonNode item : finalStructure) {
          String originalPath = readText(item, "originalPath");
          String finalPath = readText(item, "finalPath");
          if (!originalPath.isBlank() && !finalPath.isBlank()) {
            files.add(new PlannedFile(originalPath, finalPath));
          }
        }
      }
      return new JobPlanResult(
          cleanText(readText(node, "rootTopic"), "Teacher Files"),
          folderPlan,
          files,
          cleanText(readText(node, "readmeSummary"), "")
      );
    } catch (Exception e) {
      return mockPlan(input);
    }
  }

  private JsonNode parseJsonObject(String content) throws Exception {
    String s = content == null ? "" : content.trim();
    int start = s.indexOf('{');
    int end = s.lastIndexOf('}');
    if (start >= 0 && end > start) s = s.substring(start, end + 1);
    return objectMapper.readTree(s.getBytes(StandardCharsets.UTF_8));
  }

  private static List<String> readStringList(JsonNode node) {
    List<String> out = new ArrayList<>();
    if (node == null || node.isNull()) return out;
    if (node.isArray()) {
      for (JsonNode item : node) {
        if (item == null || item.isNull()) continue;
        String value = item.asText("").trim();
        if (!value.isBlank()) out.add(value);
      }
      return out;
    }
    String single = node.asText("").trim();
    if (!single.isBlank()) out.add(single);
    return out;
  }

  private static String readText(JsonNode node, String field) {
    return node == null ? "" : node.path(field).asText("").trim();
  }

  private static Integer readInt(JsonNode node, String field) {
    if (node == null || !node.has(field) || node.get(field).isNull()) return null;
    JsonNode value = node.get(field);
    if (value.isInt() || value.isLong()) return value.asInt();
    try {
      return Integer.parseInt(value.asText("").trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Double readDouble(JsonNode node, String field) {
    if (node == null || !node.has(field) || node.get(field).isNull()) return null;
    JsonNode value = node.get(field);
    if (value.isDouble() || value.isFloat() || value.isNumber()) return value.asDouble();
    try {
      return Double.parseDouble(value.asText("").trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private ItemAiResult mockItem(ItemAiInput input) {
    String ext = input.extension() == null ? "" : input.extension().toLowerCase(Locale.ROOT);
    boolean paper = "pdf".equals(ext) || "docx".equals(ext) || "doc".equals(ext);
    String docType = paper ? "paper" : ("txt".equals(ext) ? "reference" : "unknown");
    String category = guessCategory(input.path() + " " + input.sampleText());
    String subtype = "paper".equals(docType) ? "general" : "unknown";
    String filename = ZipOrganizeNaming.sanitizeFilename(input.filename(), ext);
    String folder = ZipOrganizeNaming.defaultFolder(docType, category, subtype);
    return new ItemAiResult(
        docType,
        category,
        subtype,
        "",
        List.of(),
        null,
        List.of(),
        "",
        folder,
        filename,
        paper ? 0.72d : 0.45d
    );
  }

  private JobPlanResult mockPlan(JobPlanInput input) {
    List<PlannedFile> files = new ArrayList<>();
    List<String> folders = new ArrayList<>();
    for (JobPlanItem item : input.items()) {
      String folder = item.suggestedFolder();
      if (folder == null || folder.isBlank()) {
        folder = ZipOrganizeNaming.defaultFolder(item.docType(), item.paperCategory(), item.paperSubtype());
      }
      String finalPath = ZipOrganizeNaming.joinPath(folder, item.suggestedFilename());
      files.add(new PlannedFile(item.originalPath(), finalPath));
      if (!folder.isBlank() && !folders.contains(folder)) folders.add(folder);
    }
    return new JobPlanResult("Teacher Files", folders, files, "Files were organized by file type and topic.");
  }

  private static String guessCategory(String text) {
    String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
    if (lower.contains("nlp") || lower.contains("language") || lower.contains("transformer")) return "NLP";
    if (lower.contains("vision") || lower.contains("image") || lower.contains("detection")) return "CV";
    if (lower.contains("teach") || lower.contains("education") || lower.contains("curriculum")) return "EduTech";
    if (lower.contains("algorithm")) return "Algorithm";
    if (lower.contains("system") || lower.contains("distributed")) return "System";
    if (lower.contains("mining") || lower.contains("recommend")) return "DataMining";
    return "Other";
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      return "[]";
    }
  }

  private static String safe(String s, int maxChars) {
    String text = s == null ? "" : s.trim();
    return text.length() <= maxChars ? text : text.substring(0, maxChars);
  }

  private static String cleanText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static String cleanEnum(String value, String fallback) {
    String text = cleanText(value, fallback).toLowerCase(Locale.ROOT);
    return text.replaceAll("[^a-z_\\-]", "");
  }
}
