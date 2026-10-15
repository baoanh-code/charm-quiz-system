package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.ManagerRecommendationRuleResponse;
import com.charmquiz.backend.exception.ManagerRecommendationRuleNotFoundException;
import com.charmquiz.backend.exception.ManagerRuleCharmNotFoundException;
import com.charmquiz.backend.exception.ManagerRuleConflictException;
import com.charmquiz.backend.exception.ManagerUpstreamException;
import com.charmquiz.backend.model.RecommendationRuleField;
import com.charmquiz.backend.model.RecommendationRuleOperator;
import com.charmquiz.backend.model.RecommendationRuleStatus;
import com.charmquiz.backend.service.SupabaseManagerRecommendationRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ManagerRecommendationRuleController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class ManagerRecommendationRuleControllerTest {

    private static final UUID RULE_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CHARM_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupabaseManagerRecommendationRuleService ruleService;

    @Test
    void listsActiveAndInactiveManagedRules() throws Exception {
        when(ruleService.getAllRules()).thenReturn(List.of(
                rule(RULE_ID, RecommendationRuleStatus.ACTIVE),
                rule(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                        RecommendationRuleStatus.INACTIVE)
        ));

        mockMvc.perform(get("/api/manager/recommendation-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].field").value("INTEREST"))
                .andExpect(jsonPath("$[0].operator").value("CONTAINS"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].status").value("INACTIVE"))
                .andExpect(jsonPath("$[0].createdAt").doesNotExist());
    }

    @Test
    void getsOneManagedRule() throws Exception {
        when(ruleService.getRule(RULE_ID))
                .thenReturn(rule(RULE_ID, RecommendationRuleStatus.ACTIVE));

        mockMvc.perform(get("/api/manager/recommendation-rules/{id}", RULE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RULE_ID.toString()))
                .andExpect(jsonPath("$.charmId").value(CHARM_ID.toString()));
    }

    @Test
    void createsRuleAndReturnsLocation() throws Exception {
        when(ruleService.createRule(any()))
                .thenReturn(rule(RULE_ID, RecommendationRuleStatus.ACTIVE));

        mockMvc.perform(post("/api/manager/recommendation-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/manager/recommendation-rules/" + RULE_ID
                ))
                .andExpect(jsonPath("$.id").value(RULE_ID.toString()));
    }

    @Test
    void updatesRule() throws Exception {
        when(ruleService.updateRule(any(), any()))
                .thenReturn(rule(RULE_ID, RecommendationRuleStatus.ACTIVE));

        mockMvc.perform(put("/api/manager/recommendation-rules/{id}", RULE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(10));
    }

    @Test
    void patchesOnlyStatusThroughDedicatedEndpoint() throws Exception {
        when(ruleService.updateStatus(RULE_ID, RecommendationRuleStatus.INACTIVE))
                .thenReturn(rule(RULE_ID, RecommendationRuleStatus.INACTIVE));

        mockMvc.perform(patch("/api/manager/recommendation-rules/{id}/status", RULE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(ruleService).updateStatus(RULE_ID, RecommendationRuleStatus.INACTIVE);
    }

    @Test
    void invalidUuidFieldOperatorStatusBlankValueAndPriorityReturnBadRequest()
            throws Exception {
        mockMvc.perform(get("/api/manager/recommendation-rules/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        for (String body : List.of(
                validBody().replace("INTEREST", "interests"),
                validBody().replace("CONTAINS", "MATCHES"),
                validBody().replace("ACTIVE", "DISABLED"),
                validBody().replace("music", "   "),
                validBody().replace("\"priority\": 10", "\"priority\": 2147483648")
        )) {
            mockMvc.perform(post("/api/manager/recommendation-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        verifyNoInteractions(ruleService);
    }

    @Test
    void mapsRuleCharmConflictAndUpstreamErrors() throws Exception {
        when(ruleService.getRule(RULE_ID))
                .thenThrow(new ManagerRecommendationRuleNotFoundException())
                .thenThrow(new ManagerUpstreamException("failed"));
        when(ruleService.createRule(any()))
                .thenThrow(new ManagerRuleCharmNotFoundException())
                .thenThrow(new ManagerRuleConflictException("duplicate"));

        mockMvc.perform(get("/api/manager/recommendation-rules/{id}", RULE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RULE_NOT_FOUND"));
        mockMvc.perform(post("/api/manager/recommendation-rules")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARM_NOT_FOUND"));
        mockMvc.perform(post("/api/manager/recommendation-rules")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RULE_CONFLICT"));
        mockMvc.perform(get("/api/manager/recommendation-rules/{id}", RULE_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
    }

    private ManagerRecommendationRuleResponse rule(
            UUID id,
            RecommendationRuleStatus status) {
        return new ManagerRecommendationRuleResponse(
                id.toString(),
                CHARM_ID.toString(),
                RecommendationRuleField.INTEREST,
                RecommendationRuleOperator.CONTAINS,
                "music",
                10,
                status
        );
    }

    private String validBody() {
        return """
                {
                  "charmId": "11111111-1111-1111-1111-111111111111",
                  "field": "INTEREST",
                  "operator": "CONTAINS",
                  "value": "music",
                  "priority": 10,
                  "status": "ACTIVE"
                }
                """;
    }
}
