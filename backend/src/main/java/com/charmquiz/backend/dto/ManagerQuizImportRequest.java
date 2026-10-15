package com.charmquiz.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ManagerQuizImportRequest(
        @NotBlank String title,
        String description,
        @NotNull @Size(min = 10, max = 10)
        List<@Valid ManagerQuizQuestionImportRequest> questions
) {
}
