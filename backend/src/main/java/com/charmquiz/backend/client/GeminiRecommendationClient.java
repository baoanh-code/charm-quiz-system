package com.charmquiz.backend.client;

import com.charmquiz.backend.config.GeminiProperties;
import com.charmquiz.backend.model.EligibleCharm;
import com.charmquiz.backend.model.RecommendationSectionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeminiRecommendationClient {

    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final GeminiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiRecommendationClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this(properties, createRestClient(properties), objectMapper);
    }

    GeminiRecommendationClient(
            GeminiProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public Map<RecommendationSectionType, List<String>> recommend(
            Map<RecommendationSectionType, List<String>> unresolvedInputs,
            List<EligibleCharm> allowedCatalog) {
        if (!isConfigured()) {
            throw new AiRecommendationException("Gemini API key is not configured");
        }

        try {
            String prompt = buildPrompt(unresolvedInputs, allowedCatalog);
            Map<String, Object> response = restClient.post()
                    .uri("/{model}:generateContent", properties.getModel())
                    .header("x-goog-api-key", properties.getApiKey())
                    .body(requestBody(prompt))
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {
                    });

            return parseResponse(response);
        } catch (AiRecommendationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiRecommendationException("Gemini recommendation request failed", exception);
        }
    }

    private String buildPrompt(
            Map<RecommendationSectionType, List<String>> unresolvedInputs,
            List<EligibleCharm> allowedCatalog) throws Exception {
        List<Map<String, Object>> catalog = allowedCatalog.stream()
                .map(charm -> Map.<String, Object>of(
                        "id", charm.id(),
                        "name", nullSafe(charm.name()),
                        "description", nullSafe(charm.description()),
                        "category", nullSafe(charm.category()),
                        "tags", charm.normalizedTags()
                ))
                .toList();

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("instruction", "Select up to 3 suitable charm IDs for each requested section. "
                + "Use only IDs from allowedCatalog. Do not invent IDs or return charm details.");
        task.put("sections", unresolvedInputs);
        task.put("allowedCatalog", catalog);
        return objectMapper.writeValueAsString(task);
    }

    private Map<String, Object> requestBody(String prompt) {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "sections", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "type", Map.of("type", "string"),
                                                "charmIds", Map.of(
                                                        "type", "array",
                                                        "items", Map.of("type", "string"),
                                                        "maxItems", 3
                                                )
                                        ),
                                        "required", List.of("type", "charmIds")
                                )
                        )
                ),
                "required", List.of("sections")
        );

        return Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseJsonSchema", schema,
                        "temperature", 0
                )
        );
    }

    @SuppressWarnings("unchecked")
    private Map<RecommendationSectionType, List<String>> parseResponse(Map<String, Object> response)
            throws Exception {
        if (response == null) {
            throw new AiRecommendationException("Gemini returned an empty response");
        }

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new AiRecommendationException("Gemini returned no candidate");
        }

        Map<String, Object> content = (Map<String, Object>) candidates.getFirst().get("content");
        List<Map<String, Object>> parts = content == null
                ? null
                : (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty() || !(parts.getFirst().get("text") instanceof String json)) {
            throw new AiRecommendationException("Gemini returned no structured content");
        }

        Map<String, Object> parsed = objectMapper.readValue(
                json,
                new TypeReference<Map<String, Object>>() {
                }
        );
        Object rawSections = parsed.get("sections");
        if (!(rawSections instanceof List<?> sections)) {
            throw new AiRecommendationException("Gemini response has no sections");
        }

        Map<RecommendationSectionType, List<String>> result =
                new EnumMap<>(RecommendationSectionType.class);
        for (Object rawSection : sections) {
            if (!(rawSection instanceof Map<?, ?> section)
                    || !(section.get("type") instanceof String type)
                    || !(section.get("charmIds") instanceof List<?> ids)) {
                throw new AiRecommendationException("Gemini section is invalid");
            }

            RecommendationSectionType sectionType;
            try {
                sectionType = RecommendationSectionType.valueOf(type);
            } catch (IllegalArgumentException exception) {
                throw new AiRecommendationException("Gemini returned an unknown section", exception);
            }

            List<String> charmIds = ids.stream()
                    .map(id -> {
                        if (!(id instanceof String stringId)) {
                            throw new AiRecommendationException("Gemini returned a non-string charm ID");
                        }
                        return stringId;
                    })
                    .toList();
            result.put(sectionType, charmIds);
        }
        return result;
    }

    private static RestClient createRestClient(GeminiProperties properties) {
        Duration timeout = Duration.ofMillis(properties.getTimeoutMillis());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(GEMINI_BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
