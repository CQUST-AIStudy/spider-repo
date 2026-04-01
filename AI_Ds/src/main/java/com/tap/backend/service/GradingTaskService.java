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
    private final GradingSubmissionService gradingSubmissionService;

    public GradingTaskService(GradingTaskRepository taskRepo,
                              GradingSubmissionRepository submissionRepo,
                              GradingRubricRepository rubricRepo,
                              UserRepository userRepo,
                              ObjectStorageService storageService,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              GradingSubmissionService gradingSubmissionService) {
        this.taskRepo = taskRepo;
        this.submissionRepo = submissionRepo;
        this.rubricRepo = rubricRepo;
        this.userRepo = userRepo;
        this.storageService = storageService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.gradingSubmissionService = gradingSubmissionService;
    }

    @Transactional
    public Map<String, Object> createTask(Long teacherId, Long experimentId, Long classId,
                                           Long rubricId, java.math.BigDecimal scoreRangeMin,
                                           java.math.BigDecimal scoreRangeMax,
                                           MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("At least one PDF or DOCX file is required");
        }
        if (files.length > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Batch size exceeds maximum of " + MAX_BATCH_SIZE);
        }
        validateScoreRange(scoreRangeMin, scoreRangeMax);

        UserEntity teacher = userRepo.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        GradingRubricEntity rubric = rubricRepo.findById(rubricId)
                .orElseThrow(() -> new IllegalArgumentException("Rubric not found"));
        if (!teacherId.equals(rubric.getTeacherId())) {
            throw new IllegalArgumentException("Rubric not found");
        }

        // Separate valid documents from invalid files
        List<MultipartFile> validPdfs = new ArrayList<>();
        List<String> rejectedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (isSupportedDocument(file)) {
                validPdfs.add(file);
            } else {
                rejectedFiles.add(file.getOriginalFilename());
            }
        }

        if (validPdfs.isEmpty()) {
            throw new IllegalArgumentException("No valid PDF or DOCX files in the batch");
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

        // Store documents and create submissions
        for (MultipartFile pdf : validPdfs) {
            try {
                String extension = resolveExtension(pdf.getOriginalFilename());
                String objectKey = "grading/" + task.getId() + "/" + UUID.randomUUID() + extension;
                storageService.putBytes(objectKey, pdf.getBytes(), detectContentType(pdf, extension));

                GradingSubmissionEntity sub = new GradingSubmissionEntity();
                sub.setTask(task);
                sub.setPdfObjectKey(objectKey);
                sub.setOriginalFilename(pdf.getOriginalFilename());
                sub.setStudentName(extractStudentName(pdf.getOriginalFilename()));
                sub.setStatus(SubmissionStatus.PENDING);
                submissionRepo.save(sub);
            } catch (Exception e) {
                log.error("Failed to store document: {}", pdf.getOriginalFilename(), e);
                rejectedFiles.add(pdf.getOriginalFilename() + " (storage error)");
                task.setTotalCount(task.getTotalCount() - 1);
            }
        }

        if (task.getTotalCount() <= 0) {
            throw new IllegalArgumentException("All PDF or DOCX files failed to upload");
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
    public GradingTaskEntity getTaskDetail(Long taskId, Long teacherId) {
        return requireOwnedTask(taskId, teacherId);
    }

    @Transactional(readOnly = true)
    public List<GradingSubmissionEntity> getTaskSubmissions(Long taskId, Long teacherId) {
        requireOwnedTask(taskId, teacherId);
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
    public void retryFailed(Long taskId, Long teacherId) {
        GradingTaskEntity task = requireOwnedTask(taskId, teacherId);

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

        refreshTaskCounters(task);
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

    @Transactional
    public void deleteOwnedTask(Long taskId, Long teacherId) {
        GradingTaskEntity task = requireOwnedTask(taskId, teacherId);
        if (task.getStatus() == GradingTaskStatus.PROCESSING) {
            throw new IllegalStateException("Task is still processing");
        }
        taskRepo.delete(task);
        log.info("Deleted grading task {} by teacher {}", taskId, teacherId);
    }

    /**
     * 导出 Excel：学号、姓名、班级、成绩，可选评语
     */
    @Transactional(readOnly = true)
    public byte[] exportExcel(Long taskId, Long teacherId, List<Long> submissionIds, boolean includeComments) {
        requireOwnedTask(taskId, teacherId);
        List<GradingSubmissionEntity> subs;
        if (submissionIds != null && !submissionIds.isEmpty()) {
            subs = submissionRepo.findAllByTaskIdAndIdIn(taskId, submissionIds);
            if (subs.size() != new HashSet<>(submissionIds).size()) {
                throw new IllegalArgumentException("Some submissions do not belong to this task");
            }
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
        final Long teacherId = sub.getTask().getTeacherId();

        Long taskId = sub.getTaskId();
        GradingTaskEntity task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        refreshTaskCounters(task);
        int done = task.getCompletedCount() + task.getFailedCount();
        if (done >= task.getTotalCount()) {
            task.setStatus(task.getFailedCount() > 0 ? GradingTaskStatus.FAILED : GradingTaskStatus.COMPLETED);
            taskRepo.save(task);
        } else if (task.getStatus() != GradingTaskStatus.PROCESSING) {
            task.setStatus(GradingTaskStatus.PROCESSING);
            taskRepo.save(task);
        }

        if (subStatus == SubmissionStatus.SCORED || subStatus == SubmissionStatus.NEED_MORE_EVIDENCE) {
            final Long submissionIdFinal = submissionId;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    autoFinalizeSubmission(submissionIdFinal, teacherId);
                }
            });
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
            if (!pending.isEmpty() && task.getStatus() == GradingTaskStatus.PENDING) {
                task.setStatus(GradingTaskStatus.PROCESSING);
                taskRepo.save(task);
            }
            for (GradingSubmissionEntity sub : pending) {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("taskId", taskId);
                msg.put("submissionId", sub.getId());
                msg.put("pdfObjectKey", sub.getPdfObjectKey());
                msg.put("originalFilename", sub.getOriginalFilename());
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

    private boolean isSupportedDocument(MultipartFile file) {
        try {
            if (file.isEmpty()) return false;
            String filename = file.getOriginalFilename();
            String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".docx")) {
                return true;
            }
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
        return filename.replaceAll("\\.[^.]+$", "");
    }

    private String resolveExtension(String filename) {
        if (filename == null) {
            return ".pdf";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".docx") ? ".docx" : ".pdf";
    }

    private String detectContentType(MultipartFile file, String extension) {
        if (file.getContentType() != null && !file.getContentType().isBlank()) {
            return file.getContentType();
        }
        if (".docx".equalsIgnoreCase(extension)) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return "application/pdf";
    }

    private void validateScoreRange(java.math.BigDecimal scoreRangeMin, java.math.BigDecimal scoreRangeMax) {
        if (scoreRangeMin == null && scoreRangeMax == null) {
            return;
        }
        if (scoreRangeMin == null || scoreRangeMax == null) {
            throw new IllegalArgumentException("Both scoreRangeMin and scoreRangeMax are required");
        }
        if (scoreRangeMin.compareTo(java.math.BigDecimal.ZERO) < 0
                || scoreRangeMax.compareTo(new java.math.BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Score range must be between 0 and 100");
        }
        if (scoreRangeMin.compareTo(scoreRangeMax) > 0) {
            throw new IllegalArgumentException("scoreRangeMin must be less than or equal to scoreRangeMax");
        }
    }

    private GradingTaskEntity requireOwnedTask(Long taskId, Long teacherId) {
        GradingTaskEntity task = taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        if (!Objects.equals(task.getTeacherId(), teacherId)) {
            throw new IllegalArgumentException("No permission to access this task");
        }
        return task;
    }

    private void refreshTaskCounters(GradingTaskEntity task) {
        Long taskId = task.getId();
        int completedCount = submissionRepo.countByTaskIdAndStatus(taskId, SubmissionStatus.SCORED)
                + submissionRepo.countByTaskIdAndStatus(taskId, SubmissionStatus.NEED_MORE_EVIDENCE);
        int failedCount = submissionRepo.countByTaskIdAndStatus(taskId, SubmissionStatus.FAILED);
        task.setCompletedCount(completedCount);
        task.setFailedCount(failedCount);
    }

    private void autoFinalizeSubmission(Long submissionId, Long teacherId) {
        try {
            GradingSubmissionEntity submission = submissionRepo.findById(submissionId).orElse(null);
            if (submission == null || teacherId == null) {
                return;
            }

            if (submission.getFinalReviewComment() == null || submission.getFinalReviewComment().isBlank()) {
                gradingSubmissionService.generateFinalReview(submissionId, teacherId);
            }
            gradingSubmissionService.publishToStudentReport(submissionId, teacherId);
            log.info("Auto finalized submission {}", submissionId);
        } catch (Exception e) {
            log.warn("Auto finalization failed for submission {}: {}", submissionId, e.getMessage());
        }
    }
}
