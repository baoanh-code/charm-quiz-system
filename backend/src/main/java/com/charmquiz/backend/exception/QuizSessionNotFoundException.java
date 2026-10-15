package com.charmquiz.backend.exception;

public class QuizSessionNotFoundException extends RuntimeException {

    public QuizSessionNotFoundException() {
        super("Quiz session was not found.");
    }
}
