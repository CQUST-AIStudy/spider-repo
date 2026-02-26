package com.tap.backend.api.rag;

import com.tap.backend.rag.RagAnalyticsService;
import com.tap.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/course-spaces/{id}/analytics")
public class RagAnalyticsController {

    private final RagAnalyticsService analyticsService;

    public RagAnalyticsController(RagAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/hot-questions")
    public ApiResponse<List<RagAnalyticsService.QuestionRank>> hotQuestions(
            @PathVariable("id") Long courseSpaceId,
            @RequestParam(defaultValue = "20") int top) {
        return ApiResponse.of(analyticsService.getHotQuestions(courseSpaceId, top));
    }

    @GetMapping("/hit-rate")
    public ApiResponse<Map<String, Object>> hitRate(
            @PathVariable("id") Long courseSpaceId,
            @RequestParam(defaultValue = "0.4") double threshold) {
        return ApiResponse.of(Map.of("hitRate", analyticsService.getHitRate(courseSpaceId, threshold)));
    }

    @GetMapping("/citation-coverage")
    public ApiResponse<Map<String, Long>> citationCoverage(@PathVariable("id") Long courseSpaceId) {
        return ApiResponse.of(analyticsService.getCitationCoverage(courseSpaceId));
    }

    @GetMapping("/web-trigger-rate")
    public ApiResponse<Map<String, Object>> webTriggerRate(@PathVariable("id") Long courseSpaceId) {
        return ApiResponse.of(Map.of("webTriggerRate", analyticsService.getWebTriggerRate(courseSpaceId)));
    }

    @GetMapping("/feedback-stats")
    public ApiResponse<RagAnalyticsService.FeedbackStats> feedbackStats(@PathVariable("id") Long courseSpaceId) {
        return ApiResponse.of(analyticsService.getFeedbackStats(courseSpaceId));
    }

    @GetMapping("/resource-gaps")
    public ApiResponse<List<RagAnalyticsService.GapAlert>> resourceGaps(
            @PathVariable("id") Long courseSpaceId,
            @RequestParam(defaultValue = "0.4") double coverageThreshold,
            @RequestParam(defaultValue = "3") int minFrequency) {
        return ApiResponse.of(analyticsService.getResourceGaps(courseSpaceId, coverageThreshold, minFrequency));
    }
}
