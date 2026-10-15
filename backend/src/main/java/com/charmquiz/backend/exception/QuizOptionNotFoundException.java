package com.charmquiz.backend.exception;

public class QuizOptionNotFoundException extends RuntimeException {

    public QuizOptionNotFoundException() {
        super("Quiz option was not found.");
    }
}
