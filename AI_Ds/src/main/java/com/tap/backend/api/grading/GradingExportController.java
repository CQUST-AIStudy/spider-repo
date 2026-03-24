package com.tap.backend.api.grading;

import com.tap.backend.domain.grading.GradingSubmissionEntity;
import com.tap.backend.domain.grading.GradingTaskEntity;
import com.tap.backend.domain.grading.ReportFileEntity;
import com.tap.backend.infra.storage.ObjectStorageService;
import com.tap.backend.repo.GradingSubmissionRepository;
import com.tap.backend.repo.GradingTaskRepository;
import com.tap.backend.repo.ReportFileRepository;
import com.tap.backend.security.TeacherPrincipalResolver;
import com.tap.backend.security.UserPrincipal;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/grading")
public class GradingExportController {

    private final GradingTaskRepository taskRepo;
    private final GradingSubmissionRepository submissionRepo;
    private final ReportFileRepository reportFileRepo;
    private final ObjectStorageService storageService;
    private final TeacherPrincipalResolver teacherPrincipalResolver;

    @Value("${tap.grading.export.max-concurrency:6}")
    private int exportMaxConcurrency;

    @Value("${tap.grading.export.submit-interval-ms:30}")
    private long exportSubmitIntervalMs;

    public GradingExportController(
            GradingTaskRepository taskRepo,
            GradingSubmissionRepository submissionRepo,
            ReportFileRepository reportFileRepo,
            ObjectStorageService storageService,
            TeacherPrincipalResolver teacherPrincipalResolver
    ) {
        this.taskRepo = taskRepo;
        this.submissionRepo = submissionRepo;
        this.reportFileRepo = reportFileRepo;
        this.storageService = storageService;
        this.teacherPrincipalResolver = teacherPrincipalResolver;
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<?> downloadReport(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long teacherId = teacherPrincipalResolver.requireTeacherId(principal);
        var reportOpt = reportFileRepo.findBySubmissionIdAndFileType(id, "pdf");
        if (reportOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Report not yet generated"));
        }
        requireOwnedTask(reportOpt.get().getTaskId(), teacherId);
        byte[] bytes = storageService.getBytes(reportOpt.get().getObjectKey());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.pdf")
                .body(bytes);
    }

    @PostMapping("/tasks/{id}/export")
    public ResponseEntity<?> exportBatch(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long teacherId = teacherPrincipalResolver.requireTeacherId(principal);
        GradingTaskEntity task = requireOwnedTask(id, teacherId);

        List<GradingSubmissionEntity> submissions = submissionRepo.findAllByTaskId(id);
        if (submissions.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No submissions found"));
        }

        List<ReportFileEntity> reports = reportFileRepo.findAllByTaskIdAndFileType(id, "pdf");
        Map<Long, String> reportKeyBySubId = reports.stream()
                .filter(report -> report.getSubmission() != null && report.getSubmission().getId() != null)
                .collect(Collectors.toMap(
                        report -> report.getSubmission().getId(),
                        ReportFileEntity::getObjectKey,
                        (left, right) -> left
                ));

        int concurrency = Math.max(1, Math.min(exportMaxConcurrency, submissions.size()));
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CompletionService<ExportPayload> completionService = new ExecutorCompletionService<>(pool);
        Map<Long, ExportPayload> payloadBySubmissionId = new ConcurrentHashMap<>();

        try {
            int submitted = 0;
            for (GradingSubmissionEntity submission : submissions) {
                completionService.submit(() -> loadExportPayload(submission, reportKeyBySubId.get(submission.getId())));
                submitted++;
                if (exportSubmitIntervalMs > 0) {
                    Thread.sleep(exportSubmitIntervalMs);
                }
            }

            for (int i = 0; i < submitted; i++) {
                ExportPayload payload = completionService.take().get();
                payloadBySubmissionId.put(payload.submissionId(), payload);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);
            int exportedCount = 0;
            List<String> missingSubmissions = new ArrayList<>();
            Set<String> usedNames = new HashSet<>();

            for (GradingSubmissionEntity submission : submissions) {
                ExportPayload payload = payloadBySubmissionId.get(submission.getId());
                String baseName = sanitizeFileName(
                        submission.getStudentName() != null && !submission.getStudentName().isBlank()
                                ? submission.getStudentName()
                                : "submission_" + submission.getId()
                );
                if (payload != null && payload.pdfBytes() != null && payload.pdfBytes().length > 0) {
                    String filename = uniqueFileName(
                            payload.filename() == null ? baseName + ".pdf" : payload.filename(),
                            usedNames
                    );
                    zos.putNextEntry(new ZipEntry(filename));
                    zos.write(payload.pdfBytes());
                    zos.closeEntry();
                    exportedCount++;
                } else {
                    String reason = payload == null || payload.reason() == null ? "unknown" : payload.reason();
                    missingSubmissions.add(baseName + " (submissionId=" + submission.getId() + ", reason=" + reason + ")");
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

            ReportFileEntity reportFile = new ReportFileEntity();
            reportFile.setTask(task);
            reportFile.setFileType("zip");
            reportFile.setObjectKey(zipKey);
            reportFileRepo.save(reportFile);

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

    private ExportPayload loadExportPayload(GradingSubmissionEntity submission, String reportObjectKey) {
        String baseName = sanitizeFileName(
                submission.getStudentName() != null && !submission.getStudentName().isBlank()
                        ? submission.getStudentName()
                        : "submission_" + submission.getId()
        );
        try {
            if (reportObjectKey != null && !reportObjectKey.isBlank()) {
                byte[] reportBytes = storageService.getBytes(reportObjectKey);
                if (reportBytes != null && reportBytes.length > 0) {
                    return new ExportPayload(submission.getId(), baseName + ".pdf", reportBytes, "report");
                }
            }
        } catch (Exception ignored) {
        }

        try {
            if (submission.getPdfObjectKey() != null && !submission.getPdfObjectKey().isBlank()) {
                byte[] originBytes = storageService.getBytes(submission.getPdfObjectKey());
                if (originBytes != null && originBytes.length > 0) {
                    return new ExportPayload(submission.getId(), baseName + "-original.pdf", originBytes, "original");
                }
            }
        } catch (Exception ignored) {
        }
        return new ExportPayload(submission.getId(), null, null, "missing");
    }

    private GradingTaskEntity requireOwnedTask(Long taskId, Long teacherId) {
        GradingTaskEntity task = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        if (!Objects.equals(task.getTeacherId(), teacherId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }
        return task;
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

    private record ExportPayload(Long submissionId, String filename, byte[] pdfBytes, String reason) {}
}
