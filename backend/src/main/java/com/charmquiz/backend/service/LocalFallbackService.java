package com.charmquiz.backend.service;

import com.charmquiz.backend.model.EligibleCharm;
import com.charmquiz.backend.model.RecommendationSectionType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LocalFallbackService {

    private static final int MAX_RESULTS = 3;

    public List<LocalMatch> recommend(
            RecommendationSectionType section,
            List<String> inputs,
            List<EligibleCharm> catalog) {
        boolean exactOnly = section == RecommendationSectionType.NAME
                || section == RecommendationSectionType.BIRTH_DATE;

        return catalog.stream()
                .filter(EligibleCharm::isEligible)
                .map(charm -> score(charm, inputs, exactOnly))
                .filter(match -> match.matchTier() > 0)
                .sorted(Comparator.comparingInt(LocalMatch::matchTier).reversed()
                        .thenComparing(Comparator.comparingInt(LocalMatch::matchedInputCount).reversed())
                        .thenComparing(match -> match.charm().id()))
                .limit(MAX_RESULTS)
                .toList();
    }

    private LocalMatch score(EligibleCharm charm, List<String> inputs, boolean exactOnly) {
        int bestTier = 0;
        int matchedInputCount = 0;
        List<String> matchedInputs = new ArrayList<>();

        String category = EligibleCharm.normalize(charm.category());
        String searchableText = normalizeWords(charm.name() + " " + charm.description());

        for (String rawInput : inputs) {
            String input = EligibleCharm.normalize(rawInput);
            if (input.isBlank()) {
                continue;
            }

            int tier = 0;
            if (charm.normalizedTags().contains(input)) {
                tier = 3;
            } else if (category.equals(input)) {
                tier = 2;
            } else if (!exactOnly && input.length() >= 3 && containsWholePhrase(searchableText, input)) {
                tier = 1;
            }

            if (tier > 0) {
                bestTier = Math.max(bestTier, tier);
                matchedInputCount++;
                matchedInputs.add(rawInput);
            }
        }

        String reason = bestTier == 0
                ? ""
                : "Local semantic match: " + String.join(", ", matchedInputs);
        return new LocalMatch(charm, bestTier, matchedInputCount, reason);
    }

    private String normalizeWords(String value) {
        return EligibleCharm.normalize(value == null ? "" : value)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    private boolean containsWholePhrase(String searchableText, String input) {
        String normalizedInput = normalizeWords(input);
        return !(normalizedInput.isBlank())
                && (" " + searchableText + " ").contains(" " + normalizedInput + " ");
    }

    public record LocalMatch(
            EligibleCharm charm,
            int matchTier,
            int matchedInputCount,
            String reason
    ) {
    }
}
