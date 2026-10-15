package com.charmquiz.backend.exception;

public class QuizAnswerAlreadySubmittedException extends RuntimeException {

    public QuizAnswerAlreadySubmittedException() {
        super("An answer has already been submitted for this question.");
    }
}
