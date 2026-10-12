package com.charmquiz.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record RecommendationRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotNull LocalDate birthDate,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 100) String> interests,
        @NotBlank @Size(max = 100) String favoriteAnimal,
        @NotBlank @Size(max = 100) String favoriteColor
) {
}
