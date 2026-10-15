package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.ManagerApiError;
import com.charmquiz.backend.exception.ManagerQuizNotFoundException;
import com.charmquiz.backend.exception.ManagerUpstreamException;
import com.charmquiz.backend.exception.ManagerValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ManagerQuizController.class)
public class ManagerQuizExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ManagerApiError> badRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.");
    }

    @ExceptionHandler(ManagerValidationException.class)
    ResponseEntity<ManagerApiError> validation(ManagerValidationException exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(ManagerQuizNotFoundException.class)
    ResponseEntity<ManagerApiError> notFound(ManagerQuizNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "QUIZ_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ManagerUpstreamException.class)
    ResponseEntity<ManagerApiError> upstream(ManagerUpstreamException exception) {
        return error(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", "Upstream service request failed.");
    }

    private ResponseEntity<ManagerApiError> error(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity.status(status).body(new ManagerApiError(code, message));
    }
}
