package com.charmquiz.backend.exception;

public class QuizQuestionNotFoundException extends RuntimeException {

    public QuizQuestionNotFoundException() {
        super("Quiz question was not found.");
    }
}
