package com.charmquiz.backend.dto;

import com.charmquiz.backend.model.CharmStockStatus;
import jakarta.validation.constraints.NotNull;

public record CharmStockStatusUpdateRequest(@NotNull CharmStockStatus stockStatus) {
}
