package com.charmquiz.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SupabaseCharmService {

    private final RestClient restClient;

    public SupabaseCharmService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.publishable-key}") String publishableKey) {

        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl + "/rest/v1")
                .defaultHeader("apikey", publishableKey)
                .build();
    }

    public String getActiveCharms() {
        return restClient.get()
                .uri("/charm?status=eq.ACTIVE&select=*")
                .retrieve()
                .body(String.class);
    }
}