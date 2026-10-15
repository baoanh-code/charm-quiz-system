package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.RecommendationResponse;
import com.charmquiz.backend.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RecommendationController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void acceptsNewRecommendationRequestContract() throws Exception {
        when(recommendationService.getRecommendations(any()))
                .thenReturn(new RecommendationResponse(List.of()));

        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Lâm Phú Bảo Anh",
                                  "birthDate": "2006-07-24",
                                  "interests": ["music", "football"],
                                  "favoriteAnimal": "cat",
                                  "favoriteColor": "red"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections").isArray());
    }

    @Test
    void rejectsMissingMandatoryInputsAndEmptyInterests() throws Exception {
        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "",
                                  "birthDate": "2006-07-24",
                                  "interests": [],
                                  "favoriteAnimal": "cat"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recommendationService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequestPayloads")
    void rejectsValidationBoundaryBeforeCallingService(String ignoredCase, String payload)
            throws Exception {
        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recommendationService);
    }

    private static Stream<Arguments> invalidRequestPayloads() {
        String twentyOneInterests = IntStream.range(0, 21)
                .mapToObj(index -> "\"interest-" + index + "\"")
                .collect(Collectors.joining(","));

        return Stream.of(
                Arguments.of("missing fullName", """
                        {"birthDate":"2006-07-24","interests":["music"],"favoriteAnimal":"cat","favoriteColor":"blue"}
                        """),
                Arguments.of("missing birthDate", """
                        {"fullName":"Test User","interests":["music"],"favoriteAnimal":"cat","favoriteColor":"blue"}
                        """),
                Arguments.of("missing interests", """
                        {"fullName":"Test User","birthDate":"2006-07-24","favoriteAnimal":"cat","favoriteColor":"blue"}
                        """),
                Arguments.of("missing favoriteAnimal", """
                        {"fullName":"Test User","birthDate":"2006-07-24","interests":["music"],"favoriteColor":"blue"}
                        """),
                Arguments.of("missing favoriteColor", """
                        {"fullName":"Test User","birthDate":"2006-07-24","interests":["music"],"favoriteAnimal":"cat"}
                        """),
                Arguments.of("whitespace-only interest", validPayload("Test User", "2006-07-24", "\"   \"", "cat", "blue")),
                Arguments.of("more than twenty interests", validPayload("Test User", "2006-07-24", twentyOneInterests, "cat", "blue")),
                Arguments.of("fullName too long", validPayload("x".repeat(201), "2006-07-24", "\"music\"", "cat", "blue")),
                Arguments.of("interest too long", validPayload("Test User", "2006-07-24", "\"" + "x".repeat(101) + "\"", "cat", "blue")),
                Arguments.of("favoriteAnimal too long", validPayload("Test User", "2006-07-24", "\"music\"", "x".repeat(101), "blue")),
                Arguments.of("favoriteColor too long", validPayload("Test User", "2006-07-24", "\"music\"", "cat", "x".repeat(101))),
                Arguments.of("malformed birthDate", validPayload("Test User", "24/07/2006", "\"music\"", "cat", "blue"))
        );
    }

    private static String validPayload(
            String fullName,
            String birthDate,
            String interestsJson,
            String favoriteAnimal,
            String favoriteColor) {
        return """
                {
                  "fullName": "%s",
                  "birthDate": "%s",
                  "interests": [%s],
                  "favoriteAnimal": "%s",
                  "favoriteColor": "%s"
                }
                """.formatted(fullName, birthDate, interestsJson, favoriteAnimal, favoriteColor);
    }
}
