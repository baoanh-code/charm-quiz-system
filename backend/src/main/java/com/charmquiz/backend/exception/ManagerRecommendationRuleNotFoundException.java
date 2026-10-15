package com.charmquiz.backend.exception;

public class ManagerRecommendationRuleNotFoundException extends RuntimeException {

    public ManagerRecommendationRuleNotFoundException() {
        super("Recommendation rule was not found.");
    }
}
