package com.charmquiz.backend.config;

import com.charmquiz.backend.dto.RecommendationResponse;
import com.charmquiz.backend.service.RecommendationService;
import com.charmquiz.backend.service.SupabaseCharmStorageService;
import com.charmquiz.backend.service.SupabaseCharmService;
import com.charmquiz.backend.service.SupabaseManagerAuthorizationService;
import com.charmquiz.backend.service.SupabaseManagerCharmService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityConfigTest.ManagerProbeController.class)
class SecurityConfigTest {

    private static final String ISSUER = "https://test-project.supabase.co/auth/v1";
    private static final String AUDIENCE = "authenticated";
    private static final UUID USER_ID = UUID.fromString("12345678-1234-1234-1234-123456789012");
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final ECKey EC_KEY;
    private static final ECKey ATTACKER_EC_KEY;
    private static final RSAKey RSA_KEY;
    private static final HttpServer JWKS_SERVER;

    static {
        try {
            EC_KEY = new ECKeyGenerator(Curve.P_256).keyID("test-ec-key").generate();
            ATTACKER_EC_KEY = new ECKeyGenerator(Curve.P_256).keyID("test-ec-key").generate();
            RSA_KEY = new RSAKeyGenerator(2048).keyID("test-rsa-key").generate();
            JWKS_SERVER = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                    0
            );
            JWKS_SERVER.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(List.of(
                        EC_KEY.toPublicJWK(),
                        RSA_KEY.toPublicJWK()
                )).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            JWKS_SERVER.start();
        } catch (JOSEException | IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + JWKS_SERVER.getAddress().getPort() + "/jwks"
        );
        registry.add("spring.security.oauth2.resourceserver.jwt.jws-algorithms", () -> "ES256");
        registry.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> AUDIENCE);
        registry.add("supabase.url", () -> "https://test-project.supabase.co");
        registry.add("supabase.publishable-key", () -> "test-publishable-key");
        registry.add("supabase.secret-key", () -> "test-secret-key");
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS_SERVER.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupabaseManagerAuthorizationService managerAuthorizationService;

    @MockitoBean
    private SupabaseCharmService charmService;

    @MockitoBean
    private RecommendationService recommendationService;

    @MockitoBean
    private SupabaseManagerCharmService managerCharmService;

    @MockitoBean
    private SupabaseCharmStorageService charmStorageService;

    @BeforeEach
    void configurePublicResponses() {
        when(charmService.getCatalogCharms()).thenReturn(List.of());
        when(recommendationService.getRecommendations(any()))
                .thenReturn(new RecommendationResponse(List.of()));
    }

    @Test
    void managerEndpointWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/manager/probe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managerBasePathIsProtected() throws Exception {
        mockMvc.perform(get("/api/manager"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyManagerCharmEndpointRequiresAuthentication() throws Exception {
        String id = "11111111-1111-1111-1111-111111111111";

        mockMvc.perform(get("/api/manager/charms"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/manager/charms/{id}", id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/manager/charms"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/manager/charms/{id}", id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/manager/charms/{id}/status", id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/manager/charms/{id}/stock-status", id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/manager/charm-images"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonManagerCannotReachManagerCharmController() throws Exception {
        when(managerAuthorizationService.isManager(USER_ID)).thenReturn(false);

        mockMvc.perform(get("/api/manager/charms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void validManagerReachesManagerCharmController() throws Exception {
        when(managerAuthorizationService.isManager(USER_ID)).thenReturn(true);
        when(managerCharmService.getAllCharms()).thenReturn(List.of());

        mockMvc.perform(get("/api/manager/charms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken())))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(managerCharmService).getAllCharms();
    }

    @Test
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/manager/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/manager/probe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(es256Token(
                                ATTACKER_EC_KEY,
                                ISSUER,
                                List.of(AUDIENCE),
                                Instant.now().plusSeconds(300)
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/manager/probe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(es256Token(
                                EC_KEY,
                                ISSUER,
                                List.of(AUDIENCE),
                                Instant.now().minusSeconds(60)
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/manager/probe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(es256Token(
                                EC_KEY,
                                "https://other-project.supabase.co/auth/v1",
                                List.of(AUDIENCE),
                                Instant.now().plusSeconds(300)
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/manager/probe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(es256Token(
                                EC_KEY,
                                ISSUER,
                                List.of("other-audience"),
                                Instant.now().plusSeconds(300)
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonEs256TokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/manager/probe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(rs256Token())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validAuthenticatedNonManagerIsForbidden() throws Exception {
        when(managerAuthorizationService.isManager(USER_ID)).thenReturn(false);

        mockMvc.perform(get("/api/manager/probe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken())))
                .andExpect(status().isForbidden());

        verify(managerAuthorizationService).isManager(USER_ID);
    }

    @Test
    void validManagerIsAllowedAndVerifiedSubjectUuidIsUsed() throws Exception {
        when(managerAuthorizationService.isManager(USER_ID)).thenReturn(true);

        mockMvc.perform(get("/api/manager/probe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken())))
                .andExpect(status().isOk())
                .andExpect(content().string("manager-ok"));

        verify(managerAuthorizationService).isManager(USER_ID);
    }

    @Test
    void managerLookupFailureFailsClosed() throws Exception {
        when(managerAuthorizationService.isManager(USER_ID))
                .thenThrow(new IllegalStateException("upstream unavailable"));

        mockMvc.perform(get("/api/manager/probe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicEndpointsRemainPublicWithoutToken() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/charms"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRecommendationRequest()))
                .andExpect(status().isOk());
    }

    @Test
    void malformedBearerTokenIsIgnoredByEveryPublicEndpoint() throws Exception {
        mockMvc.perform(get("/api/health")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/charms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRecommendationRequest()))
                .andExpect(status().isOk());
    }

    @Test
    void managerCorsPreflightAllowsAuthorizationHeader() throws Exception {
        mockMvc.perform(options("/api/manager/probe")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsStringIgnoringCase("Authorization")
                ));
    }

    @Test
    void managerCorsPreflightRejectsDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/manager/probe")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private String validToken() throws JOSEException {
        return es256Token(
                EC_KEY,
                ISSUER,
                List.of(AUDIENCE),
                Instant.now().plusSeconds(300)
        );
    }

    private String es256Token(
            ECKey signingKey,
            String issuer,
            List<String> audience,
            Instant expiresAt) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .type(JOSEObjectType.JWT)
                        .keyID(signingKey.getKeyID())
                        .build(),
                claims(issuer, audience, expiresAt)
        );
        jwt.sign(new ECDSASigner(signingKey));
        return jwt.serialize();
    }

    private String rs256Token() throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(RSA_KEY.getKeyID())
                        .build(),
                claims(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300))
        );
        jwt.sign(new RSASSASigner(RSA_KEY));
        return jwt.serialize();
    }

    private JWTClaimsSet claims(String issuer, List<String> audience, Instant expiresAt) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(USER_ID.toString())
                .issueTime(Date.from(now.minusSeconds(5)))
                .notBeforeTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(expiresAt))
                .claim("role", "authenticated")
                .build();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String validRecommendationRequest() {
        return """
                {
                  "fullName": "Lam Phu Bao Anh",
                  "birthDate": "2006-07-24",
                  "interests": ["music", "football"],
                  "favoriteAnimal": "cat",
                  "favoriteColor": "blue"
                }
                """;
    }

    @RestController
    static class ManagerProbeController {

        @GetMapping({"/api/manager", "/api/manager/probe"})
        String probe() {
            return "manager-ok";
        }
    }
}
