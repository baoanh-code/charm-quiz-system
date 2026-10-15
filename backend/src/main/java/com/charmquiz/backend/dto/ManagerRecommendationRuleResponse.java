package com.charmquiz.backend.dto;

import com.charmquiz.backend.model.RecommendationRuleField;
import com.charmquiz.backend.model.RecommendationRuleOperator;
import com.charmquiz.backend.model.RecommendationRuleStatus;

public record ManagerRecommendationRuleResponse(
        String id,
        String charmId,
        RecommendationRuleField field,
        RecommendationRuleOperator operator,
        String value,
        int priority,
        RecommendationRuleStatus status
) {
}
