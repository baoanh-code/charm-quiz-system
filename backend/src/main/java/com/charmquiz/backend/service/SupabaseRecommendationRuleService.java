package com.charmquiz.backend.service;

import com.charmquiz.backend.model.DerivedProfile;
import com.charmquiz.backend.model.RecommendationSectionType;
import com.charmquiz.backend.model.RuleRecommendation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class SupabaseRecommendationRuleService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public SupabaseRecommendationRuleService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.publishable-key}") String publishableKey,
            ObjectMapper objectMapper) {
        this(RestClient.builder()
                .baseUrl(supabaseUrl + "/rest/v1")
                .defaultHeader("apikey", publishableKey)
                .build(), objectMapper);
    }

    SupabaseRecommendationRuleService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public List<RuleRecommendation> getMatches(DerivedProfile profile) {
        String rawJson = restClient.post()
                .uri("/rpc/get_grouped_rule_recommendations")
                .body(new RpcRequest(
                        profile.nameInitial(),
                        profile.zodiac(),
                        profile.lifePath(),
                        profile.interests(),
                        profile.favoriteAnimal(),
                        profile.favoriteColor()
                ))
                .retrieve()
                .body(String.class);

        try {
            List<Map<String, Object>> rows = objectMapper.readValue(
                    rawJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );

            return rows.stream()
                    .map(this::toRuleRecommendation)
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse grouped recommendation rules", exception);
        }
    }

    private RuleRecommendation toRuleRecommendation(Map<String, Object> row) {
        return new RuleRecommendation(
                RecommendationSectionType.valueOf((String) row.get("section")),
                (String) row.get("matched_field"),
                (String) row.get("matched_value"),
                ((Number) row.get("priority")).intValue(),
                row.get("charm_id").toString()
        );
    }

    private record RpcRequest(
            String p_name_initial,
            String p_zodiac,
            String p_life_path,
            List<String> p_interests,
            String p_favorite_animal,
            String p_favorite_color
    ) {
    }
}
