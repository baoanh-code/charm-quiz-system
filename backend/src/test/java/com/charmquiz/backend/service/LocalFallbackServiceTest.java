package com.charmquiz.backend.service;

import com.charmquiz.backend.model.EligibleCharm;
import com.charmquiz.backend.model.RecommendationSectionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFallbackServiceTest {

    private final LocalFallbackService service = new LocalFallbackService();

    @Test
    void parsesCommaSeparatedTagsAndRanksTagBeforeCategoryAndKeyword() {
        EligibleCharm keyword = charm("3", "Music Note", "A song for you", "other", "heart");
        EligibleCharm category = charm("2", "Treble", "Sound", "music", "song");
        EligibleCharm tag = charm("1", "Melody", "Sound", "other", " music, song ");

        List<LocalFallbackService.LocalMatch> result = service.recommend(
                RecommendationSectionType.INTERESTS,
                List.of("MUSIC"),
                List.of(keyword, category, tag)
        );

        assertThat(result).extracting(match -> match.charm().id())
                .containsExactly("1", "2", "3");
    }

    @Test
    void doesNotReturnUnrelatedZeroScoreCharms() {
        List<LocalFallbackService.LocalMatch> result = service.recommend(
                RecommendationSectionType.FAVORITE_COLOR,
                List.of("purple"),
                List.of(charm("1", "Music Charm", "For songs", "music", "song"))
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shortNameAndLifePathValuesRequireExactTagOrCategoryMatch() {
        EligibleCharm accidentalNameMatch = charm("1", "Star Charm", "Classic", "general", "star");
        EligibleCharm exactInitialTag = charm("2", "Letter A Charm", "Initial", "letters", "a");
        EligibleCharm accidentalDigitMatch = charm("3", "Lucky 3 Stars", "Numbered", "general", "luck");

        assertThat(service.recommend(
                RecommendationSectionType.NAME,
                List.of("A"),
                List.of(accidentalNameMatch, exactInitialTag)
        )).extracting(match -> match.charm().id()).containsExactly("2");

        assertThat(service.recommend(
                RecommendationSectionType.BIRTH_DATE,
                List.of("3"),
                List.of(accidentalDigitMatch)
        )).isEmpty();
    }

    @Test
    void stableCharmIdBreaksEqualScores() {
        List<LocalFallbackService.LocalMatch> result = service.recommend(
                RecommendationSectionType.FAVORITE_ANIMAL,
                List.of("cat"),
                List.of(
                        charm("b", "Cat Two", "", "animal", "cat"),
                        charm("a", "Cat One", "", "animal", "cat")
                )
        );

        assertThat(result).extracting(match -> match.charm().id()).containsExactly("a", "b");
    }

    private EligibleCharm charm(
            String id,
            String name,
            String description,
            String category,
            String tags) {
        return new EligibleCharm(
                id,
                name,
                description,
                "https://example.com/" + id + ".png",
                category,
                tags,
                "ACTIVE",
                "IN_STOCK"
        );
    }
}
