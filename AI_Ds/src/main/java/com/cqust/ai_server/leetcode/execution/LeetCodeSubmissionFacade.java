package com.cqust.ai_server.leetcode.execution;

import com.cqust.ai_server.service.LeetCodeExecutionService;
import com.cqust.ai_server.service.LeetCodeRecommendationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LeetCodeSubmissionFacade {

    private final LeetCodeExecutionService executionService;
    private final LeetCodeRecommendationService recommendationService;

    public LeetCodeSubmissionFacade(
            LeetCodeExecutionService executionService,
            @Qualifier("intelligentRecommendationService") LeetCodeRecommendationService recommendationService) {
        this.executionService = executionService;
        this.recommendationService = recommendationService;
    }

    public Map<String, Object> submitSolution(
            Integer studentId,
            Long problemId,
            String code,
            String language,
            String recommendationRequestId,
            String recommendationSessionId) {
        Map<String, Object> result = executionService.submitSolution(studentId, problemId, code, language);
        if (Boolean.TRUE.equals(result.get("accepted"))
                && recommendationRequestId != null
                && !recommendationRequestId.isBlank()) {
            recommendationService.recordFeedback(
                    recommendationRequestId,
                    studentId,
                    problemId,
                    "complete",
                    recommendationSessionId
            );
        }
        return result;
    }
}
