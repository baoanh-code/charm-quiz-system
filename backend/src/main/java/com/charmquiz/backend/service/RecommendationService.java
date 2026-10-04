package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.RecommendationRequest;
import com.charmquiz.backend.dto.RecommendationResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class RecommendationService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RecommendationService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.publishable-key}") String publishableKey,
            ObjectMapper objectMapper) {

        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl + "/rest/v1")
                .defaultHeader("apikey", publishableKey)
                .build();

        this.objectMapper = objectMapper;
    }

    public RecommendationResponse getRuleRecommendation(
            RecommendationRequest request) {

        String rawJson = restClient.post()
                .uri("/rpc/get_rule_recommendation")
                .body(new RpcRequest(
                        request.getFullName(),
                        request.getInterests(),
                        request.getFavoriteMusic(),
                        request.getFavoriteSport()
                ))
                .retrieve()
                .body(String.class);

        try {
            List<Map<String, Object>> results =
                    objectMapper.readValue(
                            rawJson,
                            new TypeReference<List<Map<String, Object>>>() {
                            }
                    );

            if (results.isEmpty()) {
                return new RecommendationResponse(
                        "NONE",
                        false,
                        null,
                        null,
                        null,
                        null
                );
            }

            Map<String, Object> result = results.getFirst();

            RecommendationResponse.CharmData charm =
                    new RecommendationResponse.CharmData(
                            (String) result.get("charm_id"),
                            (String) result.get("charm_name"),
                            (String) result.get("charm_description"),
                            (String) result.get("image_url"),
                            (String) result.get("category")
                    );

            Integer priority = null;

            if (result.get("priority") != null) {
                priority = ((Number) result.get("priority")).intValue();
            }

            return new RecommendationResponse(
                    "RULE",
                    true,
                    charm,
                    (String) result.get("matched_field"),
                    (String) result.get("matched_value"),
                    priority
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse Supabase recommendation response",
                    e
            );
        }
    }

    private record RpcRequest(
            String p_full_name,
            String p_interests,
            String p_favorite_music,
            String p_favorite_sport
    ) {
    }
}