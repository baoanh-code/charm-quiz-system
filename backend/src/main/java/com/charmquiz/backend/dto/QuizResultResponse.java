package com.charmquiz.backend.dto;

import java.util.List;

public record QuizResultResponse(
        int score,
        int correctCount,
        int incorrectCount,
        boolean rewardEligible,
        List<QuizResultDetailResponse> details
) {
}
