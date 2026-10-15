package com.charmquiz.backend.dto;

public record QuizApiError(
        String code,
        String message
) {
}
