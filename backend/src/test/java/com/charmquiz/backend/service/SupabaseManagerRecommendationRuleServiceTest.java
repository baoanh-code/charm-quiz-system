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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseManagerRecommendationRuleServiceTest {

    private static final UUID RULE_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CHARM_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_CHARM_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SECRET = "test-secret-value";

    private MockRestServiceServer server;
    private SupabaseManagerRecommendationRuleService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://project.supabase.co/rest/v1")
                .defaultHeader("apikey", SECRET);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new SupabaseManagerRecommendationRuleService(
                builder.build(),
                new ObjectMapper()
        );
    }

    @Test
    void listsOnlyManagedFieldsWithExplicitColumnsStableOrderAndBothStatuses() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/rest/v1/recommendation_rule");
                    assertThat(request.getURI().getQuery())
                            .contains("select="
                                    + SupabaseManagerRecommendationRuleService.SELECT_COLUMNS)
                            .contains(SupabaseManagerRecommendationRuleService.MANAGED_FIELD_FILTER)
                            .contains("order=priority.desc.nullslast,id.asc")
                            .doesNotContain("status=eq.ACTIVE")
                            .doesNotContain("select=*")
                            .doesNotContain("favorite_music")
                            .doesNotContain("favorite_sport")
                            .doesNotContain(SECRET);
                    assertThat(request.getHeaders().getFirst("apikey")).isEqualTo(SECRET);
                    assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
                })
                .andRespond(withSuccess("["
                                + rowJson(RULE_ID, CHARM_ID, " interest ", " contains ",
                                " music ", 10, " active ")
                                + ","
                                + rowJson(
                                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                                OTHER_CHARM_ID,
                                "ZODIAC", "EQUALS", "Leo", -2, "INACTIVE")
                                + "]",
                        MediaType.APPLICATION_JSON));

        List<ManagerRecommendationRuleResponse> result = service.getAllRules();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ManagerRecommendationRuleResponse::status)
                .containsExactly(
                        RecommendationRuleStatus.ACTIVE,
                        RecommendationRuleStatus.INACTIVE
                );
        assertThat(result.getFirst().field()).isEqualTo(RecommendationRuleField.INTEREST);
        assertThat(result.getFirst().operator())
                .isEqualTo(RecommendationRuleOperator.CONTAINS);
        assertThat(result.getFirst().value()).isEqualTo("music");
        server.verify();
    }

    @Test
    void getsManagedRuleUsingExactUuidAndManagedFieldFilters() {
        server.expect(request -> assertThat(request.getURI().getQuery())
                        .isEqualTo("id=eq." + RULE_ID
                                + "&" + SupabaseManagerRecommendationRuleService.MANAGED_FIELD_FILTER
                                + "&select="
                                + SupabaseManagerRecommendationRuleService.SELECT_COLUMNS
                                + "&limit=1"))
                .andRespond(withSuccess("[" + managedRow() + "]", MediaType.APPLICATION_JSON));

        assertThat(service.getRule(RULE_ID).id()).isEqualTo(RULE_ID.toString());
        server.verify();
    }

    @Test
    void legacyRuleIsOutsideScopeForGetUpdateAndStatusPatch() {
        server.expect(request -> assertManagedIdFilter(request.getURI().getQuery()))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.getRule(RULE_ID))
                .isInstanceOf(ManagerRecommendationRuleNotFoundException.class);
        server.verify();

        server.reset();
        server.expect(request -> assertManagedIdFilter(request.getURI().getQuery()))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.updateRule(RULE_ID, request(CHARM_ID)))
                .isInstanceOf(ManagerRecommendationRuleNotFoundException.class);
        server.verify();

        server.reset();
        server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("PATCH");
                    assertManagedIdFilter(request.getURI().getQuery());
                })
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.updateStatus(
                RULE_ID,
                RecommendationRuleStatus.INACTIVE
        )).isInstanceOf(ManagerRecommendationRuleNotFoundException.class);
        server.verify();
    }

    @Test
    void createsWithGeneratedUuidTrimmedValueAndNormalizedEnums() {
        expectCharmExists(CHARM_ID);
        expectNoDuplicate(CHARM_ID, null);
        server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("POST");
                    assertThat(request.getURI().getQuery())
                            .isEqualTo("select="
                                    + SupabaseManagerRecommendationRuleService.SELECT_COLUMNS);
                    assertThat(request.getHeaders().getFirst("Prefer"))
                            .isEqualTo("return=representation");
                })
                .andExpect(content().string(containsString("\"id\":")))
                .andExpect(content().string(containsString("\"field\":\"INTEREST\"")))
                .andExpect(content().string(containsString("\"operator\":\"CONTAINS\"")))
                .andExpect(content().string(containsString("\"value\":\"Music\"")))
                .andRespond(withSuccess("[" + managedRow() + "]", MediaType.APPLICATION_JSON));

        ManagerRecommendationRuleResponse result = service.createRule(new ManagerRecommendationRuleUpsertRequest(
                CHARM_ID,
                RecommendationRuleField.INTEREST,
                RecommendationRuleOperator.CONTAINS,
                "  Music  ",
                -1,
                RecommendationRuleStatus.ACTIVE
        ));

        assertThat(result.id()).isEqualTo(RULE_ID.toString());
        server.verify();
    }

    @Test
    void charmExistenceCheckAllowsAnyAvailabilityAndMissingCharmIsNotFound() {
        expectCharmExists(CHARM_ID);
        expectNoDuplicate(CHARM_ID, null);
        server.expect(request -> {
                }).andRespond(withSuccess("[" + managedRow() + "]", MediaType.APPLICATION_JSON));
        service.createRule(request(CHARM_ID));
        server.verify();

        server.reset();
        server.expect(request -> assertThat(request.getURI().getQuery())
                        .isEqualTo("id=eq." + CHARM_ID + "&select=id&limit=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.createRule(request(CHARM_ID)))
                .isInstanceOf(ManagerRuleCharmNotFoundException.class);
        server.verify();
    }

    @Test
    void exactDuplicateIsConflictButDifferentCharmAndLegacyFieldAreNotDuplicates() {
        expectCharmExists(CHARM_ID);
        server.expect(request -> {
                    assertThat(request.getURI().getQuery())
                            .contains("charm_id=eq." + CHARM_ID)
                            .contains("field=eq.INTEREST")
                            .doesNotContain("interests");
                })
                .andRespond(withSuccess("["
                                + duplicateRow(RULE_ID, "INTEREST", "CONTAINS", " mUsIc ")
                                + "]",
                        MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.createRule(request(CHARM_ID)))
                .isInstanceOf(ManagerRuleConflictException.class);
        server.verify();

        server.reset();
        expectCharmExists(OTHER_CHARM_ID);
        expectNoDuplicate(OTHER_CHARM_ID, null);
        server.expect(request -> {
                }).andRespond(withSuccess("["
                                + rowJson(RULE_ID, OTHER_CHARM_ID, "INTEREST", "CONTAINS",
                                "music", 10, "ACTIVE")
                                + "]",
                        MediaType.APPLICATION_JSON));
        assertThat(service.createRule(request(OTHER_CHARM_ID)).charmId())
                .isEqualTo(OTHER_CHARM_ID.toString());
        server.verify();
    }

    @Test
    void updateUsesScopedLookupDuplicateExclusionAndScopedPatch() {
        server.expect(request -> assertManagedIdFilter(request.getURI().getQuery()))
                .andRespond(withSuccess("[" + managedRow() + "]", MediaType.APPLICATION_JSON));
        expectCharmExists(CHARM_ID);
        expectNoDuplicate(CHARM_ID, RULE_ID);
        server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("PATCH");
                    assertManagedIdFilter(request.getURI().getQuery());
                })
                .andExpect(content().string(containsString("\"priority\":10")))
                .andRespond(withSuccess("[" + managedRow() + "]", MediaType.APPLICATION_JSON));

        service.updateRule(RULE_ID, request(CHARM_ID));
        server.verify();
    }

    @Test
    void statusPatchContainsOnlyStatusAndUsesManagedBoundary() {
        server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("PATCH");
                    assertManagedIdFilter(request.getURI().getQuery());
                })
                .andExpect(content().string("{\"status\":\"INACTIVE\"}"))
                .andRespond(withSuccess("["
                                + rowJson(RULE_ID, CHARM_ID, "INTEREST", "CONTAINS",
                                "music", 10, "INACTIVE")
                                + "]",
                        MediaType.APPLICATION_JSON));

        assertThat(service.updateStatus(RULE_ID, RecommendationRuleStatus.INACTIVE).status())
                .isEqualTo(RecommendationRuleStatus.INACTIVE);
        server.verify();
    }

    @Test
    void deleteUsesExactUuidAndManagedFieldBoundary() {
        server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("DELETE");
                    assertManagedIdFilter(request.getURI().getQuery());
                    assertThat(request.getHeaders().getFirst("Prefer"))
                            .isEqualTo("return=representation");
                })
                .andRespond(withSuccess("[" + managedRow() + "]", MediaType.APPLICATION_JSON));

        service.deleteRule(RULE_ID);
        server.verify();

        server.reset();
        server.expect(request -> assertManagedIdFilter(request.getURI().getQuery()))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.deleteRule(RULE_ID))
                .isInstanceOf(ManagerRecommendationRuleNotFoundException.class);
        server.verify();
    }

    @Test
    void malformedAndUpstreamFailuresAreMappedWithoutSensitiveDetails() {
        server.expect(request -> {
                }).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(service::getAllRules)
                .isInstanceOf(ManagerUpstreamException.class);
        server.verify();

        server.reset();
        server.expect(request -> {
                }).andRespond(withServerError());
        assertThatThrownBy(service::getAllRules)
                .isInstanceOf(ManagerUpstreamException.class)
                .hasMessage("Supabase recommendation rule request failed.");
        server.verify();

        server.reset();
        server.expect(request -> {
                }).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("sensitive upstream detail")
                        .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(service::getAllRules)
                .isInstanceOf(ManagerUpstreamException.class)
                .hasMessageNotContaining("sensitive upstream detail");
        server.verify();
    }

    @Test
    void upstreamConflictIsMappedToRuleConflict() {
        expectCharmExists(CHARM_ID);
        expectNoDuplicate(CHARM_ID, null);
        server.expect(request -> {
                }).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> service.createRule(request(CHARM_ID)))
                .isInstanceOf(ManagerRuleConflictException.class);
        server.verify();
    }

    private void expectCharmExists(UUID charmId) {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/rest/v1/charm");
                    assertThat(request.getURI().getQuery())
                            .isEqualTo("id=eq." + charmId + "&select=id&limit=1")
                            .doesNotContain("status")
                            .doesNotContain("stock_status");
                })
                .andRespond(withSuccess("[{\"id\":\"" + charmId + "\"}]",
                        MediaType.APPLICATION_JSON));
    }

    private void expectNoDuplicate(UUID charmId, UUID excludedId) {
        server.expect(request -> {
                    String query = request.getURI().getQuery();
                    assertThat(query)
                            .contains("charm_id=eq." + charmId)
                            .contains("field=eq.INTEREST")
                            .contains("select=id,field,operator,value")
                            .doesNotContain("interests");
                    if (excludedId != null) {
                        assertThat(query).contains("id=neq." + excludedId);
                    }
                })
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    }

    private void assertManagedIdFilter(String query) {
        assertThat(query)
                .contains("id=eq." + RULE_ID)
                .contains(SupabaseManagerRecommendationRuleService.MANAGED_FIELD_FILTER)
                .contains("select=" + SupabaseManagerRecommendationRuleService.SELECT_COLUMNS)
                .contains("limit=1");
    }

    private ManagerRecommendationRuleUpsertRequest request(UUID charmId) {
        return new ManagerRecommendationRuleUpsertRequest(
                charmId,
                RecommendationRuleField.INTEREST,
                RecommendationRuleOperator.CONTAINS,
                "music",
                10,
                RecommendationRuleStatus.ACTIVE
        );
    }

    private String managedRow() {
        return rowJson(
                RULE_ID,
                CHARM_ID,
                "INTEREST",
                "CONTAINS",
                "music",
                10,
                "ACTIVE"
        );
    }

    private String rowJson(
            UUID id,
            UUID charmId,
            String field,
            String operator,
            String value,
            int priority,
            String status) {
        return """
                {
                  "id": "%s",
                  "charm_id": "%s",
                  "field": "%s",
                  "operator": "%s",
                  "value": "%s",
                  "priority": %d,
                  "status": "%s"
                }
                """.formatted(id, charmId, field, operator, value, priority, status);
    }

    private String duplicateRow(UUID id, String field, String operator, String value) {
        return """
                {
                  "id": "%s",
                  "field": "%s",
                  "operator": "%s",
                  "value": "%s"
                }
                """.formatted(id, field, operator, value);
    }
}
