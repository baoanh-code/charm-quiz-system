package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.QuizApiError;
import com.charmquiz.backend.exception.QuizAnswerAlreadySubmittedException;
import com.charmquiz.backend.exception.QuizIncompleteException;
import com.charmquiz.backend.exception.QuizOptionNotFoundException;
import com.charmquiz.backend.exception.QuizQuestionNotFoundException;
import com.charmquiz.backend.exception.QuizSessionCompletedException;
import com.charmquiz.backend.exception.QuizSessionNotFoundException;
import com.charmquiz.backend.exception.QuizUnavailableException;
import com.charmquiz.backend.exception.QuizUpstreamException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = QuizController.class)
public class QuizExceptionHandler {

    @ExceptionHandler(QuizUnavailableException.class)
    ResponseEntity<QuizApiError> unavailable(QuizUnavailableException exception) {
        return error(HttpStatus.CONFLICT, "QUIZ_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    ResponseEntity<QuizApiError> validation(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.");
    }

    @ExceptionHandler(QuizSessionNotFoundException.class)
    ResponseEntity<QuizApiError> sessionNotFound(QuizSessionNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(QuizQuestionNotFoundException.class)
    ResponseEntity<QuizApiError> questionNotFound(QuizQuestionNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(QuizOptionNotFoundException.class)
    ResponseEntity<QuizApiError> optionNotFound(QuizOptionNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "OPTION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(QuizSessionCompletedException.class)
    ResponseEntity<QuizApiError> sessionCompleted(QuizSessionCompletedException exception) {
        return error(HttpStatus.CONFLICT, "SESSION_COMPLETED", exception.getMessage());
    }

    @ExceptionHandler(QuizAnswerAlreadySubmittedException.class)
    ResponseEntity<QuizApiError> duplicateAnswer(QuizAnswerAlreadySubmittedException exception) {
        return error(HttpStatus.CONFLICT, "ANSWER_ALREADY_SUBMITTED", exception.getMessage());
    }

    @ExceptionHandler(QuizIncompleteException.class)
    ResponseEntity<QuizApiError> incomplete(QuizIncompleteException exception) {
        return error(HttpStatus.CONFLICT, "QUIZ_INCOMPLETE", exception.getMessage());
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
