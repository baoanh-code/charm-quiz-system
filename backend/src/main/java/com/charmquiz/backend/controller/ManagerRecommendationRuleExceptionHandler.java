package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.ManagerApiError;
import com.charmquiz.backend.exception.ManagerRecommendationRuleNotFoundException;
import com.charmquiz.backend.exception.ManagerRuleCharmNotFoundException;
import com.charmquiz.backend.exception.ManagerRuleConflictException;
import com.charmquiz.backend.exception.ManagerUpstreamException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ManagerRecommendationRuleController.class)
public class ManagerRecommendationRuleExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ManagerApiError> badRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.");
    }

    @ExceptionHandler(ManagerRecommendationRuleNotFoundException.class)
    ResponseEntity<ManagerApiError> ruleNotFound(
            ManagerRecommendationRuleNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ManagerRuleCharmNotFoundException.class)
    ResponseEntity<ManagerApiError> charmNotFound(ManagerRuleCharmNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "CHARM_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ManagerRuleConflictException.class)
    ResponseEntity<ManagerApiError> conflict(ManagerRuleConflictException exception) {
        return error(HttpStatus.CONFLICT, "RULE_CONFLICT", exception.getMessage());
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
