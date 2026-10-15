package com.charmquiz.backend.dto;

import java.util.List;

public record ManagerQuizQuestionResponse(
        String id,
        String prompt,
        List<String> options,
        int correctIndex,
        String explanation
) {
}
