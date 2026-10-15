package com.charmquiz.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SupabaseManagerAuthorizationService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public SupabaseManagerAuthorizationService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key}") String secretKey,
            ObjectMapper objectMapper) {
        this(RestClient.builder()
                .baseUrl(supabaseUrl + "/rest/v1")
                .defaultHeader("apikey", secretKey)
                .build(), objectMapper);
    }

    SupabaseManagerAuthorizationService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public boolean isManager(UUID userId) {
        String rawJson = restClient.get()
                .uri("/manager?id=eq." + userId + "&select=id&limit=1")
                .retrieve()
                .body(String.class);

        try {
            List<Map<String, Object>> rows = objectMapper.readValue(
                    rawJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );

            if (rows.isEmpty()) {
                return false;
            }

            Object returnedId = rows.getFirst().get("id");
            if (returnedId == null || !userId.equals(UUID.fromString(returnedId.toString()))) {
                throw new IllegalStateException("Invalid manager membership response from Supabase");
            }

            return true;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse manager membership from Supabase", exception);
        }
    }
}
