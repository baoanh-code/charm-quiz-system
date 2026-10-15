package com.charmquiz.backend.dto;

public record QuizAnswerResponse(
        boolean isCorrect,
        String correctOptionId,
        String explanation
) {
}
