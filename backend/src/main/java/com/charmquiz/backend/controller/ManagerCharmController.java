package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.CharmImageUploadResponse;
import com.charmquiz.backend.dto.CharmStatusUpdateRequest;
import com.charmquiz.backend.dto.CharmStockStatusUpdateRequest;
import com.charmquiz.backend.dto.ManagerCharmResponse;
import com.charmquiz.backend.dto.ManagerCharmUpsertRequest;
import com.charmquiz.backend.service.SupabaseCharmStorageService;
import com.charmquiz.backend.service.SupabaseManagerCharmService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/manager")
public class ManagerCharmController {

    private final SupabaseManagerCharmService charmService;
    private final SupabaseCharmStorageService storageService;

    public ManagerCharmController(
            SupabaseManagerCharmService charmService,
            SupabaseCharmStorageService storageService) {
        this.charmService = charmService;
        this.storageService = storageService;
    }

    @GetMapping("/charms")
    public List<ManagerCharmResponse> getCharms() {
        return charmService.getAllCharms();
    }

    @GetMapping("/charms/{id}")
    public ManagerCharmResponse getCharm(@PathVariable UUID id) {
        return charmService.getCharm(id);
    }

    @PostMapping("/charms")
    public ResponseEntity<ManagerCharmResponse> createCharm(
            @Valid @RequestBody ManagerCharmUpsertRequest request) {
        ManagerCharmResponse created = charmService.createCharm(request);
        return ResponseEntity
                .created(URI.create("/api/manager/charms/" + created.id()))
                .body(created);
    }

    @PutMapping("/charms/{id}")
    public ManagerCharmResponse updateCharm(
            @PathVariable UUID id,
            @Valid @RequestBody ManagerCharmUpsertRequest request) {
        return charmService.updateCharm(id, request);
    }

    @PatchMapping("/charms/{id}/status")
    public ManagerCharmResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CharmStatusUpdateRequest request) {
        return charmService.updateStatus(id, request.status());
    }

    @PatchMapping("/charms/{id}/stock-status")
    public ManagerCharmResponse updateStockStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CharmStockStatusUpdateRequest request) {
        return charmService.updateStockStatus(id, request.stockStatus());
    }

    @PostMapping(
            value = "/charm-images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<CharmImageUploadResponse> uploadImage(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(201).body(storageService.upload(file));
    }
}
