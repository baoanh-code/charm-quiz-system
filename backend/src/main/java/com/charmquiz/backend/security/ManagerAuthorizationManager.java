package com.charmquiz.backend.security;

import com.charmquiz.backend.service.SupabaseManagerAuthorizationService;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

@Component
public class ManagerAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final AuthorizationDecision DENIED = new AuthorizationDecision(false);

    private final SupabaseManagerAuthorizationService managerAuthorizationService;

    public ManagerAuthorizationManager(
            SupabaseManagerAuthorizationService managerAuthorizationService) {
        this.managerAuthorizationService = managerAuthorizationService;
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            return DENIED;
        }

        try {
            UUID userId = UUID.fromString(jwtAuthentication.getToken().getSubject());
            return new AuthorizationDecision(managerAuthorizationService.isManager(userId));
        } catch (RuntimeException exception) {
            return DENIED;
        }
    }
}
