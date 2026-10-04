package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.RecommendationRequest;
import com.charmquiz.backend.service.RecommendationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping
    public String getRecommendation(
            @RequestBody RecommendationRequest request) {

        return recommendationService.getRuleRecommendation(request);
    }
}