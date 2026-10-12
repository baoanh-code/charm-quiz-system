package com.charmquiz.backend.dto;

import com.charmquiz.backend.model.RecommendationSectionType;
import com.charmquiz.backend.model.RecommendationSource;

import java.util.List;
import java.util.Map;

public record RecommendationResponse(List<Section> sections) {

    public RecommendationResponse {
        sections = List.copyOf(sections);
    }

    public record Section(
            RecommendationSectionType type,
            List<String> input,
            Map<String, String> derivedValues,
            List<Item> recommendations
    ) {
        public Section {
            input = List.copyOf(input);
            derivedValues = Map.copyOf(derivedValues);
            recommendations = List.copyOf(recommendations);
        }
    }

    public record Item(
            String charmId,
            String name,
            String imageUrl,
            RecommendationSource source,
            int rank,
            String reason
    ) {
    }
}
