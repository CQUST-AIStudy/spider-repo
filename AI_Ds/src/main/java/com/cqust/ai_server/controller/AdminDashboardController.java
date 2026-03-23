package com.cqust.ai_server.controller;

import com.tap.backend.service.AdminDashboardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin-dashboard")
public class AdminDashboardController {

  private final AdminDashboardService adminDashboardService;

  public AdminDashboardController(AdminDashboardService adminDashboardService) {
    this.adminDashboardService = adminDashboardService;
  }

  record SyncRequest(String mode, Boolean force) {}

  @GetMapping("/overview")
  public ResponseEntity<Map<String, Object>> getOverview(HttpServletRequest request) {
    ResponseEntity<Map<String, Object>> authFailure = ensureAdmin(request);
    if (authFailure != null) {
      return authFailure;
    }
    return ResponseEntity.ok(adminDashboardService.getOverview());
  }

  @PostMapping("/classes/{classId}/sync")
  public ResponseEntity<Map<String, Object>> triggerClassSync(
      @PathVariable Long classId,
      @RequestBody(required = false) SyncRequest req,
      HttpServletRequest request) {
    ResponseEntity<Map<String, Object>> authFailure = ensureAdmin(request);
    if (authFailure != null) {
      return authFailure;
    }

    String mode = req == null ? "incremental" : req.mode();
    boolean force = req != null && Boolean.TRUE.equals(req.force());
    return ResponseEntity.ok(adminDashboardService.triggerClassSync(classId, mode, force));
  }

  private ResponseEntity<Map<String, Object>> ensureAdmin(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return error(HttpStatus.UNAUTHORIZED, "未登录或会话已失效");
    }
    Object role = session.getAttribute("userRole");
    if (role == null || !"admin".equalsIgnoreCase(String.valueOf(role))) {
      return error(HttpStatus.FORBIDDEN, "仅管理员可访问该接口");
    }
    return null;
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("success", false);
    body.put("message", message);
    return ResponseEntity.status(status).body(body);
  }
}
