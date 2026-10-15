package com.charmquiz.backend.dto;

import java.util.List;

public record QuizQuestionResponse(
        String id,
        String prompt,
        List<QuizOptionResponse> options
) {
}
