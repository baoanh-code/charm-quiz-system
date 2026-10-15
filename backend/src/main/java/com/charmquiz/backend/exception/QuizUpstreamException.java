package com.charmquiz.backend.exception;

public class QuizUpstreamException extends RuntimeException {

    public QuizUpstreamException(String message) {
        super(message);
    }

    public QuizUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
