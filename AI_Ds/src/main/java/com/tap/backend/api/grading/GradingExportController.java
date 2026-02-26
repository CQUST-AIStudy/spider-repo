package com.tap.backend.api.grading;

import com.tap.backend.domain.grading.*;
import com.tap.backend.infra.storage.ObjectStorageService;
import com.tap.backend.repo.*;
import com.tap.common.api.ApiResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/grading")
public class GradingExportController {

    private final GradingTaskRepository taskRepo;
    private final GradingSubmissionRepository submissionRepo;
    private final ReportFileRepository reportFileRepo;
    private final ObjectStorageService storageService;

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
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);

            for (GradingSubmissionEntity sub : subs) {
                var reportOpt = reportFileRepo.findBySubmissionIdAndFileType(sub.getId(), "pdf");
                if (reportOpt.isPresent()) {
                    byte[] pdfBytes = storageService.getBytes(reportOpt.get().getObjectKey());
                    String filename = (sub.getStudentName() != null ? sub.getStudentName() : "submission_" + sub.getId()) + ".pdf";
                    zos.putNextEntry(new ZipEntry(filename));
                    zos.write(pdfBytes);
                    zos.closeEntry();
                }
            }
            zos.close();

            // Store ZIP in MinIO
            byte[] zipBytes = baos.toByteArray();
            String zipKey = "grading/exports/task-" + id + ".zip";
            storageService.putBytes(zipKey, zipBytes, "application/zip");

            // Save report file record
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
        }
    }
}
