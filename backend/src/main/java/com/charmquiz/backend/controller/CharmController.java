package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.CharmCatalogItemResponse;
import com.charmquiz.backend.service.SupabaseCharmService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/charms")
public class CharmController {

    private final SupabaseCharmService charmService;

    public CharmController(SupabaseCharmService charmService) {
        this.charmService = charmService;
    }

    @GetMapping
    public List<CharmCatalogItemResponse> getCharms() {
        return charmService.getCatalogCharms();
    }
}
