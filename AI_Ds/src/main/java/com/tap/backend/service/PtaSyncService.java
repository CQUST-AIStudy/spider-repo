package com.tap.backend.service;

import com.tap.backend.domain.classroom.TeachingClassEntity;
import com.tap.backend.repo.TeachingClassRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class PtaSyncService {

    private static final Logger log = LoggerFactory.getLogger(PtaSyncService.class);
    private static final Duration COOLDOWN = Duration.ofHours(24);

    private final TeachingClassRepository classRepo;
    private final RestTemplate restTemplate;

    @Value("${pta.spider-url:http://127.0.0.1:8100}")
    private String spiderUrl;

    public PtaSyncService(
            TeachingClassRepository classRepo,
            @Value("${pta.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${pta.read-timeout-ms:20000}") int readTimeoutMs
    ) {
        this.classRepo = classRepo;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(1000, connectTimeoutMs));
        requestFactory.setReadTimeout(Math.max(1000, readTimeoutMs));
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Transactional
    public Map<String, Object> updateSyncConfig(Long classId, Long teacherId, String ptaKeyword, Boolean syncEnabled) {
        TeachingClassEntity teachingClass = requireOwnedClass(classId, teacherId);
        if (ptaKeyword != null) {
            teachingClass.setPtaKeyword(resolvePtaKeyword(teachingClass, ptaKeyword));
        }
        if (syncEnabled != null) {
            teachingClass.setSyncEnabled(syncEnabled);
        }
        classRepo.save(teachingClass);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ptaKeyword", teachingClass.getPtaKeyword());
        result.put("syncEnabled", teachingClass.getSyncEnabled());
        return result;
    }

    @Transactional
    public Map<String, Object> triggerSync(Long classId, Long teacherId) {
        return doTriggerSync(classId, teacherId, true);
    }

    @Transactional
    public Map<String, Object> triggerSyncScheduled(Long classId) {
        TeachingClassEntity teachingClass = classRepo.findById(classId)
                .orElseThrow(() -> new NoSuchElementException("class not found"));
        return doTriggerSync(teachingClass, false);
    }

    public Map<String, Object> getSyncStatus(Long classId, Long teacherId) {
        TeachingClassEntity teachingClass = requireOwnedClass(classId, teacherId);
        return toStatusMap(teachingClass);
    }

    @Transactional
    public void updateSyncResult(Long classId, String status) {
        classRepo.findById(classId).ifPresent(teachingClass -> {
            teachingClass.setSyncStatus(status);
            if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
                teachingClass.setLastSyncAt(Instant.now());
            }
            classRepo.save(teachingClass);
        });
    }

    public List<TeachingClassEntity> listSyncEnabledClasses() {
        return classRepo.findAll().stream()
                .filter(teachingClass -> Boolean.TRUE.equals(teachingClass.getSyncEnabled())
                        && teachingClass.getPtaKeyword() != null
                        && !teachingClass.getPtaKeyword().isBlank())
                .toList();
    }

    private Map<String, Object> doTriggerSync(Long classId, Long teacherId, boolean checkCooldown) {
        return doTriggerSync(requireOwnedClass(classId, teacherId), checkCooldown);
    }

    private Map<String, Object> doTriggerSync(TeachingClassEntity teachingClass, boolean checkCooldown) {
        if (teachingClass.getPtaKeyword() == null || teachingClass.getPtaKeyword().isBlank()) {
            throw new IllegalStateException("pta keyword is required before sync");
        }

        if (checkCooldown && teachingClass.getLastSyncAt() != null) {
            Duration since = Duration.between(teachingClass.getLastSyncAt(), Instant.now());
            if (since.compareTo(COOLDOWN) < 0) {
                long remainingHours = COOLDOWN.minus(since).toHours();
                long remainingMinutes = COOLDOWN.minus(since).toMinutes() % 60;
                String message = remainingHours > 0
                        ? "sync cooldown active, retry in " + remainingHours + "h " + remainingMinutes + "m"
                        : "sync cooldown active, retry in " + remainingMinutes + "m";
                throw new IllegalStateException(message);
            }
        }

        teachingClass.setSyncStatus("RUNNING");
        classRepo.save(teachingClass);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("keyword", teachingClass.getPtaKeyword());
            body.put("class_id", teachingClass.getId().intValue());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(spiderUrl + "/crawl", entity, Map.class);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("syncStatus", "RUNNING");
            if (response.getBody() != null) {
                result.put("taskId", response.getBody().get("task_id"));
                result.put("message", response.getBody().get("message"));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to trigger PTA sync: {}", e.getMessage());
            teachingClass.setSyncStatus("FAILED");
            classRepo.save(teachingClass);
            throw new RuntimeException("pta spider call failed: " + e.getMessage());
        }
    }

    private TeachingClassEntity requireOwnedClass(Long classId, Long teacherId) {
        TeachingClassEntity teachingClass = classRepo.findById(classId)
                .orElseThrow(() -> new NoSuchElementException("class not found"));
        if (!teacherId.equals(teachingClass.getTeacherId())) {
            throw new SecurityException("forbidden");
        }
        return teachingClass;
    }

    private Map<String, Object> toStatusMap(TeachingClassEntity teachingClass) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syncStatus", teachingClass.getSyncStatus());
        result.put("lastSyncAt", teachingClass.getLastSyncAt());
        result.put("ptaKeyword", teachingClass.getPtaKeyword());
        result.put("syncEnabled", teachingClass.getSyncEnabled());
        return result;
    }

    private String resolvePtaKeyword(TeachingClassEntity teachingClass, String ptaKeyword) {
        if (ptaKeyword != null && !ptaKeyword.isBlank()) {
            return ptaKeyword.trim();
        }
        return teachingClass.getName() == null ? null : teachingClass.getName().trim();
    }
}
