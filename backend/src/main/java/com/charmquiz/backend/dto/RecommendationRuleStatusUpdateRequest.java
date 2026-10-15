package com.charmquiz.backend.dto;

import com.charmquiz.backend.model.RecommendationRuleStatus;
import jakarta.validation.constraints.NotNull;

public record RecommendationRuleStatusUpdateRequest(
        @NotNull RecommendationRuleStatus status
) {
}
