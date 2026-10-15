package com.charmquiz.backend.exception;

public class QuizUnavailableException extends RuntimeException {

    public QuizUnavailableException() {
        super("The quiz is currently unavailable.");
    }
}
