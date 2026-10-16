package com.charmquiz.backend.service;

import com.charmquiz.backend.model.EligibleCharm;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Service
public class RandomSuggestionService {

    static final int MAX_SUGGESTIONS = 8;
    private static final String GENERAL_CATEGORY = "GENERAL";

    private final Random random;

    public RandomSuggestionService() {
        this(new Random());
    }

    RandomSuggestionService(Random random) {
        this.random = random;
    }

    public List<EligibleCharm> select(
            List<EligibleCharm> catalog,
            Set<String> excludedCharmIds) {
        Map<String, EligibleCharm> distinctEligible = new LinkedHashMap<>();
        for (EligibleCharm charm : catalog) {
            if (charm.isEligible()
                    && !excludedCharmIds.contains(charm.id())
                    && GENERAL_CATEGORY.equals(normalizedCategory(charm.category()))) {
                distinctEligible.putIfAbsent(charm.id(), charm);
            }
        }

        List<EligibleCharm> general = new ArrayList<>(distinctEligible.values());
        Collections.shuffle(general, random);
        return List.copyOf(general.subList(0, Math.min(general.size(), MAX_SUGGESTIONS)));
    }

    private String normalizedCategory(String category) {
        return category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
    }
}
