package com.tap.backend.api.classroom;

import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.repo.UserRepository;
import com.tap.backend.service.PtaSyncService;
import com.tap.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/classes/{classId}/pta-sync")
public class PtaSyncController {

    private final PtaSyncService syncService;
    private final UserRepository userRepo;

    public PtaSyncController(PtaSyncService syncService, UserRepository userRepo) {
        this.syncService = syncService;
        this.userRepo = userRepo;
    }

    private Long resolveUserId(UserDetails principal) {
        if (principal != null) {
            return userRepo.findByUsername(principal.getUsername())
                    .orElseThrow(() -> new NoSuchElementException("用户不存在"))
                    .getId();
        }
        return userRepo.findAll().stream()
                .filter(u -> u.getRole() != null && u.getRole().name().equals("TEACHER"))
                .findFirst()
                .map(UserEntity::getId)
                .orElse(1L);
    }

    record SyncConfigRequest(String ptaKeyword, Boolean syncEnabled) {}

    @PutMapping
    public ApiResponse<Map<String, Object>> updateConfig(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long classId,
            @RequestBody SyncConfigRequest req) {
        Long userId = resolveUserId(principal);
        return ApiResponse.of(syncService.updateSyncConfig(classId, userId, req.ptaKeyword(), req.syncEnabled()));
    }

    @PostMapping("/trigger")
    public ApiResponse<Map<String, Object>> trigger(@PathVariable Long classId) {
        return ApiResponse.of(syncService.triggerSync(classId));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable Long classId) {
        return ApiResponse.of(syncService.getSyncStatus(classId));
    }

    /** 爬虫服务回调：任务完成后更新同步状态 */
    record CallbackRequest(String status) {}

    @PutMapping("/callback")
    public ApiResponse<Void> callback(@PathVariable Long classId, @RequestBody CallbackRequest req) {
        syncService.updateSyncResult(classId, req.status());
        return ApiResponse.of(null);
    }
}
