package com.tap.backend.api.classroom;

import com.tap.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

/**
 * PTA Cookie 状态管理
 * - 爬虫服务上报 cookie 过期 → 存内存
 * - 前端查询 cookie 状态 → 显示告警
 * - 教师手动提交 cookie → 转发给爬虫服务验证
 */
@RestController
@RequestMapping("/api/pta-cookie")
public class PtaCookieController {

    private static final Logger log = LoggerFactory.getLogger(PtaCookieController.class);
    private final RestTemplate rest = new RestTemplate();

    @Value("${pta.spider-url:http://localhost:8100}")
    private String spiderUrl;

    // 内存缓存（重启后由爬虫下次运行时重新上报）
    private volatile String cookieStatus = "UNKNOWN";
    private volatile String cookieError = "";
    private volatile Instant lastUpdated = null;

    /** 爬虫服务回调：上报 cookie 状态 */
    record StatusReport(String status, String error) {}

    @PutMapping("/status")
    public ApiResponse<Void> reportStatus(@RequestBody StatusReport req) {
        this.cookieStatus = req.status();
        this.cookieError = req.error() != null ? req.error() : "";
        this.lastUpdated = Instant.now();
        log.info("PTA Cookie 状态更新: {} {}", req.status(), req.error());
        return ApiResponse.of(null);
    }

    /** 前端查询 cookie 状态 */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        // 优先从爬虫服务实时查询
        try {
            ResponseEntity<Map> resp = rest.getForEntity(spiderUrl + "/cookie/status", Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Map body = resp.getBody();
                this.cookieStatus = String.valueOf(body.getOrDefault("status", "UNKNOWN"));
                this.cookieError = String.valueOf(body.getOrDefault("error", ""));
            }
        } catch (Exception e) {
            log.debug("查询爬虫 cookie 状态失败（爬虫服务可能未启动）: {}", e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", cookieStatus);
        result.put("error", cookieError);
        result.put("lastUpdated", lastUpdated);
        return ApiResponse.of(result);
    }

    /** 教师手动提交 cookie → 转发给爬虫服务验证 */
    record CookieSubmit(String cookies) {}

    @PostMapping("/update")
    public ApiResponse<Map<String, Object>> submitCookie(@RequestBody CookieSubmit req) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of("cookies", req.cookies());
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> resp = rest.postForEntity(
                    spiderUrl + "/cookie/update", entity, Map.class);

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Map respBody = resp.getBody();
                boolean valid = Boolean.TRUE.equals(respBody.get("valid"));
                if (valid) {
                    this.cookieStatus = "OK";
                    this.cookieError = "";
                    this.lastUpdated = Instant.now();
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("valid", valid);
                result.put("message", respBody.get("message"));
                return ApiResponse.of(result);
            }
            throw new RuntimeException("爬虫服务返回异常");
        } catch (Exception e) {
            log.error("提交 Cookie 失败: {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("valid", false);
            result.put("message", "爬虫服务不可用: " + e.getMessage());
            return ApiResponse.of(result);
        }
    }
}
