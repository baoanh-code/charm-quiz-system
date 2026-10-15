package com.charmquiz.backend.dto;

import com.charmquiz.backend.model.CharmStatus;
import com.charmquiz.backend.model.CharmStockStatus;

import java.util.List;

public record ManagerCharmResponse(
        String id,
        String name,
        String description,
        String imageUrl,
        String category,
        List<String> tags,
        CharmStatus status,
        CharmStockStatus stockStatus
) {
}
