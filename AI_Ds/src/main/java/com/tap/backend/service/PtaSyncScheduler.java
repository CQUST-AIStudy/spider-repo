package com.tap.backend.service;

import com.tap.backend.domain.classroom.TeachingClassEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PtaSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(PtaSyncScheduler.class);
    private static final long INTERVAL_BETWEEN_CLASSES_MS = 5 * 60 * 1000L; // 5 分钟

    private final PtaSyncService syncService;

    public PtaSyncScheduler(PtaSyncService syncService) {
        this.syncService = syncService;
    }

    /** 每天凌晨 2 点执行，串行处理，班级之间间隔 5 分钟 */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledSync() {
        List<TeachingClassEntity> classes = syncService.listSyncEnabledClasses();
        if (classes.isEmpty()) {
            log.info("[PTA定时同步] 没有开启同步的班级");
            return;
        }

        log.info("[PTA定时同步] 开始处理 {} 个班级", classes.size());
        for (int i = 0; i < classes.size(); i++) {
            TeachingClassEntity tc = classes.get(i);
            try {
                log.info("[PTA定时同步] ({}/{}) 正在同步: {} (keyword={})",
                        i + 1, classes.size(), tc.getName(), tc.getPtaKeyword());
                syncService.triggerSyncScheduled(tc.getId());
            } catch (Exception e) {
                log.warn("[PTA定时同步] 班级 {} 同步失败: {}", tc.getName(), e.getMessage());
            }

            // 班级之间间隔 5 分钟，最后一个不等
            if (i < classes.size() - 1) {
                try {
                    Thread.sleep(INTERVAL_BETWEEN_CLASSES_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[PTA定时同步] 被中断");
                    return;
                }
            }
        }
        log.info("[PTA定时同步] 全部完成");
    }
}
