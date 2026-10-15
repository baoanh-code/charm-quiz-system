package com.charmquiz.backend.exception;

public class ManagerCharmNotFoundException extends RuntimeException {

    public ManagerCharmNotFoundException() {
        super("Charm was not found.");
    }
}
