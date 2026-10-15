package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.QuizSessionResponse;
import com.charmquiz.backend.service.SupabaseQuizService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final SupabaseQuizService quizService;

    public QuizController(SupabaseQuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/start")
    public ResponseEntity<QuizSessionResponse> startQuiz() {
        return ResponseEntity.status(201).body(quizService.startSession());
    }
}
