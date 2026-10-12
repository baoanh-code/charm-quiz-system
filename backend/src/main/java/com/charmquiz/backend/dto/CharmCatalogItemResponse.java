package com.charmquiz.backend.dto;

import com.charmquiz.backend.model.CharmStockStatus;

public record CharmCatalogItemResponse(
        String id,
        String name,
        String description,
        String imageUrl,
        String category,
        CharmStockStatus stockStatus,
        boolean available
) {
    public CharmCatalogItemResponse {
        available = stockStatus == CharmStockStatus.IN_STOCK;
    }

    public static CharmCatalogItemResponse from(
            String id,
            String name,
            String description,
            String imageUrl,
            String category,
            CharmStockStatus stockStatus
    ) {
        return new CharmCatalogItemResponse(
                id,
                name,
                description,
                imageUrl,
                category,
                stockStatus,
                stockStatus == CharmStockStatus.IN_STOCK
        );
    }
}
