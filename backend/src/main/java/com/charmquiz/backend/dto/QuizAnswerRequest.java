package com.charmquiz.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record QuizAnswerRequest(
        @NotNull UUID sessionId,
        @NotNull UUID questionId,
        @NotNull UUID optionId
) {
}
