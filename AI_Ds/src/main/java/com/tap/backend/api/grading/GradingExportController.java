package com.tap.backend.api.grading;

import com.tap.backend.domain.grading.GradingSubmissionEntity;
import com.tap.backend.domain.grading.GradingTaskEntity;
import com.tap.backend.domain.grading.ReportFileEntity;
import com.tap.backend.infra.storage.ObjectStorageService;
import com.tap.backend.repo.GradingSubmissionRepository;
import com.tap.backend.repo.GradingTaskRepository;
import com.tap.backend.repo.ReportFileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/grading")
public class GradingExportController {

    private final GradingTaskRepository taskRepo;
    private final GradingSubmissionRepository submissionRepo;
    private final ReportFileRepository reportFileRepo;
    private final ObjectStorageService storageService;

    @Value("${tap.grading.export.max-concurrency:6}")
    private int exportMaxConcurrency;

    @Value("${tap.grading.export.submit-interval-ms:30}")
    private long exportSubmitIntervalMs;

    public GradingExportController(GradingTaskRepository taskRepo,
                                   GradingSubmissionRepository submissionRepo,
                                   ReportFileRepository reportFileRepo,
                                   ObjectStorageService storageService) {
        this.taskRepo = taskRepo;
        this.submissionRepo = submissionRepo;
        this.reportFileRepo = reportFileRepo;
        this.storageService = storageService;
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<?> downloadReport(@PathVariable Long id) {
        var reportOpt = reportFileRepo.findBySubmissionIdAndFileType(id, "pdf");
        if (reportOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Report not yet generated"));
        }
        byte[] bytes = storageService.getBytes(reportOpt.get().getObjectKey());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.pdf")
                .body(bytes);
    }

    @PostMapping("/tasks/{id}/export")
    public ResponseEntity<?> exportBatch(@PathVariable Long id) {
        GradingTaskEntity task = taskRepo.findById(id).orElse(null);
        if (task == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Task not found"));
        }

        List<GradingSubmissionEntity> subs = submissionRepo.findAllByTaskId(id);
        if (subs.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No submissions found"));
        }

        List<ReportFileEntity> reports = reportFileRepo.findAllByTaskIdAndFileType(id, "pdf");
        Map<Long, String> reportKeyBySubId = reports.stream()
                .filter(r -> r.getSubmission() != null && r.getSubmission().getId() != null)
                .collect(Collectors.toMap(
                        r -> r.getSubmission().getId(),
                        ReportFileEntity::getObjectKey,
                        (a, b) -> a
                ));

        int concurrency = Math.max(1, Math.min(exportMaxConcurrency, subs.size()));
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CompletionService<ExportPayload> cs = new ExecutorCompletionService<>(pool);
        Map<Long, ExportPayload> payloadBySubId = new ConcurrentHashMap<>();

        try {
            int submitted = 0;
            for (GradingSubmissionEntity sub : subs) {
                cs.submit(() -> loadExportPayload(sub, reportKeyBySubId.get(sub.getId())));
                submitted++;
                if (exportSubmitIntervalMs > 0) {
                    Thread.sleep(exportSubmitIntervalMs);
                }
            }

            for (int i = 0; i < submitted; i++) {
                ExportPayload payload = cs.take().get();
                payloadBySubId.put(payload.submissionId(), payload);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);
            int exportedCount = 0;
            List<String> missingSubmissions = new ArrayList<>();
            Set<String> usedNames = new HashSet<>();

            for (GradingSubmissionEntity sub : subs) {
                ExportPayload payload = payloadBySubId.get(sub.getId());
                String baseName = sanitizeFileName((sub.getStudentName() != null && !sub.getStudentName().isBlank())
                        ? sub.getStudentName()
                        : "submission_" + sub.getId());
                if (payload != null && payload.pdfBytes() != null && payload.pdfBytes().length > 0) {
                    String filename = uniqueFileName(payload.filename() == null ? (baseName + ".pdf") : payload.filename(), usedNames);
                    zos.putNextEntry(new ZipEntry(filename));
                    zos.write(payload.pdfBytes());
                    zos.closeEntry();
                    exportedCount++;
                } else {
                    String reason = payload == null || payload.reason() == null ? "unknown" : payload.reason();
                    missingSubmissions.add(baseName + " (submissionId=" + sub.getId() + ", reason=" + reason + ")");
                }
            }

            if (!missingSubmissions.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("These submissions have no exportable report/original PDF:\n");
                for (String item : missingSubmissions) {
                    sb.append("- ").append(item).append('\n');
                }
                zos.putNextEntry(new ZipEntry("README-missing-reports.txt"));
                zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.close();

            if (exportedCount == 0) {
                return ResponseEntity.status(409).body(Map.of(
                        "message", "No report files are available yet. Please wait for report generation to finish."
                ));
            }

            byte[] zipBytes = baos.toByteArray();
            String zipKey = "grading/exports/task-" + id + ".zip";
            storageService.putBytes(zipKey, zipBytes, "application/zip");

            ReportFileEntity rf = new ReportFileEntity();
            rf.setTask(task);
            rf.setFileType("zip");
            rf.setObjectKey(zipKey);
            reportFileRepo.save(rf);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=grading-export.zip")
                    .body(zipBytes);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Export failed: " + e.getMessage()));
        } finally {
            pool.shutdownNow();
        }
    }

    private ExportPayload loadExportPayload(GradingSubmissionEntity sub, String reportObjectKey) {
        String baseName = sanitizeFileName((sub.getStudentName() != null && !sub.getStudentName().isBlank())
                ? sub.getStudentName()
                : "submission_" + sub.getId());
        try {
            if (reportObjectKey != null && !reportObjectKey.isBlank()) {
                byte[] reportBytes = storageService.getBytes(reportObjectKey);
                if (reportBytes != null && reportBytes.length > 0) {
                    return new ExportPayload(sub.getId(), baseName + ".pdf", reportBytes, "report");
                }
            }
        } catch (Exception ignored) {
        }

        try {
            if (sub.getPdfObjectKey() != null && !sub.getPdfObjectKey().isBlank()) {
                byte[] originBytes = storageService.getBytes(sub.getPdfObjectKey());
                if (originBytes != null && originBytes.length > 0) {
                    return new ExportPayload(sub.getId(), baseName + "-original.pdf", originBytes, "original");
                }
            }
        } catch (Exception ignored) {
        }
        return new ExportPayload(sub.getId(), null, null, "missing");
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
    }

    private String uniqueFileName(String original, Set<String> usedNames) {
        if (usedNames.add(original)) {
            return original;
        }
        int dot = original.lastIndexOf('.');
        String base = dot >= 0 ? original.substring(0, dot) : original;
        String ext = dot >= 0 ? original.substring(dot) : "";
        int idx = 2;
        while (true) {
            String candidate = base + "-" + idx + ext;
            if (usedNames.add(candidate)) {
                return candidate;
            }
            idx++;
        }
    }

    private record ExportPayload(Long submissionId, String filename, byte[] pdfBytes, String reason) {
    }
}
