package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.ManagerApiError;
import com.charmquiz.backend.exception.ManagerCharmNotFoundException;
import com.charmquiz.backend.exception.ManagerConflictException;
import com.charmquiz.backend.exception.ManagerPayloadTooLargeException;
import com.charmquiz.backend.exception.ManagerUpstreamException;
import com.charmquiz.backend.exception.ManagerValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(assignableTypes = ManagerCharmController.class)
public class ManagerCharmExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class,
            HttpMediaTypeNotSupportedException.class
    })
    ResponseEntity<ManagerApiError> badRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.");
    }

    @ExceptionHandler(ManagerValidationException.class)
    ResponseEntity<ManagerApiError> validation(ManagerValidationException exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(ManagerCharmNotFoundException.class)
    ResponseEntity<ManagerApiError> notFound(ManagerCharmNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "CHARM_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ManagerConflictException.class)
    ResponseEntity<ManagerApiError> conflict(ManagerConflictException exception) {
        return error(HttpStatus.CONFLICT, "CHARM_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler({ManagerPayloadTooLargeException.class, MaxUploadSizeExceededException.class})
    ResponseEntity<ManagerApiError> payloadTooLarge(Exception exception) {
        return error(HttpStatus.CONTENT_TOO_LARGE, "IMAGE_TOO_LARGE", "Image must not exceed 5 MB.");
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
