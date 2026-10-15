package com.charmquiz.backend.exception;

public class ManagerQuizNotFoundException extends RuntimeException {

    public ManagerQuizNotFoundException() {
        super("Quiz was not found.");
    }
}
