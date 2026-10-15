package com.charmquiz.backend.dto;

import com.charmquiz.backend.model.QuizStatus;
import jakarta.validation.constraints.NotNull;

public record ManagerQuizStatusUpdateRequest(@NotNull QuizStatus status) {
}
