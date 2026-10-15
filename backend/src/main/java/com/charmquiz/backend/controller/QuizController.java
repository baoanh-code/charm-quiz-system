package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.QuizAnswerRequest;
import com.charmquiz.backend.dto.QuizAnswerResponse;
import com.charmquiz.backend.dto.QuizResultResponse;
import com.charmquiz.backend.dto.QuizSessionResponse;
import com.charmquiz.backend.service.SupabaseQuizAnswerService;
import com.charmquiz.backend.service.SupabaseQuizService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final SupabaseQuizService quizService;
    private final SupabaseQuizAnswerService quizAnswerService;

    public QuizController(
            SupabaseQuizService quizService,
            SupabaseQuizAnswerService quizAnswerService) {
        this.quizService = quizService;
        this.quizAnswerService = quizAnswerService;
    }

    @PostMapping("/start")
    public ResponseEntity<QuizSessionResponse> startQuiz() {
        return ResponseEntity.status(201).body(quizService.startSession());
    }

    @PostMapping("/answer")
    public QuizAnswerResponse submitAnswer(@Valid @RequestBody QuizAnswerRequest request) {
        return quizAnswerService.submitAnswer(request);
    }

    @GetMapping("/result")
    public QuizResultResponse getResult(@RequestParam UUID sessionId) {
        return quizAnswerService.getResult(sessionId);
    }
}
