package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.QuizOptionResponse;
import com.charmquiz.backend.dto.QuizQuestionResponse;
import com.charmquiz.backend.dto.QuizSessionResponse;
import com.charmquiz.backend.exception.QuizUnavailableException;
import com.charmquiz.backend.exception.QuizUpstreamException;
import com.charmquiz.backend.service.SupabaseQuizService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = QuizController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class QuizControllerTest {

    private static final UUID SESSION_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupabaseQuizService quizService;

    @Test
    void startsSessionWithCanonicalSafeContract() throws Exception {
        when(quizService.startSession()).thenReturn(response());

        mockMvc.perform(post("/api/quiz/start"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.questions", hasSize(10)))
                .andExpect(jsonPath("$.questions[0].id").isNotEmpty())
                .andExpect(jsonPath("$.questions[0].prompt").value("Question 1"))
                .andExpect(jsonPath("$.questions[0].options", hasSize(4)))
                .andExpect(jsonPath("$.questions[0].options[0].id").isNotEmpty())
                .andExpect(jsonPath("$.questions[0].options[0].label").value("Option 1"))
                .andExpect(content().string(not(containsString("isCorrect"))))
                .andExpect(content().string(not(containsString("is_correct"))))
                .andExpect(content().string(not(containsString("correctOptionId"))))
                .andExpect(content().string(not(containsString("correctIndex"))))
                .andExpect(content().string(not(containsString("explanation"))));
    }

    @Test
    void mapsUnavailableAndUpstreamErrorsSafely() throws Exception {
        when(quizService.startSession())
                .thenThrow(new QuizUnavailableException())
                .thenThrow(new QuizUpstreamException("sensitive upstream detail"));

        mockMvc.perform(post("/api/quiz/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUIZ_UNAVAILABLE"));

        mockMvc.perform(post("/api/quiz/start"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))
                .andExpect(content().string(not(containsString("sensitive upstream detail"))));
    }

    private QuizSessionResponse response() {
        List<QuizQuestionResponse> questions = IntStream.range(0, 10)
                .mapToObj(questionIndex -> new QuizQuestionResponse(
                        UUID.randomUUID().toString(),
                        "Question " + (questionIndex + 1),
                        IntStream.range(0, 4)
                                .mapToObj(optionIndex -> new QuizOptionResponse(
                                        UUID.randomUUID().toString(),
                                        "Option " + (optionIndex + 1)
                                ))
                                .toList()
                ))
                .toList();
        return new QuizSessionResponse(SESSION_ID.toString(), questions);
    }
}
