package com.charmquiz.backend.service;

import com.charmquiz.backend.client.GeminiRecommendationClient;
import com.charmquiz.backend.dto.RecommendationRequest;
import com.charmquiz.backend.dto.RecommendationResponse;
import com.charmquiz.backend.model.EligibleCharm;
import com.charmquiz.backend.model.RecommendationSectionType;
import com.charmquiz.backend.model.RecommendationSource;
import com.charmquiz.backend.model.RuleRecommendation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    private SupabaseCharmService charmService;
    private SupabaseRecommendationRuleService ruleService;
    private GeminiRecommendationClient geminiClient;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        charmService = mock(SupabaseCharmService.class);
        ruleService = mock(SupabaseRecommendationRuleService.class);
        geminiClient = mock(GeminiRecommendationClient.class);
        service = new RecommendationService(
                charmService,
                ruleService,
                new ProfileDerivationService(),
                geminiClient,
                new LocalFallbackService()
        );
    }

    @Test
    void returnsAllSectionsAndUsesRulesBeforeAiForEachSection() {
        List<EligibleCharm> catalog = List.of(
                charm("rule-low", "Low Music", "music"),
                charm("rule-high", "High Music", "music"),
                charm("cat", "Cat Charm", "cat"),
                charm("cat-2", "Second Cat Charm", "cat"),
                charm("red", "Red Charm", "red")
        );
        when(charmService.getEligibleCharms()).thenReturn(catalog);
        when(ruleService.getMatches(any())).thenReturn(List.of(
                new RuleRecommendation(
                        RecommendationSectionType.INTERESTS,
                        "INTEREST",
                        "music",
                        10,
                        "rule-low"
                ),
                new RuleRecommendation(
                        RecommendationSectionType.INTERESTS,
                        "INTEREST",
                        "music",
                        20,
                        "rule-high"
                )
        ));
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.recommend(anyMap(), anyList())).thenReturn(Map.of(
                RecommendationSectionType.FAVORITE_ANIMAL, List.of("cat-2", "cat"),
                RecommendationSectionType.FAVORITE_COLOR, List.of("red")
        ));

        RecommendationResponse response = service.getRecommendations(request());

        assertThat(response.sections()).extracting(RecommendationResponse.Section::type)
                .containsExactly(
                        RecommendationSectionType.NAME,
                        RecommendationSectionType.BIRTH_DATE,
                        RecommendationSectionType.INTERESTS,
                        RecommendationSectionType.FAVORITE_ANIMAL,
                        RecommendationSectionType.FAVORITE_COLOR
                );
        RecommendationResponse.Section interests = section(response, RecommendationSectionType.INTERESTS);
        assertThat(interests.recommendations())
                .extracting(RecommendationResponse.Item::charmId)
                .containsExactly("rule-high", "rule-low");
        assertThat(interests.recommendations())
                .extracting(RecommendationResponse.Item::source)
                .containsOnly(RecommendationSource.RULE);
        assertThat(interests.recommendations())
                .extracting(RecommendationResponse.Item::rank)
                .containsExactly(1, 2);
        assertThat(section(response, RecommendationSectionType.FAVORITE_ANIMAL).recommendations())
                .extracting(RecommendationResponse.Item::rank)
                .containsExactly(1, 2);
        assertThat(section(response, RecommendationSectionType.NAME).derivedValues())
                .containsEntry("givenName", "Anh")
                .containsEntry("initial", "A");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<RecommendationSectionType, List<String>>> inputCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(geminiClient).recommend(inputCaptor.capture(), anyList());
        assertThat(inputCaptor.getValue()).doesNotContainKeys(
                RecommendationSectionType.NAME,
                RecommendationSectionType.BIRTH_DATE,
                RecommendationSectionType.INTERESTS
        );
    }

    @Test
    void invalidAiCharmIdFallsBackLocallyForAllUnresolvedAiSections() {
        List<EligibleCharm> catalog = List.of(
                charm("music-a", "Music Charm A", "music"),
                charm("music-b", "Music Charm B", "music"),
                charm("cat", "Cat Charm", "cat"),
                charm("red", "Red Charm", "red")
        );
        when(charmService.getEligibleCharms()).thenReturn(catalog);
        when(ruleService.getMatches(any())).thenReturn(List.of());
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.recommend(anyMap(), anyList())).thenReturn(Map.of(
                RecommendationSectionType.INTERESTS, List.of("invented-id")
        ));

        RecommendationResponse response = service.getRecommendations(request());

        assertThat(section(response, RecommendationSectionType.INTERESTS).recommendations())
                .extracting(RecommendationResponse.Item::charmId)
                .containsExactly("music-a", "music-b");
        assertThat(section(response, RecommendationSectionType.INTERESTS).recommendations())
                .extracting(RecommendationResponse.Item::rank)
                .containsExactly(1, 2);
        assertThat(section(response, RecommendationSectionType.FAVORITE_ANIMAL).recommendations())
                .extracting(RecommendationResponse.Item::charmId)
                .containsExactly("cat");
        assertThat(section(response, RecommendationSectionType.FAVORITE_COLOR).recommendations())
                .extracting(RecommendationResponse.Item::charmId)
                .containsExactly("red");
        assertThat(section(response, RecommendationSectionType.INTERESTS).recommendations())
                .extracting(RecommendationResponse.Item::source)
                .containsOnly(RecommendationSource.LOCAL_FALLBACK);
    }

    @Test
    void neverReturnsInactiveOrOutOfStockCatalogEntries() {
        EligibleCharm inactive = new EligibleCharm(
                "inactive", "Music Charm", "", "", "music", "music", "INACTIVE", "IN_STOCK");
        EligibleCharm outOfStock = new EligibleCharm(
                "out", "Cat Charm", "", "", "animal", "cat", "ACTIVE", "OUT_OF_STOCK");
        when(charmService.getEligibleCharms()).thenReturn(List.of(inactive, outOfStock));
        when(ruleService.getMatches(any())).thenReturn(List.of(
                new RuleRecommendation(
                        RecommendationSectionType.INTERESTS,
                        "INTEREST",
                        "music",
                        10,
                        "inactive"
                )
        ));
        when(geminiClient.isConfigured()).thenReturn(false);

        RecommendationResponse response = service.getRecommendations(request());

        assertThat(response.sections())
                .allSatisfy(section -> assertThat(section.recommendations()).isEmpty());
        verify(geminiClient, never()).recommend(anyMap(), anyList());
    }

    private RecommendationRequest request() {
        return new RecommendationRequest(
                "Lâm Phú Bảo Anh",
                LocalDate.of(2006, 7, 24),
                List.of("music"),
                "cat",
                "red"
        );
    }

    private RecommendationResponse.Section section(
            RecommendationResponse response,
            RecommendationSectionType type) {
        return response.sections().stream()
                .filter(section -> section.type() == type)
                .findFirst()
                .orElseThrow();
    }

    private EligibleCharm charm(String id, String name, String tag) {
        return new EligibleCharm(
                id,
                name,
                "",
                "https://example.com/" + id + ".png",
                "general",
                tag,
                "ACTIVE",
                "IN_STOCK"
        );
    }
}
