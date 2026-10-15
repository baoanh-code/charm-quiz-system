package com.charmquiz.backend.exception;

public class ManagerRuleCharmNotFoundException extends RuntimeException {

    public ManagerRuleCharmNotFoundException() {
        super("Referenced charm was not found.");
    }
}
