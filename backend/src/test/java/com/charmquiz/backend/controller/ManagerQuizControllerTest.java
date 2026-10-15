package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.ManagerQuizQuestionResponse;
import com.charmquiz.backend.dto.ManagerQuizResponse;
import com.charmquiz.backend.exception.ManagerQuizNotFoundException;
import com.charmquiz.backend.exception.ManagerUpstreamException;
import com.charmquiz.backend.model.QuizStatus;
import com.charmquiz.backend.service.SupabaseManagerQuizService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ManagerQuizController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class ManagerQuizControllerTest {

    private static final UUID QUIZ_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupabaseManagerQuizService quizService;

    @Test
    void listsAndGetsFrontendCompatibleQuizData() throws Exception {
        when(quizService.getAllQuizzes()).thenReturn(List.of(response()));
        when(quizService.getQuiz(QUIZ_ID)).thenReturn(response());

        mockMvc.perform(get("/api/manager/quizzes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(QUIZ_ID.toString()))
                .andExpect(jsonPath("$[0].status").value("INACTIVE"))
                .andExpect(jsonPath("$[0].questions[0].prompt").value("Question 1"))
                .andExpect(jsonPath("$[0].questions[0].options.length()").value(4))
                .andExpect(jsonPath("$[0].questions[0].correctIndex").value(1));

        mockMvc.perform(get("/api/manager/quizzes/{id}", QUIZ_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("History quiz"));
    }

    @Test
    void validatesWithoutPersistingAndImportsWithCreatedLocation() throws Exception {
        when(quizService.importQuiz(any())).thenReturn(response());

        mockMvc.perform(post("/api/manager/quizzes/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        verify(quizService).validateImport(any());

        mockMvc.perform(post("/api/manager/quizzes/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/manager/quizzes/" + QUIZ_ID
                ))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void patchesQuizStatus() throws Exception {
        ManagerQuizResponse active = new ManagerQuizResponse(
                QUIZ_ID.toString(), "History quiz", null, QuizStatus.ACTIVE, List.of());
        when(quizService.updateStatus(QUIZ_ID, QuizStatus.ACTIVE)).thenReturn(active);

        mockMvc.perform(patch("/api/manager/quizzes/{id}/status", QUIZ_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(quizService).updateStatus(QUIZ_ID, QuizStatus.ACTIVE);
    }

    @Test
    void rejectsEveryImportValidationBoundary() throws Exception {
        for (String body : List.of(
                validRequest().replace("History quiz", "   "),
                validRequest().replaceFirst("\"prompt\":\"Question 1\"", "\"prompt\":\"   \""),
                validRequest().replaceFirst("\\[\"A\",\"B\",\"C\",\"D\"\\]", "[\"A\",\"B\",\"C\"]"),
                validRequest().replaceFirst("\"A\"", "\"   \""),
                validRequest().replaceFirst("\"correctIndex\":1", "\"correctIndex\":4"),
                requestWithQuestionCount(9)
        )) {
            mockMvc.perform(post("/api/manager/quizzes/import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(quizService);
    }

    @Test
    void rejectsMalformedUuidBodyAndStatus() throws Exception {
        mockMvc.perform(get("/api/manager/quizzes/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/manager/quizzes/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(patch("/api/manager/quizzes/{id}/status", QUIZ_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DRAFT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(quizService);
    }

    @Test
    void mapsNotFoundAndUpstreamErrors() throws Exception {
        when(quizService.getQuiz(QUIZ_ID))
                .thenThrow(new ManagerQuizNotFoundException())
                .thenThrow(new ManagerUpstreamException("failed"));

        mockMvc.perform(get("/api/manager/quizzes/{id}", QUIZ_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUIZ_NOT_FOUND"));
        mockMvc.perform(get("/api/manager/quizzes/{id}", QUIZ_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
    }

    private ManagerQuizResponse response() {
        return new ManagerQuizResponse(
                QUIZ_ID.toString(),
                "History quiz",
                "Description",
                QuizStatus.INACTIVE,
                List.of(new ManagerQuizQuestionResponse(
                        "10000000-0000-0000-0000-000000000001",
                        "Question 1",
                        List.of("A", "B", "C", "D"),
                        1,
                        "Explanation"
                ))
        );
    }

    private String validRequest() {
        return requestWithQuestionCount(10);
    }

    private String requestWithQuestionCount(int questionCount) {
        StringBuilder questions = new StringBuilder();
        for (int index = 1; index <= questionCount; index++) {
            if (index > 1) {
                questions.append(',');
            }
            questions.append("{\"prompt\":\"Question ")
                    .append(index)
                    .append("\",\"options\":[\"A\",\"B\",\"C\",\"D\"],")
                    .append("\"correctIndex\":1,\"explanation\":\"Why\"}");
        }
        return "{\"title\":\"History quiz\",\"description\":\"About history\","
                + "\"questions\":[" + questions + "]}";
    }
}
