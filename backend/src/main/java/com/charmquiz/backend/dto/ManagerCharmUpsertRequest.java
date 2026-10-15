package com.charmquiz.backend.dto;

import com.charmquiz.backend.model.CharmStatus;
import com.charmquiz.backend.model.CharmStockStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ManagerCharmUpsertRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 2048)
        @Pattern(regexp = "^https://[^\\s]+$", message = "must be an HTTPS URL")
        String imageUrl,
        @Size(max = 200) String category,
        @Size(max = 50) List<@Valid @NotBlank @Size(max = 100) String> tags,
        @NotNull CharmStatus status,
        @NotNull CharmStockStatus stockStatus
) {
}
