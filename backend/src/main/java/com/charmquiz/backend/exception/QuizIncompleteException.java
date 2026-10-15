package com.charmquiz.backend.exception;

public class QuizIncompleteException extends RuntimeException {

    public QuizIncompleteException() {
        super("Quiz session is not complete.");
    }
}
