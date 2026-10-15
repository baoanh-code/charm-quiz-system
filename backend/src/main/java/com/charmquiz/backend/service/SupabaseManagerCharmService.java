package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.ManagerCharmResponse;
import com.charmquiz.backend.dto.ManagerCharmUpsertRequest;
import com.charmquiz.backend.exception.ManagerCharmNotFoundException;
import com.charmquiz.backend.exception.ManagerConflictException;
import com.charmquiz.backend.exception.ManagerUpstreamException;
import com.charmquiz.backend.exception.ManagerValidationException;
import com.charmquiz.backend.model.CharmStatus;
import com.charmquiz.backend.model.CharmStockStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class SupabaseManagerCharmService {

    static final String SELECT_COLUMNS =
            "id,name,description,image_url,category,tags,status,stock_status";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public SupabaseManagerCharmService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key}") String secretKey,
            ObjectMapper objectMapper) {
        this(RestClient.builder()
                .baseUrl(supabaseUrl + "/rest/v1")
                .defaultHeader("apikey", secretKey)
                .build(), objectMapper);
    }

    SupabaseManagerCharmService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public List<ManagerCharmResponse> getAllCharms() {
        String rawJson = execute(() -> restClient.get()
                .uri("/charm?select=" + SELECT_COLUMNS + "&order=name.asc.nullslast,id.asc")
                .retrieve()
                .body(String.class));
        return parseRows(rawJson).stream().map(this::toResponse).toList();
    }

    public ManagerCharmResponse getCharm(UUID id) {
        String rawJson = execute(() -> restClient.get()
                .uri(charmByIdUri(id))
                .retrieve()
                .body(String.class));
        return exactlyOne(rawJson, true);
    }

    public ManagerCharmResponse createCharm(ManagerCharmUpsertRequest request) {
        UUID id = UUID.randomUUID();
        Map<String, Object> body = writeBody(request);
        body.put("id", id.toString());

        String rawJson = execute(() -> restClient.post()
                .uri("/charm?select=" + SELECT_COLUMNS)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Prefer", "return=representation")
                .body(body)
                .retrieve()
                .body(String.class));
        return exactlyOne(rawJson, false);
    }

    public ManagerCharmResponse updateCharm(UUID id, ManagerCharmUpsertRequest request) {
        return patchCharm(id, writeBody(request));
    }

    public ManagerCharmResponse updateStatus(UUID id, CharmStatus status) {
        return patchCharm(id, Map.of("status", status.name()));
    }

    public ManagerCharmResponse updateStockStatus(UUID id, CharmStockStatus stockStatus) {
        return patchCharm(id, Map.of("stock_status", stockStatus.name()));
    }

    private ManagerCharmResponse patchCharm(UUID id, Map<String, Object> body) {
        String rawJson = execute(() -> restClient.patch()
                .uri(charmByIdUri(id))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Prefer", "return=representation")
                .body(body)
                .retrieve()
                .body(String.class));
        return exactlyOne(rawJson, true);
    }

    private String charmByIdUri(UUID id) {
        return "/charm?id=eq." + id + "&select=" + SELECT_COLUMNS + "&limit=1";
    }

    private Map<String, Object> writeBody(ManagerCharmUpsertRequest request) {
        validateHttpsUrl(request.imageUrl());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", request.name().trim());
        body.put("description", nullableTrimmed(request.description()));
        body.put("image_url", request.imageUrl().trim());
        body.put("category", nullableTrimmed(request.category()));
        body.put("tags", String.join(",", normalizeTags(request.tags())));
        body.put("status", request.status().name());
        body.put("stock_status", request.stockStatus().name());
        return body;
    }

    private void validateHttpsUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new ManagerValidationException("imageUrl must be a valid HTTPS URL.");
            }
        } catch (IllegalArgumentException exception) {
            throw new ManagerValidationException("imageUrl must be a valid HTTPS URL.");
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }

        Map<String, String> distinct = new LinkedHashMap<>();
        for (String rawTag : tags) {
            String tag = rawTag.trim();
            if (tag.contains(",")) {
                throw new ManagerValidationException("Tags must not contain commas.");
            }
            distinct.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
        }
        return List.copyOf(distinct.values());
    }

    private ManagerCharmResponse exactlyOne(String rawJson, boolean emptyIsNotFound) {
        List<Map<String, Object>> rows = parseRows(rawJson);
        if (rows.isEmpty() && emptyIsNotFound) {
            throw new ManagerCharmNotFoundException();
        }
        if (rows.size() != 1) {
            throw new ManagerUpstreamException("Unexpected charm response from Supabase.");
        }
        return toResponse(rows.getFirst());
    }

    private List<Map<String, Object>> parseRows(String rawJson) {
        try {
            return objectMapper.readValue(
                    rawJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );
        } catch (Exception exception) {
            throw new ManagerUpstreamException("Invalid charm response from Supabase.", exception);
        }
    }

    private ManagerCharmResponse toResponse(Map<String, Object> row) {
        try {
            String id = UUID.fromString(requiredString(row, "id")).toString();
            String name = requiredString(row, "name");
            String imageUrl = requiredString(row, "image_url");
            CharmStatus status = CharmStatus.valueOf(requiredString(row, "status"));
            CharmStockStatus stockStatus = CharmStockStatus.valueOf(
                    requiredString(row, "stock_status")
            );

            return new ManagerCharmResponse(
                    id,
                    name,
                    nullableTrimmed(asNullableString(row.get("description"))),
                    imageUrl,
                    nullableTrimmed(asNullableString(row.get("category"))),
                    parseTags(asNullableString(row.get("tags"))),
                    status,
                    stockStatus
            );
        } catch (ManagerUpstreamException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ManagerUpstreamException("Invalid charm data from Supabase.", exception);
        }
    }

    private List<String> parseTags(String rawTags) {
        if (rawTags == null || rawTags.isBlank()) {
            return List.of();
        }

        List<String> tags = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (String rawTag : rawTags.split(",")) {
            String tag = rawTag.trim();
            String key = tag.toLowerCase(Locale.ROOT);
            if (!tag.isBlank() && seen.putIfAbsent(key, Boolean.TRUE) == null) {
                tags.add(tag);
            }
        }
        return List.copyOf(tags);
    }

    private String requiredString(Map<String, Object> row, String column) {
        String value = asNullableString(row.get(column));
        if (value == null || value.isBlank()) {
            throw new ManagerUpstreamException("Missing required charm field from Supabase.");
        }
        return value;
    }

    private String nullableTrimmed(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String asNullableString(Object value) {
        return value == null ? null : value.toString();
    }

    private String execute(Supplier<String> request) {
        try {
            return request.get();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 409) {
                throw new ManagerConflictException("Charm operation conflicts with existing data.", exception);
            }
            throw new ManagerUpstreamException("Supabase charm request failed.", exception);
        } catch (RestClientException exception) {
            throw new ManagerUpstreamException("Supabase charm request failed.", exception);
        }
    }
}
