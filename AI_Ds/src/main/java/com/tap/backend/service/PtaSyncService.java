package com.tap.backend.service;

import com.tap.backend.domain.classroom.TeachingClassEntity;
import com.tap.backend.repo.TeachingClassRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class PtaSyncService {

    private static final Logger log = LoggerFactory.getLogger(PtaSyncService.class);
    private static final Duration COOLDOWN = Duration.ofHours(24);

    private final TeachingClassRepository classRepo;
    private final RestTemplate restTemplate;

    @Value("${pta.spider-url:http://127.0.0.1:8100}")
    private String spiderUrl;

    public PtaSyncService(TeachingClassRepository classRepo,
                          @Value("${pta.connect-timeout-ms:5000}") int connectTimeoutMs,
                          @Value("${pta.read-timeout-ms:20000}") int readTimeoutMs) {
        this.classRepo = classRepo;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Math.max(1000, connectTimeoutMs));
        rf.setReadTimeout(Math.max(1000, readTimeoutMs));
        this.restTemplate = new RestTemplate(rf);
    }

    @Transactional
    public Map<String, Object> updateSyncConfig(Long classId, Long teacherId,
                                                  String ptaKeyword, Boolean syncEnabled) {
        TeachingClassEntity tc = classRepo.findById(classId)
                .orElseThrow(() -> new NoSuchElementException("班级不存在"));
        if (!tc.getTeacherId().equals(teacherId)) {
            throw new SecurityException("无权修改此班级");
        }
        if (ptaKeyword != null) tc.setPtaKeyword(ptaKeyword);
        if (syncEnabled != null) tc.setSyncEnabled(syncEnabled);
        classRepo.save(tc);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ptaKeyword", tc.getPtaKeyword());
        result.put("syncEnabled", tc.getSyncEnabled());
        return result;
    }

    /** 手动触发同步（带冷却检查） */
    @Transactional
    public Map<String, Object> triggerSync(Long classId) {
        return doTriggerSync(classId, true);
    }

    /** 定时任务触发同步（跳过冷却检查） */
    @Transactional
    public Map<String, Object> triggerSyncScheduled(Long classId) {
        return doTriggerSync(classId, false);
    }

    private Map<String, Object> doTriggerSync(Long classId, boolean checkCooldown) {
        TeachingClassEntity tc = classRepo.findById(classId)
                .orElseThrow(() -> new NoSuchElementException("班级不存在"));

        if (tc.getPtaKeyword() == null || tc.getPtaKeyword().isBlank()) {
            throw new IllegalStateException("请先设置 PTA 搜索关键词");
        }

        // 冷却检查（仅手动触发时）
        if (checkCooldown && tc.getLastSyncAt() != null) {
            Duration since = Duration.between(tc.getLastSyncAt(), Instant.now());
            if (since.compareTo(COOLDOWN) < 0) {
                long remainingHours = COOLDOWN.minus(since).toHours();
                long remainingMinutes = COOLDOWN.minus(since).toMinutes() % 60;
                String msg = remainingHours > 0
                        ? "距上次同步不足24小时，请" + remainingHours + "小时" + remainingMinutes + "分钟后再试"
                        : "距上次同步不足24小时，请" + remainingMinutes + "分钟后再试";
                throw new IllegalStateException(msg);
            }
        }

        // 调用 FastAPI
        tc.setSyncStatus("RUNNING");
        classRepo.save(tc);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("keyword", tc.getPtaKeyword());
            body.put("class_id", classId.intValue());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    spiderUrl + "/crawl", entity, Map.class);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("syncStatus", "RUNNING");
            if (resp.getBody() != null) {
                result.put("taskId", resp.getBody().get("task_id"));
                result.put("message", resp.getBody().get("message"));
            }
            return result;

        } catch (Exception e) {
            log.error("触发爬取失败: {}", e.getMessage());
            tc.setSyncStatus("FAILED");
            classRepo.save(tc);
            throw new RuntimeException("爬虫服务调用失败: " + e.getMessage());
        }
    }

    @Transactional
    public void updateSyncResult(Long classId, String status) {
        classRepo.findById(classId).ifPresent(tc -> {
            tc.setSyncStatus(status);
            if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
                tc.setLastSyncAt(Instant.now());
            }
            classRepo.save(tc);
        });
    }

    public Map<String, Object> getSyncStatus(Long classId) {
        TeachingClassEntity tc = classRepo.findById(classId)
                .orElseThrow(() -> new NoSuchElementException("班级不存在"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syncStatus", tc.getSyncStatus());
        result.put("lastSyncAt", tc.getLastSyncAt());
        result.put("ptaKeyword", tc.getPtaKeyword());
        result.put("syncEnabled", tc.getSyncEnabled());
        return result;
    }

    /** 获取所有开启同步的班级 */
    public List<TeachingClassEntity> listSyncEnabledClasses() {
        return classRepo.findAll().stream()
                .filter(tc -> Boolean.TRUE.equals(tc.getSyncEnabled())
                        && tc.getPtaKeyword() != null && !tc.getPtaKeyword().isBlank())
                .toList();
    }
}
