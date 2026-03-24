package com.tap.backend.api.classroom;

import com.tap.backend.domain.user.UserEntity;
import com.tap.backend.repo.UserRepository;
import com.tap.backend.service.PtaSyncService;
import com.tap.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;
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
                    .orElseThrow(() -> new NoSuchElementException("user not found"))
                    .getId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
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
    public ApiResponse<Map<String, Object>> trigger(@AuthenticationPrincipal UserDetails principal,
                                                    @PathVariable Long classId) {
        resolveUserId(principal);
        return ApiResponse.of(syncService.triggerSync(classId));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status(@AuthenticationPrincipal UserDetails principal,
                                                   @PathVariable Long classId) {
        resolveUserId(principal);
        return ApiResponse.of(syncService.getSyncStatus(classId));
    }

    /** 鐖櫕鏈嶅姟鍥炶皟锛氫换鍔″畬鎴愬悗鏇存柊鍚屾鐘舵€?*/
    record CallbackRequest(String status) {}

    @PutMapping("/callback")
    public ApiResponse<Void> callback(@PathVariable Long classId, @RequestBody CallbackRequest req) {
        syncService.updateSyncResult(classId, req.status());
        return ApiResponse.of(null);
    }
}
