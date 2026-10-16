package com.charmquiz.backend.service;

import com.charmquiz.backend.model.EligibleCharm;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RandomSuggestionServiceTest {

    private final RandomSuggestionService service = new RandomSuggestionService(new Random(7));

    @Test
    void returnsOnlyGeneralWithoutFallingBackToOtherCategories() {
        List<EligibleCharm> catalog = List.of(
                charm("animal", "ANIMAL"),
                charm("general-a", "GENERAL"),
                charm("color", "COLOR"),
                charm("general-b", " general "),
                charm("interest", "INTEREST"),
                charm("sport", "SPORT"),
                charm("culture", "CULTURE"),
                charm("other", "OTHER")
        );

        List<EligibleCharm> selected = service.select(catalog, Set.of());

        assertThat(selected).extracting(EligibleCharm::id)
                .containsExactlyInAnyOrder("general-a", "general-b")
                .doesNotContain("animal", "color", "interest", "sport", "culture", "other");
    }

    @Test
    void excludesDedicatedUnavailableAndAlreadyRecommendedCharms() {
        List<EligibleCharm> catalog = List.of(
                charm("general", "GENERAL"),
                charm("initial", "INITIAL"),
                charm("zodiac", "ZODIAC"),
                charm("used", "GENERAL"),
                new EligibleCharm("inactive", "Inactive", "", "image", "GENERAL", "", "INACTIVE", "IN_STOCK"),
                new EligibleCharm("out", "Out", "", "image", "GENERAL", "", "ACTIVE", "OUT_OF_STOCK")
        );

        assertThat(service.select(catalog, Set.of("used")))
                .extracting(EligibleCharm::id)
                .containsExactly("general");
    }

    @Test
    void returnsAtMostEightDistinctCharmIds() {
        List<EligibleCharm> catalog = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            catalog.add(charm("general-" + index, "GENERAL"));
        }
        catalog.add(charm("general-0", "GENERAL"));

        List<EligibleCharm> selected = service.select(catalog, Set.of());

        assertThat(selected).hasSize(8);
        assertThat(selected).extracting(EligibleCharm::id).doesNotHaveDuplicates();
    }

    @Test
    void randomizesSelectionsAcrossRequests() {
        List<EligibleCharm> catalog = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            catalog.add(charm("general-" + index, "GENERAL"));
        }

        List<String> first = service.select(catalog, Set.of()).stream()
                .map(EligibleCharm::id)
                .toList();
        List<String> second = service.select(catalog, Set.of()).stream()
                .map(EligibleCharm::id)
                .toList();

        assertThat(second).isNotEqualTo(first);
    }

    private EligibleCharm charm(String id, String category) {
        return new EligibleCharm(id, id, "", "image", category, "", "ACTIVE", "IN_STOCK");
    }
}
