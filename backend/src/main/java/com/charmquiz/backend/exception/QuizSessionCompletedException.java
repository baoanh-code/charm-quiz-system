package com.charmquiz.backend.exception;

public class QuizSessionCompletedException extends RuntimeException {

    public QuizSessionCompletedException() {
        super("Quiz session is already complete.");
    }
}
