package com.charmquiz.backend.exception;

public class ManagerUpstreamException extends RuntimeException {

    public ManagerUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }

    public ManagerUpstreamException(String message) {
        super(message);
    }
}
