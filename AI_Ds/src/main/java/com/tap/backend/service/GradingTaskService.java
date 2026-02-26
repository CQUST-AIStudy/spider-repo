package com.tap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tap.backend.domain.grading.*;
import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.infra.storage.ObjectStorageService;
import com.tap.backend.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
public class GradingTaskService {
    private static final Logger log = LoggerFactory.getLogger(GradingTaskService.class);
    private static final int MAX_BATCH_SIZE = 200;
    private static final String QUEUE_KEY = "grading:tasks";
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // %PDF

    private final GradingTaskRepository taskRepo;
    private final GradingSubmissionRepository submissionRepo;
    private final GradingRubricRepository rubricRepo;
    private final UserRepository userRepo;
    private final ObjectStorageService storageService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public GradingTaskService(GradingTaskRepository taskRepo,
                              GradingSubmissionRepository submissionRepo,
                              GradingRubricRepository rubricRepo,
                              UserRepository userRepo,
                              ObjectStorageService storageService,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper) {
        this.taskRepo = taskRepo;
        this.submissionRepo = submissionRepo;
        this.rubricRepo = rubricRepo;
        this.userRepo = userRepo;
        this.storageService = storageService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> createTask(Long teacherId, Long experimentId, Long classId,
                                           Long rubricId, java.math.BigDecimal scoreRangeMin,
                                           java.math.BigDecimal scoreRangeMax,
                                           MultipartFile[] files) {
        if (files.length > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Batch size exceeds maximum of " + MAX_BATCH_SIZE);
        }

        UserEntity teacher = userRepo.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        GradingRubricEntity rubric = rubricRepo.findById(rubricId)
                .orElseThrow(() -> new IllegalArgumentException("Rubric not found"));

        // Separate valid PDFs from invalid files
        List<MultipartFile> validPdfs = new ArrayList<>();
        List<String> rejectedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (isPdf(file)) {
                validPdfs.add(file);
            } else {
                rejectedFiles.add(file.getOriginalFilename());
            }
        }

        if (validPdfs.isEmpty()) {
            throw new IllegalArgumentException("No valid PDF files in the batch");
        }

        GradingTaskEntity task = new GradingTaskEntity();
        task.setTeacher(teacher);
        task.setExperimentId(experimentId);
        task.setClassId(classId);
        task.setRubric(rubric);
        task.setScoreRangeMin(scoreRangeMin);
        task.setScoreRangeMax(scoreRangeMax);
        task.setStatus(GradingTaskStatus.PENDING);
        task.setTotalCount(validPdfs.size());
        task = taskRepo.save(task);

        // Store PDFs and create submissions
        for (MultipartFile pdf : validPdfs) {
            try {
                String objectKey = "grading/" + task.getId() + "/" + UUID.randomUUID() + ".pdf";
                storageService.putBytes(objectKey, pdf.getBytes(), "application/pdf");

                GradingSubmissionEntity sub = new GradingSubmissionEntity();
                sub.setTask(task);
                sub.setPdfObjectKey(objectKey);
                sub.setOriginalFilename(pdf.getOriginalFilename());
                sub.setStudentName(extractStudentName(pdf.getOriginalFilename()));
                sub.setStatus(SubmissionStatus.PENDING);
                submissionRepo.save(sub);
            } catch (Exception e) {
                log.error("Failed to store PDF: {}", pdf.getOriginalFilename(), e);
                rejectedFiles.add(pdf.getOriginalFilename() + " (storage error)");
                task.setTotalCount(task.getTotalCount() - 1);
            }
        }

        task = taskRepo.save(task);
        final Long taskIdFinal = task.getId();

        // Publish to Redis AFTER transaction commits to avoid orphan messages
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishTaskToQueue(taskIdFinal);
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("status", task.getStatus().name());
        result.put("totalCount", task.getTotalCount());
        result.put("rubricId", rubricId);
        result.put("createdAt", task.getCreatedAt().toString());
        if (!rejectedFiles.isEmpty()) {
            result.put("rejectedFiles", rejectedFiles);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<GradingTaskEntity> getTaskList(Long teacherId, GradingTaskStatus status, Pageable pageable) {
        if (status != null) {
            return taskRepo.findAllByTeacherIdAndStatus(teacherId, status, pageable);
        }
        return taskRepo.findAllByTeacherId(teacherId, pageable);
    }

    @Transactional(readOnly = true)
    public GradingTaskEntity getTaskDetail(Long taskId) {
        return taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
    }

    @Transactional(readOnly = true)
    public List<GradingSubmissionEntity> getTaskSubmissions(Long taskId) {
        return submissionRepo.findAllByTaskId(taskId);
    }

    @Transactional
    public void deleteTask(Long taskId, Long teacherId) {
        GradingTaskEntity task = taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        if (!task.getTeacherId().equals(teacherId)) {
            throw new IllegalArgumentException("无权删除此任务");
        }
        if (task.getStatus() == GradingTaskStatus.PROCESSING) {
            throw new IllegalStateException("任务正在处理中，无法删除");
        }
        // CASCADE delete handles submissions, evidence, scores, traces, reports
        taskRepo.delete(task);
        log.info("Deleted grading task {} by teacher {}", taskId, teacherId);
    }

    @Transactional
    public void retryFailed(Long taskId) {
        GradingTaskEntity task = taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        List<GradingSubmissionEntity> failed = submissionRepo
                .findAllByTaskIdAndStatus(taskId, SubmissionStatus.FAILED);

        if (failed.isEmpty()) {
            throw new IllegalStateException("No failed submissions to retry");
        }

        for (GradingSubmissionEntity sub : failed) {
            sub.setStatus(SubmissionStatus.PENDING);
            sub.setErrorMessage(null);
            submissionRepo.save(sub);
        }

        task.setFailedCount(task.getFailedCount() - failed.size());
        task.setStatus(GradingTaskStatus.PROCESSING);
        taskRepo.save(task);

        final Long taskIdFinal = task.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishTaskToQueue(taskIdFinal);
            }
        });
    }

    /**
     * 导出 Excel：学号、姓名、班级、成绩，可选评语
     */
    @Transactional(readOnly = true)
    public byte[] exportExcel(Long taskId, List<Long> submissionIds, boolean includeComments) {
        List<GradingSubmissionEntity> subs;
        if (submissionIds != null && !submissionIds.isEmpty()) {
            subs = submissionRepo.findAllById(submissionIds);
        } else {
            subs = submissionRepo.findAllByTaskId(taskId);
        }

        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             var baos = new ByteArrayOutputStream()) {

            var sheet = workbook.createSheet("批改成绩");
            var headerStyle = workbook.createCellStyle();
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            var headerRow = sheet.createRow(0);
            int col = 0;
            String[] headers = includeComments
                    ? new String[]{"学号", "姓名", "班级", "成绩", "总评"}
                    : new String[]{"学号", "姓名", "班级", "成绩"};
            for (String h : headers) {
                var cell = headerRow.createCell(col++);
                cell.setCellValue(h);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (GradingSubmissionEntity sub : subs) {
                var row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(sub.getStudentNo() != null ? sub.getStudentNo() : "");
                row.createCell(1).setCellValue(sub.getStudentName() != null ? sub.getStudentName() : "");
                row.createCell(2).setCellValue(sub.getClassName() != null ? sub.getClassName() : "");
                if (sub.getTotalScore() != null) {
                    row.createCell(3).setCellValue(sub.getTotalScore().doubleValue());
                } else {
                    row.createCell(3).setCellValue("");
                }
                if (includeComments) {
                    row.createCell(4).setCellValue(sub.getFinalReviewComment() != null ? sub.getFinalReviewComment() : "");
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("导出 Excel 失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void onSubmissionComplete(Long submissionId, String status, java.math.BigDecimal totalScore) {
        GradingSubmissionEntity sub = submissionRepo.findById(submissionId).orElse(null);
        if (sub == null) {
            log.warn("Submission not found for completion: {}", submissionId);
            return;
        }

        SubmissionStatus subStatus = SubmissionStatus.valueOf(status);
        sub.setStatus(subStatus);
        sub.setTotalScore(totalScore);
        submissionRepo.save(sub);

        // Use atomic SQL updates to avoid race conditions under concurrent Redis notifications
        Long taskId = sub.getTaskId();
        if (subStatus == SubmissionStatus.SCORED || subStatus == SubmissionStatus.NEED_MORE_EVIDENCE) {
            taskRepo.incrementCompletedCount(taskId);
        } else if (subStatus == SubmissionStatus.FAILED) {
            taskRepo.incrementFailedCount(taskId);
        }

        // Re-read task after atomic update
        GradingTaskEntity task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        int done = task.getCompletedCount() + task.getFailedCount();
        if (done >= task.getTotalCount()) {
            task.setStatus(task.getFailedCount() > 0 ? GradingTaskStatus.FAILED : GradingTaskStatus.COMPLETED);
            taskRepo.save(task);
        } else if (task.getStatus() != GradingTaskStatus.PROCESSING) {
            task.setStatus(GradingTaskStatus.PROCESSING);
            taskRepo.save(task);
        }
    }

    private void publishTaskToQueue(Long taskId) {
        try {
            GradingTaskEntity task = taskRepo.findById(taskId).orElse(null);
            if (task == null) return;
            // Fetch rubric custom prompt
            GradingRubricEntity rubric = task.getRubric();
            String customPrompt = rubric != null ? rubric.getCustomPrompt() : null;

            List<GradingSubmissionEntity> pending = submissionRepo
                    .findAllByTaskIdAndStatus(taskId, SubmissionStatus.PENDING);
            for (GradingSubmissionEntity sub : pending) {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("taskId", taskId);
                msg.put("submissionId", sub.getId());
                msg.put("pdfObjectKey", sub.getPdfObjectKey());
                msg.put("rubricId", rubric.getId());
                if (customPrompt != null && !customPrompt.isBlank()) {
                    msg.put("customPrompt", customPrompt);
                }
                if (task.getScoreRangeMin() != null) {
                    msg.put("scoreRangeMin", task.getScoreRangeMin());
                }
                if (task.getScoreRangeMax() != null) {
                    msg.put("scoreRangeMax", task.getScoreRangeMax());
                }
                redisTemplate.opsForList().rightPush(QUEUE_KEY, objectMapper.writeValueAsString(msg));
            }
            log.info("Published {} submissions to grading queue for task {}", pending.size(), taskId);
        } catch (Exception e) {
            log.error("Failed to publish to Redis queue", e);
        }
    }

    private boolean isPdf(MultipartFile file) {
        try {
            if (file.isEmpty()) return false;
            // Check magic bytes
            try (InputStream is = file.getInputStream()) {
                byte[] header = new byte[4];
                if (is.read(header) < 4) return false;
                return header[0] == PDF_MAGIC[0] && header[1] == PDF_MAGIC[1]
                        && header[2] == PDF_MAGIC[2] && header[3] == PDF_MAGIC[3];
            }
        } catch (Exception e) {
            return false;
        }
    }

    private String extractStudentName(String filename) {
        if (filename == null) return null;
        // Remove .pdf extension and use as student name
        return filename.replaceAll("\\.[pP][dD][fF]$", "");
    }
}
