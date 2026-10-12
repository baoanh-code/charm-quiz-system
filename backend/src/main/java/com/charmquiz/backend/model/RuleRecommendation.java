package com.charmquiz.backend.model;

public record RuleRecommendation(
        RecommendationSectionType section,
        String matchedField,
        String matchedValue,
        int priority,
        String charmId
) {
}
