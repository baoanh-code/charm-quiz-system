package com.charmquiz.backend.model;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public record EligibleCharm(
        String id,
        String name,
        String description,
        String imageUrl,
        String category,
        String tags,
        String status,
        String stockStatus
) {
    public boolean isEligible() {
        return "ACTIVE".equalsIgnoreCase(status)
                && "IN_STOCK".equalsIgnoreCase(stockStatus);
    }

    public List<String> normalizedTags() {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }

        return Arrays.stream(tags.split(","))
                .map(EligibleCharm::normalize)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .toList();
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
