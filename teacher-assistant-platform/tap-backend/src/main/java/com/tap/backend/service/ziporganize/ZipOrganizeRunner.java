package com.tap.backend.service.ziporganize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tap.backend.domain.ziporganize.ZipOrganizeExtractStatus;
import com.tap.backend.domain.ziporganize.ZipOrganizeItemEntity;
import com.tap.backend.domain.ziporganize.ZipOrganizeJobEntity;
import com.tap.backend.domain.ziporganize.ZipOrganizeJobStatus;
import com.tap.backend.infra.crypto.Digests;
import com.tap.backend.infra.storage.ObjectStorageService;
import com.tap.backend.infra.text.FileTextExtractor;
import com.tap.backend.quota.QuotaService;
import com.tap.backend.repo.ZipOrganizeItemRepository;
import com.tap.backend.repo.ZipOrganizeJobRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class ZipOrganizeRunner {
  private final ZipOrganizeJobRepository jobRepository;
  private final ZipOrganizeItemRepository itemRepository;
  private final ObjectStorageService objectStorageService;
  private final FileTextExtractor fileTextExtractor;
  private final ZipOrganizeAiService aiService;
  private final ZipPackService zipPackService;
  private final ZipOrganizeProperties props;
  private final ObjectMapper objectMapper;
  private final ExecutorService zipOrganizeItemExecutor;
  private final QuotaService quotaService;

  public ZipOrganizeRunner(ZipOrganizeJobRepository jobRepository,
      ZipOrganizeItemRepository itemRepository,
      ObjectStorageService objectStorageService,
      FileTextExtractor fileTextExtractor,
      ZipOrganizeAiService aiService,
      ZipPackService zipPackService,
      ZipOrganizeProperties props,
      ObjectMapper objectMapper,
      ExecutorService zipOrganizeItemExecutor,
      QuotaService quotaService) {
    this.jobRepository = jobRepository;
    this.itemRepository = itemRepository;
    this.objectStorageService = objectStorageService;
    this.fileTextExtractor = fileTextExtractor;
    this.aiService = aiService;
    this.zipPackService = zipPackService;
    this.props = props;
    this.objectMapper = objectMapper;
    this.zipOrganizeItemExecutor = zipOrganizeItemExecutor;
    this.quotaService = quotaService;
  }

  public void runJob(long jobId) {
    if (MDC.get("traceId") == null || MDC.get("traceId").isBlank()) {
      MDC.put("traceId", java.util.UUID.randomUUID().toString().replace("-", ""));
    }
    MDC.put("zipJobId", String.valueOf(jobId));
    ZipOrganizeJobEntity job = jobRepository.findById(jobId)
        .orElseThrow(() -> new IllegalArgumentException("zip organize job not found"));
    if (job.getStatus() != ZipOrganizeJobStatus.RUNNING) return;
    long userId = job.getUserId();

    try {
      itemRepository.deleteAllByJob_Id(jobId);
      setProgress(job, 3, null);

      List<ZipOrganizeItemEntity> items = unpack(job);
      if (items.isEmpty()) throw new IllegalArgumentException("zip contains no supported files");
      job.setTotalItems(items.size());
      jobRepository.save(job);
      setProgress(job, 25, null);

      analyzeItems(job, items, userId);
      setProgress(job, 80, null);

      quotaService.consumeAiRequests(userId, 1);
      ZipOrganizeAiService.JobPlanResult plan = aiService.planJob(new ZipOrganizeAiService.JobPlanInput(
          items.stream()
              .map(item -> new ZipOrganizeAiService.JobPlanItem(
                  item.getOriginalPath(),
                  item.getDocType(),
                  item.getPaperCategory(),
                  item.getPaperSubtype(),
                  item.getTitleGuess(),
                  readKeywords(item),
                  item.getSummaryZh(),
                  item.getSuggestedFolder(),
                  item.getSuggestedFilename(),
                  item.getConfidence()
              ))
              .toList()
      ));

      assignFinalPaths(items, plan);
      itemRepository.saveAll(items);
      setProgress(job, 90, null);

      byte[] reportJson = buildReport(job, items, plan);
      String readme = buildReadme(job, items, plan);
      byte[] organizedZip = zipPackService.buildOrganizedZip(items, readme, reportJson);

      String outputKey = "zip-organize/%d/output/organized.zip".formatted(job.getId());
      String reportKey = "zip-organize/%d/output/report.json".formatted(job.getId());
      objectStorageService.putBytes(outputKey, organizedZip, "application/zip");
      objectStorageService.putBytes(reportKey, reportJson, "application/json");

      job.setOutputObjectKey(outputKey);
      job.setReportObjectKey(reportKey);
      job.setProcessedItems(items.size());
      job.setSuccessItems((int) items.stream().filter(item -> item.getFinalPath() != null && !item.getFinalPath().isBlank()).count());
      job.setFailedItems(items.size() - job.getSuccessItems());
      job.setStatus(job.getFailedItems() > 0 ? ZipOrganizeJobStatus.FAILED : ZipOrganizeJobStatus.SUCCEEDED);
      job.setFinishedAt(Instant.now());
      job.setErrorMessage(job.getFailedItems() > 0 ? "some files need manual review" : null);
      setProgress(job, 100, null);
      jobRepository.save(job);
    } catch (Exception e) {
      job.setStatus(ZipOrganizeJobStatus.FAILED);
      job.setFinishedAt(Instant.now());
      job.setErrorMessage(e.toString());
      jobRepository.save(job);
    } finally {
      MDC.remove("zipJobId");
    }
  }

  private List<ZipOrganizeItemEntity> unpack(ZipOrganizeJobEntity job) throws Exception {
    byte[] zipBytes = objectStorageService.getBytes(job.getInputObjectKey());
    int maxFiles = props.maxFiles() <= 0 ? 200 : props.maxFiles();
    int previewMaxChars = props.textPreviewMaxChars() <= 0 ? 6000 : props.textPreviewMaxChars();
    List<ZipOrganizeItemEntity> items = new ArrayList<>();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;
        if (items.size() >= maxFiles) throw new IllegalArgumentException("zip contains too many files");
        String originalPath = ZipOrganizeNaming.normalizeZipEntryPath(entry.getName());
        byte[] bytes = zis.readAllBytes();
        if (bytes.length == 0) continue;

        String filename = ZipOrganizeNaming.filenameOf(originalPath);
        String ext = ZipOrganizeNaming.extensionOf(filename);
        String contentType = ZipOrganizeNaming.guessContentType(filename);
        String sha256 = Digests.sha256Hex(bytes);
        String objectKey = "zip-organize/%d/items/%s/%s".formatted(
            job.getId(),
            sha256,
            ZipOrganizeNaming.sanitizeFilename(filename, ext)
        );
        objectStorageService.putBytes(objectKey, bytes, contentType);

        String full = fileTextExtractor.extract(filename, contentType, bytes);
        String preview = truncate(full, previewMaxChars);
        String extractedKey = null;
        ZipOrganizeExtractStatus extractStatus = preview.isBlank() ? ZipOrganizeExtractStatus.EMPTY : ZipOrganizeExtractStatus.EXTRACTED;
        if (!full.isBlank()) {
          extractedKey = "zip-organize/%d/extract/%s.txt".formatted(job.getId(), sha256);
          objectStorageService.putBytes(extractedKey, full.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
        }

        ZipOrganizeItemEntity item = new ZipOrganizeItemEntity();
        item.setJob(job);
        item.setOriginalPath(originalPath);
        item.setFilename(filename);
        item.setContentType(contentType);
        item.setSizeBytes(bytes.length);
        item.setSha256(sha256);
        item.setObjectKey(objectKey);
        item.setExtractStatus(extractStatus);
        item.setExtractedTextPreview(preview);
        item.setExtractedTextKey(extractedKey);
        items.add(item);
      }
    }
    return itemRepository.saveAll(items);
  }

  private void analyzeItems(ZipOrganizeJobEntity job, List<ZipOrganizeItemEntity> items, long userId) {
    Map<Long, ZipOrganizeItemEntity> byId = new HashMap<>();
    for (ZipOrganizeItemEntity item : items) byId.put(item.getId(), item);

    var mdc = MDC.getCopyOfContextMap();
    ExecutorCompletionService<ItemAnalysis> cs = new ExecutorCompletionService<>(zipOrganizeItemExecutor);
    for (ZipOrganizeItemEntity item : items) {
      cs.submit(() -> {
        if (mdc != null) MDC.setContextMap(mdc);
        quotaService.consumeAiRequests(userId, 1);
        try {
          ZipOrganizeAiService.ItemAiResult ai = aiService.analyzeItem(new ZipOrganizeAiService.ItemAiInput(
              item.getOriginalPath(),
              item.getFilename(),
              ZipOrganizeNaming.extensionOf(item.getFilename()),
              truncate(item.getExtractedTextPreview(), props.itemPromptMaxChars() <= 0 ? 6000 : props.itemPromptMaxChars())
          ));
          return new ItemAnalysis(item.getId(), ai, null);
        } catch (Exception e) {
          return new ItemAnalysis(item.getId(), fallbackItem(item), e.toString());
        }
      });
    }

    int total = items.size();
    int processed = 0;
    for (int i = 0; i < total; i++) {
      try {
        ItemAnalysis analysis = cs.take().get();
        ZipOrganizeItemEntity item = byId.get(analysis.itemId());
        if (item != null) {
          applyAnalysis(item, analysis.result(), analysis.errorMessage());
          itemRepository.save(item);
        }
      } catch (Exception ignored) {
      }
      processed++;
      job.setProcessedItems(processed);
      int progress = 25 + (int) Math.round(55.0 * processed / Math.max(1, total));
      setProgress(job, progress, null);
    }
  }

  private void applyAnalysis(ZipOrganizeItemEntity item, ZipOrganizeAiService.ItemAiResult ai, String errorMessage) {
    item.setDocType(ai.docType());
    item.setPaperCategory(ai.paperCategory());
    item.setPaperSubtype(ai.paperSubtype());
    item.setTitleGuess(ai.titleGuess());
    item.setAuthorGuess(firstNonBlank(ai.authorGuess()));
    item.setYearGuess(ai.yearGuess());
    item.setSummaryZh(ai.summaryZh());
    item.setSuggestedFolder(ai.suggestedFolder());
    item.setSuggestedFilename(ensureFilename(ai.suggestedFilename(), item.getFilename()));
    item.setConfidence(ai.confidence());
    item.setErrorMessage(errorMessage);
    try {
      item.setKeywordsJson(objectMapper.writeValueAsString(ai.keywords() == null ? List.of() : ai.keywords()));
    } catch (Exception ignored) {
      item.setKeywordsJson("[]");
    }
  }

  private void assignFinalPaths(List<ZipOrganizeItemEntity> items, ZipOrganizeAiService.JobPlanResult plan) {
    Map<String, String> mapped = new HashMap<>();
    if (plan.finalStructure() != null) {
      for (ZipOrganizeAiService.PlannedFile file : plan.finalStructure()) {
        mapped.put(file.originalPath(), file.finalPath());
      }
    }
    Set<String> used = new HashSet<>();
    double lowConfidenceThreshold = props.lowConfidenceThreshold() <= 0 ? 0.60d : props.lowConfidenceThreshold();
    items.sort(Comparator.comparing(ZipOrganizeItemEntity::getOriginalPath));
    for (ZipOrganizeItemEntity item : items) {
      String filename = ensureFilename(item.getSuggestedFilename(), item.getFilename());
      String folder = item.getSuggestedFolder();
      if (folder == null || folder.isBlank()) {
        folder = ZipOrganizeNaming.defaultFolder(item.getDocType(), item.getPaperCategory(), item.getPaperSubtype());
      }
      Double confidence = item.getConfidence();
      if (confidence == null || confidence < lowConfidenceThreshold || "unknown".equals(item.getDocType())) {
        folder = "Review_Required";
      }
      String candidate = mapped.get(item.getOriginalPath());
      if (candidate == null || candidate.isBlank()) {
        candidate = ZipOrganizeNaming.joinPath(folder, filename);
      } else {
        String mappedFilename = ZipOrganizeNaming.filenameOf(candidate);
        String mappedFolder = candidate.contains("/") ? candidate.substring(0, candidate.lastIndexOf('/')) : "";
        candidate = ZipOrganizeNaming.joinPath(mappedFolder, ensureFilename(mappedFilename, item.getFilename()));
      }
      item.setFinalPath(ZipOrganizeNaming.ensureUniquePath(candidate, used));
    }
  }

  private byte[] buildReport(ZipOrganizeJobEntity job, List<ZipOrganizeItemEntity> items, ZipOrganizeAiService.JobPlanResult plan) throws Exception {
    Map<String, Object> jobMap = new HashMap<>();
    jobMap.put("jobId", job.getId());
    jobMap.put("status", job.getStatus());
    jobMap.put("provider", aiService.provider());
    jobMap.put("model", aiService.model());
    jobMap.put("rootTopic", plan.rootTopic());
    jobMap.put("folderPlan", plan.folderPlan());
    jobMap.put("readmeSummary", plan.readmeSummary());

    List<Map<String, Object>> itemMaps = new ArrayList<>();
    for (ZipOrganizeItemEntity item : items) {
      Map<String, Object> map = new HashMap<>();
      map.put("originalPath", item.getOriginalPath());
      map.put("finalPath", item.getFinalPath());
      map.put("docType", item.getDocType());
      map.put("paperCategory", item.getPaperCategory());
      map.put("paperSubtype", item.getPaperSubtype());
      map.put("titleGuess", item.getTitleGuess());
      map.put("authorGuess", item.getAuthorGuess());
      map.put("yearGuess", item.getYearGuess());
      map.put("keywords", readKeywords(item));
      map.put("summaryZh", item.getSummaryZh());
      map.put("confidence", item.getConfidence());
      map.put("errorMessage", item.getErrorMessage());
      itemMaps.add(map);
    }

    Map<String, Object> report = new HashMap<>();
    report.put("job", jobMap);
    report.put("counts", Map.of(
        "total", items.size(),
        "reviewRequired", items.stream().filter(item -> item.getFinalPath() != null && item.getFinalPath().startsWith("Review_Required/")).count()
    ));
    report.put("items", itemMaps);
    return objectMapper.writeValueAsBytes(report);
  }

  private String buildReadme(ZipOrganizeJobEntity job, List<ZipOrganizeItemEntity> items, ZipOrganizeAiService.JobPlanResult plan) {
    long reviewRequired = items.stream()
        .filter(item -> item.getFinalPath() != null && item.getFinalPath().startsWith("Review_Required/"))
        .count();
    return """
        ZIP Organize Report

        Job ID: %d
        Provider: %s
        Model: %s
        Root Topic: %s
        Total Files: %d
        Review Required: %d

        Summary:
        %s
        """.formatted(
        job.getId(),
        aiService.provider(),
        aiService.model(),
        plan.rootTopic() == null ? "" : plan.rootTopic(),
        items.size(),
        reviewRequired,
        plan.readmeSummary() == null ? "" : plan.readmeSummary()
    );
  }

  private void setProgress(ZipOrganizeJobEntity job, int progress, String errorMessage) {
    job.setProgress(Math.max(0, Math.min(100, progress)));
    if (errorMessage != null) job.setErrorMessage(errorMessage);
    jobRepository.save(job);
  }

  private static String firstNonBlank(List<String> values) {
    if (values == null) return "";
    for (String value : values) {
      if (value != null && !value.isBlank()) return value.trim();
    }
    return "";
  }

  private static String ensureFilename(String candidate, String fallback) {
    String ext = ZipOrganizeNaming.extensionOf(fallback);
    String filename = candidate == null || candidate.isBlank() ? fallback : candidate;
    return ZipOrganizeNaming.sanitizeFilename(filename, ext);
  }

  private static String truncate(String text, int maxChars) {
    String value = text == null ? "" : text.trim();
    if (maxChars <= 0 || value.length() <= maxChars) return value;
    return value.substring(0, maxChars);
  }

  private List<String> readKeywords(ZipOrganizeItemEntity item) {
    try {
      return objectMapper.readValue(item.getKeywordsJson() == null ? "[]" : item.getKeywordsJson(),
          objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private ZipOrganizeAiService.ItemAiResult fallbackItem(ZipOrganizeItemEntity item) {
    String docType = switch (ZipOrganizeNaming.extensionOf(item.getFilename())) {
      case "pdf", "doc", "docx" -> "paper";
      case "txt" -> "reference";
      default -> "unknown";
    };
    String folder = ZipOrganizeNaming.defaultFolder(docType, "Other", "general");
    return new ZipOrganizeAiService.ItemAiResult(
        docType,
        "Other",
        "general",
        "",
        List.of(),
        null,
        List.of(),
        "",
        folder,
        item.getFilename(),
        "unknown".equals(docType) ? 0.3d : 0.55d
    );
  }

  private record ItemAnalysis(Long itemId, ZipOrganizeAiService.ItemAiResult result, String errorMessage) {}
}
