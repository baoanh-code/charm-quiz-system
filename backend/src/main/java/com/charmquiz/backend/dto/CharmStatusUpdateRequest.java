package com.charmquiz.backend.dto;

import com.charmquiz.backend.model.CharmStatus;
import jakarta.validation.constraints.NotNull;

public record CharmStatusUpdateRequest(@NotNull CharmStatus status) {
}
