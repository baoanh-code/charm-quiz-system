package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.QuizApiError;
import com.charmquiz.backend.exception.QuizUnavailableException;
import com.charmquiz.backend.exception.QuizUpstreamException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = QuizController.class)
public class QuizExceptionHandler {

    @ExceptionHandler(QuizUnavailableException.class)
    ResponseEntity<QuizApiError> unavailable(QuizUnavailableException exception) {
        return error(HttpStatus.CONFLICT, "QUIZ_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(QuizUpstreamException.class)
    ResponseEntity<QuizApiError> upstream(QuizUpstreamException exception) {
        return error(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", "Upstream service request failed.");
    }

    private ResponseEntity<QuizApiError> error(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity.status(status).body(new QuizApiError(code, message));
    }
}
