package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.ManagerQuizImportRequest;
import com.charmquiz.backend.dto.ManagerQuizResponse;
import com.charmquiz.backend.dto.ManagerQuizStatusUpdateRequest;
import com.charmquiz.backend.dto.ManagerQuizValidationResponse;
import com.charmquiz.backend.service.SupabaseManagerQuizService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/manager/quizzes")
public class ManagerQuizController {

    private final SupabaseManagerQuizService quizService;

    public ManagerQuizController(SupabaseManagerQuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping
    public List<ManagerQuizResponse> getQuizzes() {
        return quizService.getAllQuizzes();
    }

    @GetMapping("/{id}")
    public ManagerQuizResponse getQuiz(@PathVariable UUID id) {
        return quizService.getQuiz(id);
    }

    @PostMapping("/validate")
    public ManagerQuizValidationResponse validateImport(
            @Valid @RequestBody ManagerQuizImportRequest request) {
        quizService.validateImport(request);
        return new ManagerQuizValidationResponse(true);
    }

    @PostMapping("/import")
    public ResponseEntity<ManagerQuizResponse> importQuiz(
            @Valid @RequestBody ManagerQuizImportRequest request) {
        ManagerQuizResponse created = quizService.importQuiz(request);
        return ResponseEntity
                .created(URI.create("/api/manager/quizzes/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}/status")
    public ManagerQuizResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ManagerQuizStatusUpdateRequest request) {
        return quizService.updateStatus(id, request.status());
    }
}
