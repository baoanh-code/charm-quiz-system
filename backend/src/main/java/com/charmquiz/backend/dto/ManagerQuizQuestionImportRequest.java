package com.charmquiz.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ManagerQuizQuestionImportRequest(
        @NotBlank String prompt,
        @NotNull @Size(min = 4, max = 4) List<@NotBlank String> options,
        @NotNull @Min(0) @Max(3) Integer correctIndex,
        String explanation
) {
}
