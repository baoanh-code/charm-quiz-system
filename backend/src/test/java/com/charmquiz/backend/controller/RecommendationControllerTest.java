package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.RecommendationResponse;
import com.charmquiz.backend.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
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
}
