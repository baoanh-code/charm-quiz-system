package com.charmquiz.backend.exception;

public class ManagerRuleConflictException extends RuntimeException {

    public ManagerRuleConflictException(String message) {
        super(message);
    }

    public ManagerRuleConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
