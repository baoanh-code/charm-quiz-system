package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.RecommendationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class RecommendationService {

    private final RestClient restClient;

    public RecommendationService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.publishable-key}") String publishableKey) {

        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl + "/rest/v1")
                .defaultHeader("apikey", publishableKey)
                .build();
    }

    public String getRuleRecommendation(RecommendationRequest request) {

        return restClient.post()
                .uri("/rpc/get_rule_recommendation")
                .body(new RpcRequest(
                        request.getFullName(),
                        request.getInterests(),
                        request.getFavoriteMusic(),
                        request.getFavoriteSport()
                ))
                .retrieve()
                .body(String.class);
    }

    private record RpcRequest(
            String p_full_name,
            String p_interests,
            String p_favorite_music,
            String p_favorite_sport
    ) {
    }
}