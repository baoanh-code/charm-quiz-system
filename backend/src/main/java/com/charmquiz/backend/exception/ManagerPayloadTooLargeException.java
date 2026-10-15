package com.charmquiz.backend.exception;

public class ManagerPayloadTooLargeException extends RuntimeException {

    public ManagerPayloadTooLargeException() {
        super("Image must not exceed 5 MB.");
    }
}
