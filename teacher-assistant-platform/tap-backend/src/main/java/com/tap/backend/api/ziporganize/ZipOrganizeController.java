package com.tap.backend.api.ziporganize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tap.backend.audit.AuditAction;
import com.tap.backend.audit.AuditService;
import com.tap.backend.domain.ziporganize.ZipOrganizeItemEntity;
import com.tap.backend.domain.ziporganize.ZipOrganizeJobEntity;
import com.tap.backend.infra.storage.ObjectStorageService;
import com.tap.backend.repo.ZipOrganizeItemRepository;
import com.tap.backend.repo.ZipOrganizeJobRepository;
import com.tap.backend.security.UserPrincipal;
import com.tap.backend.service.UserService;
import com.tap.backend.service.ziporganize.ZipOrganizeService;
import com.tap.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/zip-organize/jobs")
public class ZipOrganizeController {
  private final UserService userService;
  private final ZipOrganizeJobRepository jobRepository;
  private final ZipOrganizeItemRepository itemRepository;
  private final ZipOrganizeService zipOrganizeService;
  private final ObjectStorageService objectStorageService;
  private final ObjectMapper objectMapper;
  private final AuditService auditService;

  public ZipOrganizeController(UserService userService,
      ZipOrganizeJobRepository jobRepository,
      ZipOrganizeItemRepository itemRepository,
      ZipOrganizeService zipOrganizeService,
      ObjectStorageService objectStorageService,
      ObjectMapper objectMapper,
      AuditService auditService) {
    this.userService = userService;
    this.jobRepository = jobRepository;
    this.itemRepository = itemRepository;
    this.zipOrganizeService = zipOrganizeService;
    this.objectStorageService = objectStorageService;
    this.objectMapper = objectMapper;
    this.auditService = auditService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<Map<String, Object>> submit(
      @AuthenticationPrincipal UserPrincipal principal,
      HttpServletRequest request,
      @RequestPart("file") MultipartFile file
  ) throws Exception {
    if (file.isEmpty()) throw new IllegalArgumentException("zip file is empty");
    var user = userService.requireById(principal.userId());
    ZipOrganizeJobEntity job = zipOrganizeService.createJob(user, file.getOriginalFilename(), file.getBytes());
    auditService.record(principal, AuditAction.ZIP_ORGANIZE_SUBMIT, "ZipOrganizeJob", String.valueOf(job.getId()),
        Map.of("originalFilename", job.getOriginalFilename(), "bytes", file.getSize()), request);
    return ApiResponse.of(Map.of(
        "jobId", job.getId(),
        "status", job.getStatus(),
        "provider", job.getProvider(),
        "model", job.getModel()
    ));
  }

  @GetMapping("/{jobId}")
  @Transactional(readOnly = true)
  public ApiResponse<Map<String, Object>> get(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable("jobId") long jobId
  ) {
    ZipOrganizeJobEntity job = requireOwnedJob(principal.userId(), jobId);
    List<ZipOrganizeItemEntity> items = itemRepository.findAllByJob_IdOrderByOriginalPathAsc(job.getId());
    Map<String, Object> map = new HashMap<>();
    map.put("id", job.getId());
    map.put("status", job.getStatus());
    map.put("progress", job.getProgress());
    map.put("originalFilename", job.getOriginalFilename());
    map.put("provider", job.getProvider());
    map.put("model", job.getModel());
    map.put("totalItems", job.getTotalItems());
    map.put("processedItems", job.getProcessedItems());
    map.put("successItems", job.getSuccessItems());
    map.put("failedItems", job.getFailedItems());
    map.put("retryCount", job.getRetryCount());
    map.put("errorMessage", job.getErrorMessage());
    map.put("startedAt", job.getStartedAt());
    map.put("finishedAt", job.getFinishedAt());
    map.put("downloadReady", job.getOutputObjectKey() != null && !job.getOutputObjectKey().isBlank());
    map.put("reportReady", job.getReportObjectKey() != null && !job.getReportObjectKey().isBlank());
    map.put("items", items.stream().map(this::toItemMap).toList());
    return ApiResponse.of(map);
  }

  @GetMapping("/{jobId}/report")
  public ApiResponse<JsonNode> report(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable("jobId") long jobId
  ) throws Exception {
    ZipOrganizeJobEntity job = requireOwnedJob(principal.userId(), jobId);
    if (job.getReportObjectKey() == null || job.getReportObjectKey().isBlank()) {
      throw new IllegalArgumentException("report not ready");
    }
    return ApiResponse.of(objectMapper.readTree(objectStorageService.getBytes(job.getReportObjectKey())));
  }

  @GetMapping("/{jobId}/download")
  public ResponseEntity<ByteArrayResource> download(
      @AuthenticationPrincipal UserPrincipal principal,
      HttpServletRequest request,
      @PathVariable("jobId") long jobId
  ) {
    ZipOrganizeJobEntity job = requireOwnedJob(principal.userId(), jobId);
    if (job.getOutputObjectKey() == null || job.getOutputObjectKey().isBlank()) {
      throw new IllegalArgumentException("organized zip not ready");
    }
    byte[] bytes = objectStorageService.getBytes(job.getOutputObjectKey());
    String filename = job.getOriginalFilename().replaceAll("\\.zip$", "") + "-organized.zip";
    auditService.record(principal, AuditAction.ZIP_ORGANIZE_DOWNLOAD, "ZipOrganizeJob", String.valueOf(job.getId()),
        Map.of("bytes", bytes.length), request);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build()
            .toString())
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(bytes.length)
        .body(new ByteArrayResource(bytes));
  }

  @PostMapping("/{jobId}/retry")
  public ApiResponse<Map<String, Object>> retry(
      @AuthenticationPrincipal UserPrincipal principal,
      HttpServletRequest request,
      @PathVariable("jobId") long jobId
  ) {
    ZipOrganizeJobEntity job = requireOwnedJob(principal.userId(), jobId);
    job = zipOrganizeService.retryOwnedJob(job);
    auditService.record(principal, AuditAction.ZIP_ORGANIZE_RETRY, "ZipOrganizeJob", String.valueOf(job.getId()),
        Map.of("retryCount", job.getRetryCount()), request);
    return ApiResponse.of(Map.of("jobId", job.getId(), "status", job.getStatus(), "retryCount", job.getRetryCount()));
  }

  private ZipOrganizeJobEntity requireOwnedJob(Long userId, long jobId) {
    userService.requireById(userId);
    return jobRepository.findByIdAndUser_Id(jobId, userId)
        .orElseThrow(() -> new IllegalArgumentException("zip organize job not found"));
  }

  private Map<String, Object> toItemMap(ZipOrganizeItemEntity item) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", item.getId());
    map.put("originalPath", item.getOriginalPath());
    map.put("filename", item.getFilename());
    map.put("docType", item.getDocType());
    map.put("paperCategory", item.getPaperCategory());
    map.put("paperSubtype", item.getPaperSubtype());
    map.put("summaryZh", item.getSummaryZh());
    map.put("suggestedFolder", item.getSuggestedFolder());
    map.put("suggestedFilename", item.getSuggestedFilename());
    map.put("finalPath", item.getFinalPath());
    map.put("confidence", item.getConfidence());
    map.put("errorMessage", item.getErrorMessage());
    return map;
  }
}
