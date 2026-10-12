package com.charmquiz.backend.service;

import com.charmquiz.backend.client.AiRecommendationException;
import com.charmquiz.backend.client.GeminiRecommendationClient;
import com.charmquiz.backend.dto.RecommendationRequest;
import com.charmquiz.backend.dto.RecommendationResponse;
import com.charmquiz.backend.model.DerivedProfile;
import com.charmquiz.backend.model.EligibleCharm;
import com.charmquiz.backend.model.RecommendationSectionType;
import com.charmquiz.backend.model.RecommendationSource;
import com.charmquiz.backend.model.RuleRecommendation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RecommendationService {

    private static final int MAX_RESULTS_PER_SECTION = 3;
    private static final List<RecommendationSectionType> SECTION_ORDER = List.of(
            RecommendationSectionType.NAME,
            RecommendationSectionType.BIRTH_DATE,
            RecommendationSectionType.INTERESTS,
            RecommendationSectionType.FAVORITE_ANIMAL,
            RecommendationSectionType.FAVORITE_COLOR
    );
    private static final Set<RecommendationSectionType> AI_ELIGIBLE_SECTIONS = Set.of(
            RecommendationSectionType.INTERESTS,
            RecommendationSectionType.FAVORITE_ANIMAL,
            RecommendationSectionType.FAVORITE_COLOR
    );

    private final SupabaseCharmService charmService;
    private final SupabaseRecommendationRuleService ruleService;
    private final ProfileDerivationService profileDerivationService;
    private final GeminiRecommendationClient geminiClient;
    private final LocalFallbackService localFallbackService;

    public RecommendationService(
            SupabaseCharmService charmService,
            SupabaseRecommendationRuleService ruleService,
            ProfileDerivationService profileDerivationService,
            GeminiRecommendationClient geminiClient,
            LocalFallbackService localFallbackService) {
        this.charmService = charmService;
        this.ruleService = ruleService;
        this.profileDerivationService = profileDerivationService;
        this.geminiClient = geminiClient;
        this.localFallbackService = localFallbackService;
    }

    public RecommendationResponse getRecommendations(RecommendationRequest request) {
        DerivedProfile profile = profileDerivationService.derive(request);
        List<EligibleCharm> catalog = charmService.getEligibleCharms();
        Map<String, EligibleCharm> catalogById = eligibleCatalogById(catalog);
        List<EligibleCharm> eligibleCatalog = List.copyOf(catalogById.values());

        Map<RecommendationSectionType, List<RecommendationResponse.Item>> results =
                new EnumMap<>(RecommendationSectionType.class);
        addRuleResults(results, ruleService.getMatches(profile), catalogById);

        Map<RecommendationSectionType, List<String>> localInputs = localInputs(profile);
        for (RecommendationSectionType section : List.of(
                RecommendationSectionType.NAME,
                RecommendationSectionType.BIRTH_DATE)) {
            if (!hasResults(results, section)) {
                results.put(section, localItems(section, localInputs.get(section), eligibleCatalog));
            }
        }

        Map<RecommendationSectionType, List<String>> unresolvedAiInputs = new EnumMap<>(
                RecommendationSectionType.class);
        for (RecommendationSectionType section : AI_ELIGIBLE_SECTIONS) {
            if (!hasResults(results, section)) {
                unresolvedAiInputs.put(section, localInputs.get(section));
            }
        }

        if (!unresolvedAiInputs.isEmpty()) {
            resolveAiEligibleSections(results, unresolvedAiInputs, eligibleCatalog, catalogById);
        }

        return new RecommendationResponse(SECTION_ORDER.stream()
                .map(section -> new RecommendationResponse.Section(
                        section,
                        responseInput(section, request, profile),
                        derivedValues(section, profile),
                        validateFinalItems(results.getOrDefault(section, List.of()), catalogById)
                ))
                .toList());
    }

    private void addRuleResults(
            Map<RecommendationSectionType, List<RecommendationResponse.Item>> results,
            List<RuleRecommendation> rules,
            Map<String, EligibleCharm> catalogById) {
        for (RecommendationSectionType section : SECTION_ORDER) {
            Set<String> seenCharmIds = new HashSet<>();
            List<RecommendationResponse.Item> items = rules.stream()
                    .filter(rule -> rule.section() == section)
                    .filter(rule -> catalogById.containsKey(rule.charmId()))
                    .sorted(Comparator.comparingInt(RuleRecommendation::priority).reversed()
                            .thenComparing(RuleRecommendation::charmId))
                    .filter(rule -> seenCharmIds.add(rule.charmId()))
                    .limit(MAX_RESULTS_PER_SECTION)
                    .map(rule -> {
                        EligibleCharm charm = catalogById.get(rule.charmId());
                        return toItem(
                                charm,
                                RecommendationSource.RULE,
                                rule.priority(),
                                "Matched " + rule.matchedField() + ": " + rule.matchedValue()
                        );
                    })
                    .toList();
            if (!items.isEmpty()) {
                results.put(section, items);
            }
        }
    }

    private void resolveAiEligibleSections(
            Map<RecommendationSectionType, List<RecommendationResponse.Item>> results,
            Map<RecommendationSectionType, List<String>> unresolvedInputs,
            List<EligibleCharm> catalog,
            Map<String, EligibleCharm> catalogById) {
        Map<RecommendationSectionType, List<String>> aiSelections = Map.of();
        boolean validAiResponse = false;

        if (geminiClient.isConfigured() && !catalog.isEmpty()) {
            try {
                aiSelections = geminiClient.recommend(unresolvedInputs, catalog);
                validateAiSelections(aiSelections, unresolvedInputs.keySet(), catalogById);
                validAiResponse = true;
            } catch (AiRecommendationException ignored) {
                // Recoverable by design: every unresolved section falls back locally.
            }
        }

        for (Map.Entry<RecommendationSectionType, List<String>> unresolved : unresolvedInputs.entrySet()) {
            RecommendationSectionType section = unresolved.getKey();
            List<String> selectedIds = validAiResponse
                    ? aiSelections.getOrDefault(section, List.of())
                    : List.of();

            if (!selectedIds.isEmpty()) {
                List<RecommendationResponse.Item> items = new ArrayList<>();
                Set<String> seenIds = new HashSet<>();
                for (String charmId : selectedIds) {
                    if (seenIds.add(charmId)) {
                        items.add(toItem(
                                catalogById.get(charmId),
                                RecommendationSource.AI,
                                items.size() + 1,
                                "Selected by AI from the eligible charm catalog"
                        ));
                    }
                }
                results.put(section, List.copyOf(items));
            } else {
                results.put(section, localItems(section, unresolved.getValue(), catalog));
            }
        }
    }

    private void validateAiSelections(
            Map<RecommendationSectionType, List<String>> selections,
            Set<RecommendationSectionType> requestedSections,
            Map<String, EligibleCharm> catalogById) {
        for (Map.Entry<RecommendationSectionType, List<String>> entry : selections.entrySet()) {
            if (!requestedSections.contains(entry.getKey()) || !AI_ELIGIBLE_SECTIONS.contains(entry.getKey())) {
                throw new AiRecommendationException("AI returned an unrequested section");
            }
            if (entry.getValue().size() > MAX_RESULTS_PER_SECTION) {
                throw new AiRecommendationException("AI returned too many charms");
            }
            for (String charmId : entry.getValue()) {
                if (!catalogById.containsKey(charmId)) {
                    throw new AiRecommendationException("AI returned an unavailable charm ID");
                }
            }
        }
    }

    private List<RecommendationResponse.Item> localItems(
            RecommendationSectionType section,
            List<String> inputs,
            List<EligibleCharm> catalog) {
        List<LocalFallbackService.LocalMatch> matches =
                localFallbackService.recommend(section, inputs, catalog);
        List<RecommendationResponse.Item> items = new ArrayList<>();
        for (LocalFallbackService.LocalMatch match : matches) {
            items.add(toItem(
                    match.charm(),
                    RecommendationSource.LOCAL_FALLBACK,
                    items.size() + 1,
                    match.reason()
            ));
        }
        return List.copyOf(items);
    }

    private List<RecommendationResponse.Item> validateFinalItems(
            List<RecommendationResponse.Item> items,
            Map<String, EligibleCharm> catalogById) {
        Set<String> seenIds = new HashSet<>();
        List<RecommendationResponse.Item> validatedItems = new ArrayList<>();

        for (RecommendationResponse.Item item : items) {
            if (!catalogById.containsKey(item.charmId()) || !seenIds.add(item.charmId())) {
                continue;
            }

            validatedItems.add(new RecommendationResponse.Item(
                    item.charmId(),
                    item.name(),
                    item.imageUrl(),
                    item.source(),
                    validatedItems.size() + 1,
                    item.reason()
            ));

            if (validatedItems.size() == MAX_RESULTS_PER_SECTION) {
                break;
            }
        }

        return List.copyOf(validatedItems);
    }

    private Map<String, EligibleCharm> eligibleCatalogById(List<EligibleCharm> catalog) {
        Map<String, EligibleCharm> byId = new LinkedHashMap<>();
        catalog.stream()
                .filter(EligibleCharm::isEligible)
                .sorted(Comparator.comparing(EligibleCharm::id))
                .forEach(charm -> byId.putIfAbsent(charm.id(), charm));
        return byId;
    }

    private Map<RecommendationSectionType, List<String>> localInputs(DerivedProfile profile) {
        Map<RecommendationSectionType, List<String>> inputs =
                new EnumMap<>(RecommendationSectionType.class);
        inputs.put(RecommendationSectionType.NAME, List.of(profile.nameInitial()));
        inputs.put(RecommendationSectionType.BIRTH_DATE, List.of(profile.zodiac(), profile.lifePath()));
        inputs.put(RecommendationSectionType.INTERESTS, profile.interests());
        inputs.put(RecommendationSectionType.FAVORITE_ANIMAL, List.of(profile.favoriteAnimal()));
        inputs.put(RecommendationSectionType.FAVORITE_COLOR, List.of(profile.favoriteColor()));
        return inputs;
    }

    private List<String> responseInput(
            RecommendationSectionType section,
            RecommendationRequest request,
            DerivedProfile profile) {
        return switch (section) {
            case NAME -> List.of(profile.givenName());
            case BIRTH_DATE -> List.of(request.birthDate().toString());
            case INTERESTS -> profile.interests();
            case FAVORITE_ANIMAL -> List.of(profile.favoriteAnimal());
            case FAVORITE_COLOR -> List.of(profile.favoriteColor());
        };
    }

    private Map<String, String> derivedValues(
            RecommendationSectionType section,
            DerivedProfile profile) {
        return switch (section) {
            case NAME -> Map.of(
                    "givenName", profile.givenName(),
                    "initial", profile.nameInitial()
            );
            case BIRTH_DATE -> Map.of(
                    "zodiac", profile.zodiac(),
                    "lifePath", profile.lifePath()
            );
            default -> Map.of();
        };
    }

    private RecommendationResponse.Item toItem(
            EligibleCharm charm,
            RecommendationSource source,
            int rank,
            String reason) {
        return new RecommendationResponse.Item(
                charm.id(),
                charm.name(),
                charm.imageUrl(),
                source,
                rank,
                reason
        );
    }

    private boolean hasResults(
            Map<RecommendationSectionType, List<RecommendationResponse.Item>> results,
            RecommendationSectionType section) {
        return !results.getOrDefault(section, List.of()).isEmpty();
    }
}
