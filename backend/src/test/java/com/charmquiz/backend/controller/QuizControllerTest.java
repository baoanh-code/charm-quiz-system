package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.QuizAnswerRequest;
import com.charmquiz.backend.dto.QuizAnswerResponse;
import com.charmquiz.backend.dto.QuizOptionResponse;
import com.charmquiz.backend.dto.QuizQuestionResponse;
import com.charmquiz.backend.dto.QuizResultDetailResponse;
import com.charmquiz.backend.dto.QuizResultResponse;
import com.charmquiz.backend.dto.QuizSessionResponse;
import com.charmquiz.backend.exception.QuizAnswerAlreadySubmittedException;
import com.charmquiz.backend.exception.QuizIncompleteException;
import com.charmquiz.backend.exception.QuizOptionNotFoundException;
import com.charmquiz.backend.exception.QuizQuestionNotFoundException;
import com.charmquiz.backend.exception.QuizSessionCompletedException;
import com.charmquiz.backend.exception.QuizSessionNotFoundException;
import com.charmquiz.backend.exception.QuizUnavailableException;
import com.charmquiz.backend.exception.QuizUpstreamException;
import com.charmquiz.backend.service.SupabaseQuizAnswerService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @MockitoBean
    private SupabaseQuizAnswerService quizAnswerService;

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

    @Test
    void submitsCorrectAndIncorrectAnswersWithExactContract() throws Exception {
        UUID correctOptionId = UUID.randomUUID();
        when(quizAnswerService.submitAnswer(any(QuizAnswerRequest.class)))
                .thenReturn(new QuizAnswerResponse(true, correctOptionId.toString(), null))
                .thenReturn(new QuizAnswerResponse(
                        false,
                        correctOptionId.toString(),
                        "Historical explanation"
                ));

        String request = answerRequest();
        mockMvc.perform(post("/api/quiz/answer")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isCorrect").value(true))
                .andExpect(jsonPath("$.correctOptionId").value(correctOptionId.toString()))
                .andExpect(jsonPath("$.explanation").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.answeredCount").doesNotExist())
                .andExpect(jsonPath("$.finalResult").doesNotExist());

        mockMvc.perform(post("/api/quiz/answer")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isCorrect").value(false))
                .andExpect(jsonPath("$.correctOptionId").value(correctOptionId.toString()))
                .andExpect(jsonPath("$.explanation").value("Historical explanation"));
    }

    @Test
    void rejectsMalformedAnswerRequestsBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/quiz/answer")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/quiz/answer")
                        .contentType("application/json")
                        .content("{\"sessionId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(quizAnswerService);
    }

    @Test
    void mapsAnswerBusinessErrorsToStableCodes() throws Exception {
        when(quizAnswerService.submitAnswer(any(QuizAnswerRequest.class)))
                .thenThrow(new QuizSessionNotFoundException())
                .thenThrow(new QuizQuestionNotFoundException())
                .thenThrow(new QuizOptionNotFoundException())
                .thenThrow(new QuizSessionCompletedException())
                .thenThrow(new QuizAnswerAlreadySubmittedException());

        expectAnswerError(404, "SESSION_NOT_FOUND");
        expectAnswerError(404, "QUESTION_NOT_FOUND");
        expectAnswerError(404, "OPTION_NOT_FOUND");
        expectAnswerError(409, "SESSION_COMPLETED");
        expectAnswerError(409, "ANSWER_ALREADY_SUBMITTED");
    }

    @Test
    void returnsPersistedFinalResultAndRejectsPrematureResult() throws Exception {
        QuizResultResponse result = new QuizResultResponse(
                6,
                6,
                4,
                true,
                IntStream.range(0, 10)
                        .mapToObj(index -> new QuizResultDetailResponse(
                                UUID.randomUUID().toString(),
                                "Question " + (index + 1),
                                "Selected " + (index + 1),
                                "Correct " + (index + 1),
                                index < 6,
                                index == 9 ? null : "Explanation " + (index + 1)
                        ))
                        .toList()
        );
        when(quizAnswerService.getResult(SESSION_ID))
                .thenReturn(result)
                .thenThrow(new QuizIncompleteException());

        mockMvc.perform(get("/api/quiz/result").param("sessionId", SESSION_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(6))
                .andExpect(jsonPath("$.correctCount").value(6))
                .andExpect(jsonPath("$.incorrectCount").value(4))
                .andExpect(jsonPath("$.rewardEligible").value(true))
                .andExpect(jsonPath("$.details", hasSize(10)))
                .andExpect(jsonPath("$.details[0].questionId").isNotEmpty())
                .andExpect(jsonPath("$.details[0].questionText").value("Question 1"))
                .andExpect(jsonPath("$.details[0].selectedAnswer").value("Selected 1"))
                .andExpect(jsonPath("$.details[0].correctAnswer").value("Correct 1"))
                .andExpect(jsonPath("$.details[0].isCorrect").value(true))
                .andExpect(jsonPath("$.details[9].explanation")
                        .value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/quiz/result").param("sessionId", SESSION_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUIZ_INCOMPLETE"));
    }

    @Test
    void rejectsMalformedOrMissingResultSessionId() throws Exception {
        mockMvc.perform(get("/api/quiz/result"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/quiz/result").param("sessionId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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

    private String answerRequest() {
        return """
                {
                  "sessionId":"99999999-9999-9999-9999-999999999999",
                  "questionId":"11111111-1111-1111-1111-111111111111",
                  "optionId":"22222222-2222-2222-2222-222222222222"
                }
                """;
    }

    private void expectAnswerError(int statusCode, String code) throws Exception {
        mockMvc.perform(post("/api/quiz/answer")
                        .contentType("application/json")
                        .content(answerRequest()))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.code").value(code));
    }
}
