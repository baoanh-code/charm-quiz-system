package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.ManagerRecommendationRuleResponse;
import com.charmquiz.backend.dto.ManagerRecommendationRuleUpsertRequest;
import com.charmquiz.backend.exception.ManagerRecommendationRuleNotFoundException;
import com.charmquiz.backend.exception.ManagerRuleCharmNotFoundException;
import com.charmquiz.backend.exception.ManagerRuleConflictException;
import com.charmquiz.backend.exception.ManagerUpstreamException;
import com.charmquiz.backend.model.RecommendationRuleField;
import com.charmquiz.backend.model.RecommendationRuleOperator;
import com.charmquiz.backend.model.RecommendationRuleStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class SupabaseManagerRecommendationRuleService {

    static final String SELECT_COLUMNS =
            "id,charm_id,field,operator,value,priority,status";
    static final String MANAGED_FIELD_FILTER =
            "field=in.(NAME_INITIAL,ZODIAC,LIFE_PATH,INTEREST,FAVORITE_ANIMAL,FAVORITE_COLOR)";

    private static final String DUPLICATE_SELECT_COLUMNS = "id,field,operator,value";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public SupabaseManagerRecommendationRuleService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key}") String secretKey,
            ObjectMapper objectMapper) {
        this(RestClient.builder()
                .baseUrl(supabaseUrl + "/rest/v1")
                .defaultHeader("apikey", secretKey)
                .build(), objectMapper);
    }

    SupabaseManagerRecommendationRuleService(
            RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public List<ManagerRecommendationRuleResponse> getAllRules() {
        String rawJson = execute(() -> restClient.get()
                .uri("/recommendation_rule?select=" + SELECT_COLUMNS
                        + "&" + MANAGED_FIELD_FILTER
                        + "&order=priority.desc.nullslast,id.asc")
                .retrieve()
                .body(String.class));
        return parseRows(rawJson).stream().map(this::toResponse).toList();
    }

    public ManagerRecommendationRuleResponse getRule(UUID id) {
        return getExistingManagedRule(id);
    }

    public ManagerRecommendationRuleResponse createRule(
            ManagerRecommendationRuleUpsertRequest request) {
        ensureCharmExists(request.charmId());
        ensureNoDuplicate(request, null);

        Map<String, Object> body = writeBody(request);
        body.put("id", UUID.randomUUID().toString());

        String rawJson = execute(() -> restClient.post()
                .uri("/recommendation_rule?select=" + SELECT_COLUMNS)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Prefer", "return=representation")
                .body(body)
                .retrieve()
                .body(String.class));
        return exactlyOne(rawJson, false);
    }

    public ManagerRecommendationRuleResponse updateRule(
            UUID id,
            ManagerRecommendationRuleUpsertRequest request) {
        getExistingManagedRule(id);
        ensureCharmExists(request.charmId());
        ensureNoDuplicate(request, id);
        return patchRule(id, writeBody(request));
    }

    public ManagerRecommendationRuleResponse updateStatus(
            UUID id,
            RecommendationRuleStatus status) {
        return patchRule(id, Map.of("status", status.name()));
    }

    private ManagerRecommendationRuleResponse getExistingManagedRule(UUID id) {
        String rawJson = execute(() -> restClient.get()
                .uri(ruleByIdUri(id))
                .retrieve()
                .body(String.class));
        return exactlyOne(rawJson, true);
    }

    private ManagerRecommendationRuleResponse patchRule(
            UUID id,
            Map<String, Object> body) {
        String rawJson = execute(() -> restClient.patch()
                .uri(ruleByIdUri(id))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Prefer", "return=representation")
                .body(body)
                .retrieve()
                .body(String.class));
        return exactlyOne(rawJson, true);
    }

    private String ruleByIdUri(UUID id) {
        return "/recommendation_rule?id=eq." + id
                + "&" + MANAGED_FIELD_FILTER
                + "&select=" + SELECT_COLUMNS
                + "&limit=1";
    }

    private void ensureCharmExists(UUID charmId) {
        String rawJson = execute(() -> restClient.get()
                .uri("/charm?id=eq." + charmId + "&select=id&limit=1")
                .retrieve()
                .body(String.class));
        List<Map<String, Object>> rows = parseRows(rawJson);
        if (rows.isEmpty()) {
            throw new ManagerRuleCharmNotFoundException();
        }
        if (rows.size() != 1 || !charmId.equals(parseUuid(rows.getFirst(), "id"))) {
            throw new ManagerUpstreamException("Unexpected charm response from Supabase.");
        }
    }

    private void ensureNoDuplicate(
            ManagerRecommendationRuleUpsertRequest request,
            UUID excludedId) {
        StringBuilder uri = new StringBuilder("/recommendation_rule?charm_id=eq.")
                .append(request.charmId())
                .append("&field=eq.")
                .append(request.field().name());
        if (excludedId != null) {
            uri.append("&id=neq.").append(excludedId);
        }
        uri.append("&select=").append(DUPLICATE_SELECT_COLUMNS);

        String rawJson = execute(() -> restClient.get()
                .uri(uri.toString())
                .retrieve()
                .body(String.class));

        String candidateValue = request.value().trim();
        boolean duplicate = parseRows(rawJson).stream().anyMatch(row ->
                request.field() == parseField(row.get("field"))
                        && request.operator() == parseOperator(row.get("operator"))
                        && candidateValue.equalsIgnoreCase(requiredString(row, "value").trim()));
        if (duplicate) {
            throw new ManagerRuleConflictException(
                    "An equivalent recommendation rule already exists for this charm."
            );
        }
    }

    private Map<String, Object> writeBody(ManagerRecommendationRuleUpsertRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("charm_id", request.charmId().toString());
        body.put("field", request.field().name());
        body.put("operator", request.operator().name());
        body.put("value", request.value().trim());
        body.put("priority", request.priority());
        body.put("status", request.status().name());
        return body;
    }

    private ManagerRecommendationRuleResponse exactlyOne(
            String rawJson,
            boolean emptyIsNotFound) {
        List<Map<String, Object>> rows = parseRows(rawJson);
        if (rows.isEmpty() && emptyIsNotFound) {
            throw new ManagerRecommendationRuleNotFoundException();
        }
        if (rows.size() != 1) {
            throw new ManagerUpstreamException(
                    "Unexpected recommendation rule response from Supabase."
            );
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
            throw new ManagerUpstreamException(
                    "Invalid recommendation rule response from Supabase.",
                    exception
            );
        }
    }

    private ManagerRecommendationRuleResponse toResponse(Map<String, Object> row) {
        try {
            UUID id = parseUuid(row, "id");
            UUID charmId = parseUuid(row, "charm_id");
            RecommendationRuleField field = parseField(row.get("field"));
            RecommendationRuleOperator operator = parseOperator(row.get("operator"));
            String value = requiredString(row, "value").trim();
            if (value.isBlank()) {
                throw new IllegalArgumentException("Blank rule value");
            }
            int priority = parsePriority(row.get("priority"));
            RecommendationRuleStatus status = RecommendationRuleStatus.valueOf(
                    normalizedEnumValue(row.get("status"))
            );

            return new ManagerRecommendationRuleResponse(
                    id.toString(),
                    charmId.toString(),
                    field,
                    operator,
                    value,
                    priority,
                    status
            );
        } catch (ManagerUpstreamException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ManagerUpstreamException(
                    "Invalid recommendation rule data from Supabase.",
                    exception
            );
        }
    }

    private RecommendationRuleField parseField(Object value) {
        return RecommendationRuleField.valueOf(normalizedEnumValue(value));
    }

    private RecommendationRuleOperator parseOperator(Object value) {
        return RecommendationRuleOperator.valueOf(normalizedEnumValue(value));
    }

    private int parsePriority(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Invalid priority");
        }
        long priority = number.longValue();
        if (priority < Integer.MIN_VALUE || priority > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Priority outside signed 32-bit range");
        }
        return (int) priority;
    }

    private UUID parseUuid(Map<String, Object> row, String column) {
        return UUID.fromString(requiredString(row, column));
    }

    private String requiredString(Map<String, Object> row, String column) {
        Object rawValue = row.get(column);
        if (rawValue == null || rawValue.toString().isBlank()) {
            throw new ManagerUpstreamException(
                    "Missing required recommendation rule field from Supabase."
            );
        }
        return rawValue.toString();
    }

    private String normalizedEnumValue(Object value) {
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing enum value");
        }
        return value.toString().trim().toUpperCase(Locale.ROOT);
    }

    private String execute(Supplier<String> request) {
        try {
            return request.get();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 409) {
                throw new ManagerRuleConflictException(
                        "Recommendation rule operation conflicts with existing data.",
                        exception
                );
            }
            throw new ManagerUpstreamException(
                    "Supabase recommendation rule request failed.",
                    exception
            );
        } catch (RestClientException exception) {
            throw new ManagerUpstreamException(
                    "Supabase recommendation rule request failed.",
                    exception
            );
        }
    }
}
