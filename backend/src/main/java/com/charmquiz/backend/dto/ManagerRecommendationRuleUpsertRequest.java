package com.charmquiz.backend.dto;

import com.charmquiz.backend.model.RecommendationRuleField;
import com.charmquiz.backend.model.RecommendationRuleOperator;
import com.charmquiz.backend.model.RecommendationRuleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ManagerRecommendationRuleUpsertRequest(
        @NotNull UUID charmId,
        @NotNull RecommendationRuleField field,
        @NotNull RecommendationRuleOperator operator,
        @NotBlank String value,
        @NotNull Integer priority,
        @NotNull RecommendationRuleStatus status
) {
}
