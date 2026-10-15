package com.charmquiz.backend.dto;

public record QuizResultDetailResponse(
        String questionId,
        String questionText,
        String selectedAnswer,
        String correctAnswer,
        boolean isCorrect,
        String explanation
) {
}
