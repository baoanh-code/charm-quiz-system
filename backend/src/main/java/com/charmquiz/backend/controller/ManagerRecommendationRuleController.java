package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.ManagerRecommendationRuleResponse;
import com.charmquiz.backend.dto.ManagerRecommendationRuleUpsertRequest;
import com.charmquiz.backend.dto.RecommendationRuleStatusUpdateRequest;
import com.charmquiz.backend.service.SupabaseManagerRecommendationRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/manager/recommendation-rules")
public class ManagerRecommendationRuleController {

    private final SupabaseManagerRecommendationRuleService ruleService;

    public ManagerRecommendationRuleController(
            SupabaseManagerRecommendationRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public List<ManagerRecommendationRuleResponse> getRules() {
        return ruleService.getAllRules();
    }

    @GetMapping("/{id}")
    public ManagerRecommendationRuleResponse getRule(@PathVariable UUID id) {
        return ruleService.getRule(id);
    }

    @PostMapping
    public ResponseEntity<ManagerRecommendationRuleResponse> createRule(
            @Valid @RequestBody ManagerRecommendationRuleUpsertRequest request) {
        ManagerRecommendationRuleResponse created = ruleService.createRule(request);
        return ResponseEntity
                .created(URI.create("/api/manager/recommendation-rules/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ManagerRecommendationRuleResponse updateRule(
            @PathVariable UUID id,
            @Valid @RequestBody ManagerRecommendationRuleUpsertRequest request) {
        return ruleService.updateRule(id, request);
    }

    @PatchMapping("/{id}/status")
    public ManagerRecommendationRuleResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody RecommendationRuleStatusUpdateRequest request) {
        return ruleService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        ruleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
