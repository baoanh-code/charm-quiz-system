package com.charmquiz.backend.dto;

import com.charmquiz.backend.model.QuizStatus;

import java.util.List;

public record ManagerQuizResponse(
        String id,
        String title,
        String description,
        QuizStatus status,
        List<ManagerQuizQuestionResponse> questions
) {
}
