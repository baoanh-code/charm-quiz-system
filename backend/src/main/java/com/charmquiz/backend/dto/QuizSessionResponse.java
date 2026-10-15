package com.charmquiz.backend.dto;

import java.util.List;

public record QuizSessionResponse(
        String sessionId,
        List<QuizQuestionResponse> questions
) {
}
