package com.charmquiz.backend.config;

import com.charmquiz.backend.controller.CharmController;
import com.charmquiz.backend.controller.HealthController;
import com.charmquiz.backend.controller.RecommendationController;
import com.charmquiz.backend.dto.RecommendationResponse;
import com.charmquiz.backend.model.RecommendationSectionType;
import com.charmquiz.backend.model.RecommendationSource;
import com.charmquiz.backend.service.RecommendationService;
import com.charmquiz.backend.service.SupabaseCharmService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        HealthController.class,
        CharmController.class,
        RecommendationController.class
})
@Import(CorsConfig.class)
class CorsConfigTest {

    private static final String ALLOWED_ORIGIN_VITE = "http://localhost:5173";
    private static final String ALLOWED_ORIGIN_REACT = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupabaseCharmService charmService;

    @MockitoBean
    private RecommendationService recommendationService;

    @ParameterizedTest
    @ValueSource(strings = {ALLOWED_ORIGIN_VITE, ALLOWED_ORIGIN_REACT})
    void allowsConfiguredFrontendOrigins(String origin) throws Exception {
        mockMvc.perform(get("/api/health")
                        .header(HttpHeaders.ORIGIN, origin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(content().string("Backend is running"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
    void allowsConfiguredMethodsForApiPreflight(String method) throws Exception {
        mockMvc.perform(options("/api/cors-probe")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN_VITE)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ALLOWED_ORIGIN_VITE
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString(method)
                ));
    }

    @Test
    void allowsJsonContentTypeForPreflight() throws Exception {
        mockMvc.perform(options("/api/recommendations")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN_VITE)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ALLOWED_ORIGIN_VITE
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("Content-Type")
                ));
    }

    @Test
    void rejectsUnapprovedOrigin() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void doesNotApplyApiCorsPolicyOutsideApiPath() throws Exception {
        mockMvc.perform(get("/outside-cors-probe")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN_VITE))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void allowsCrossOriginCharmRequestWithoutCallingSupabase() throws Exception {
        when(charmService.getCatalogCharms()).thenReturn(List.of());

        mockMvc.perform(get("/api/charms")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN_VITE))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ALLOWED_ORIGIN_VITE
                ))
                .andExpect(content().json("[]"));
    }

    @Test
    void allowsCrossOriginRecommendationRequestWithoutCallingSupabase() throws Exception {
        RecommendationResponse response = new RecommendationResponse(
                List.of(new RecommendationResponse.Section(
                        RecommendationSectionType.INTERESTS,
                        List.of("history"),
                        Map.of(),
                        List.of(new RecommendationResponse.Item(
                                "charm-1",
                                "Test Charm",
                                "https://example.com/charm.png",
                                RecommendationSource.RULE,
                                1,
                                "Matched INTEREST: history"
                        ))
                ))
        );
        when(recommendationService.getRecommendations(any())).thenReturn(response);

        mockMvc.perform(post("/api/recommendations")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN_REACT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Test User",
                                  "birthDate": "2000-01-01",
                                  "interests": ["history"],
                                  "favoriteAnimal": "cat",
                                  "favoriteColor": "blue"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ALLOWED_ORIGIN_REACT
                ))
                .andExpect(jsonPath("$.sections[0].recommendations[0].source").value("RULE"))
                .andExpect(jsonPath("$.sections[0].recommendations[0].charmId").value("charm-1"));
    }

}
