package com.charmquiz.backend.client;

public class AiRecommendationException extends RuntimeException {

    public AiRecommendationException(String message) {
        super(message);
    }

    public AiRecommendationException(String message, Throwable cause) {
        super(message, cause);
    }
}
