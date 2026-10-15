package com.charmquiz.backend.exception;

public class ManagerConflictException extends RuntimeException {

    public ManagerConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
