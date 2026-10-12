package com.charmquiz.backend.service;

import com.charmquiz.backend.model.EligibleCharm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class SupabaseCharmService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public SupabaseCharmService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.publishable-key}") String publishableKey,
            ObjectMapper objectMapper) {

        this(RestClient.builder()
                .baseUrl(supabaseUrl + "/rest/v1")
                .defaultHeader("apikey", publishableKey)
                .build(), objectMapper);
    }

    SupabaseCharmService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public String getActiveCharms() {
        return restClient.get()
                .uri("/charm?status=eq.ACTIVE&select=*")
                .retrieve()
                .body(String.class);
    }

    public List<EligibleCharm> getEligibleCharms() {
        String rawJson = restClient.get()
                .uri("/charm?status=eq.ACTIVE&stock_status=eq.IN_STOCK"
                        + "&select=id,name,description,image_url,category,tags,status,stock_status")
                .retrieve()
                .body(String.class);

        try {
            List<Map<String, Object>> rows = objectMapper.readValue(
                    rawJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );

            return rows.stream()
                    .map(this::toEligibleCharm)
                    .filter(EligibleCharm::isEligible)
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse eligible charm catalog", exception);
        }
    }

    private EligibleCharm toEligibleCharm(Map<String, Object> row) {
        return new EligibleCharm(
                row.get("id").toString(),
                (String) row.get("name"),
                (String) row.get("description"),
                (String) row.get("image_url"),
                (String) row.get("category"),
                (String) row.get("tags"),
                (String) row.get("status"),
                (String) row.get("stock_status")
        );
    }
}
