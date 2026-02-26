package com.tap.backend.rag;

import org.springframework.stereotype.Component;

@Component
public class ModeDecisionService {

    public record ModeDecision(String effectiveMode, boolean shouldFallbackToWeb, String lowCoverageMessage) {}

    public ModeDecision decide(String requestedMode, String defaultMode, boolean allowWebSearch,
                                double coverageScore, double coverageThreshold) {
        // Determine effective mode
        String effectiveMode = requestedMode != null ? requestedMode : defaultMode;

        // If teacher set strict, student cannot override to open
        if ("strict".equals(defaultMode) && "open".equals(requestedMode)) {
            effectiveMode = "strict";
        }

        boolean lowCoverage = coverageScore < coverageThreshold;
        boolean shouldWeb = false;
        String message = null;

        if (lowCoverage) {
            if ("strict".equals(effectiveMode)) {
                message = "⚠️ 当前课程资料未覆盖此问题，建议联系教师补充相关资料，或切换到开放模式获取更多信息。";
            } else if ("open".equals(effectiveMode) && allowWebSearch) {
                shouldWeb = true;
            }
        }

        return new ModeDecision(effectiveMode, shouldWeb, message);
    }
}
