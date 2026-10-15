package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.QuizAnswerRequest;
import com.charmquiz.backend.dto.QuizAnswerResponse;
import com.charmquiz.backend.dto.QuizResultResponse;
import com.charmquiz.backend.exception.QuizAnswerAlreadySubmittedException;
import com.charmquiz.backend.exception.QuizIncompleteException;
import com.charmquiz.backend.exception.QuizOptionNotFoundException;
import com.charmquiz.backend.exception.QuizQuestionNotFoundException;
import com.charmquiz.backend.exception.QuizSessionCompletedException;
import com.charmquiz.backend.exception.QuizSessionNotFoundException;
import com.charmquiz.backend.exception.QuizUpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseQuizAnswerServiceTest {

    private static final UUID SESSION_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID QUIZ_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String SECRET = "test-secret-value";

    private MockRestServiceServer server;
    private SupabaseQuizAnswerService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://project.supabase.co/rest/v1")
                .defaultHeader("apikey", SECRET);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new SupabaseQuizAnswerService(builder.build(), new ObjectMapper());
    }

    @Test
    void submitsCorrectAndIncorrectAnswersThroughTransactionalRpc() {
        expectSubmit("OK", true, null, 1, 1, false, null);
        QuizAnswerResponse correct = service.submitAnswer(request());
        assertThat(correct.isCorrect()).isTrue();
        assertThat(correct.correctOptionId()).isEqualTo(optionId(0, 1).toString());
        assertThat(correct.explanation()).isNull();
        server.verify();

        server.reset();
        expectSubmit("OK", false, "Explanation", 2, 1, false, null);
        QuizAnswerResponse incorrect = service.submitAnswer(request());
        assertThat(incorrect.isCorrect()).isFalse();
        assertThat(incorrect.correctOptionId()).isEqualTo(optionId(0, 1).toString());
        assertThat(incorrect.explanation()).isEqualTo("Explanation");
        server.verify();
    }

    @Test
    void mapsStructuredBusinessOutcomesWithoutParsingSqlMessages() {
        assertOutcome("SESSION_NOT_FOUND", QuizSessionNotFoundException.class);
        assertOutcome("QUESTION_NOT_FOUND", QuizQuestionNotFoundException.class);
        assertOutcome("OPTION_NOT_FOUND", QuizOptionNotFoundException.class);
        assertOutcome("SESSION_COMPLETED", QuizSessionCompletedException.class);
        assertOutcome("ANSWER_ALREADY_SUBMITTED", QuizAnswerAlreadySubmittedException.class);
    }

    @Test
    void rejectsMalformedUnknownAndInconsistentRpcResponses() {
        expectRawSubmit("not-json");
        assertThatThrownBy(() -> service.submitAnswer(request()))
                .isInstanceOf(QuizUpstreamException.class);
        server.verify();

        server.reset();
        expectRawSubmit("[{\"outcome\":\"UNKNOWN\"}]");
        assertThatThrownBy(() -> service.submitAnswer(request()))
                .isInstanceOf(QuizUpstreamException.class);
        server.verify();

        server.reset();
        expectSubmit("OK", true, null, 10, 11, true, "2026-10-15T00:00:00Z");
        assertThatThrownBy(() -> service.submitAnswer(request()))
                .isInstanceOf(QuizUpstreamException.class);
        server.verify();
    }

    @Test
    void mapsRpcTransportFailureToUpstreamError() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/rest/v1/rpc/submit_quiz_answer");
                })
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.submitAnswer(request()))
                .isInstanceOf(QuizUpstreamException.class)
                .hasMessage("Supabase quiz request failed.");
        server.verify();
    }

    @Test
    void reconstructsCompletedSixOfTenResultFromPersistedRows() {
        expectCompletedResult(6);

        QuizResultResponse result = service.getResult(SESSION_ID);

        assertThat(result.score()).isEqualTo(6);
        assertThat(result.correctCount()).isEqualTo(6);
        assertThat(result.incorrectCount()).isEqualTo(4);
        assertThat(result.rewardEligible()).isTrue();
        assertThat(result.details()).hasSize(10);
        assertThat(result.details().getFirst().selectedAnswer()).isEqualTo("Q1 Option 1");
        assertThat(result.details().getFirst().correctAnswer()).isEqualTo("Q1 Option 1");
        assertThat(result.details().get(6).isCorrect()).isFalse();
        assertThat(result.details().get(6).selectedAnswer()).isEqualTo("Q7 Option 1");
        assertThat(result.details().get(6).correctAnswer()).isEqualTo("Q7 Option 2");
        assertThat(result.details().getLast().explanation()).isNull();
        server.verify();
    }

    @Test
    void fiveOfTenIsNotRewardEligible() {
        expectCompletedResult(5);
        QuizResultResponse result = service.getResult(SESSION_ID);
        assertThat(result.score()).isEqualTo(5);
        assertThat(result.rewardEligible()).isFalse();
        server.verify();
    }

    @Test
    void rejectsMissingAndIncompleteSessionsBeforeLoadingAnswers() {
        expectSession("[]");
        assertThatThrownBy(() -> service.getResult(SESSION_ID))
                .isInstanceOf(QuizSessionNotFoundException.class);
        server.verify();

        server.reset();
        expectSession(sessionJson(0, false, null));
        assertThatThrownBy(() -> service.getResult(SESSION_ID))
                .isInstanceOf(QuizIncompleteException.class);
        server.verify();
    }

    @Test
    void rejectsIncompleteOrInconsistentPersistedResultData() {
        expectSession(sessionJson(6, true, "2026-10-15T00:00:00Z"));
        expectAnswers(answerRows(9, 6));
        assertThatThrownBy(() -> service.getResult(SESSION_ID))
                .isInstanceOf(QuizUpstreamException.class);
        server.verify();

        server.reset();
        expectSession(sessionJson(7, true, "2026-10-15T00:00:00Z"));
        expectAnswers(answerRows(10, 6));
        expectQuestions();
        expectOptions(6);
        assertThatThrownBy(() -> service.getResult(SESSION_ID))
                .isInstanceOf(QuizUpstreamException.class)
                .hasMessageContaining("inconsistent");
        server.verify();
    }

    private QuizAnswerRequest request() {
        return new QuizAnswerRequest(SESSION_ID, questionId(0), optionId(0, 0));
    }

    private void assertOutcome(
            String outcome,
            Class<? extends RuntimeException> expectedType) {
        server.reset();
        expectRawSubmit("[{\"outcome\":\"" + outcome + "\"}]");
        assertThatThrownBy(() -> service.submitAnswer(request()))
                .isInstanceOf(expectedType);
        server.verify();
    }

    private void expectSubmit(
            String outcome,
            boolean correct,
            String explanation,
            int answeredCount,
            int score,
            boolean reward,
            String completedAt) {
        String explanationJson = explanation == null ? "null" : "\"" + explanation + "\"";
        String completedJson = completedAt == null ? "null" : "\"" + completedAt + "\"";
        expectRawSubmit("""
                [{
                  "outcome":"%s",
                  "is_correct":%s,
                  "correct_option_id":"%s",
                  "explanation":%s,
                  "answered_count":%d,
                  "score":%d,
                  "reward_eligible":%s,
                  "completed_at":%s
                }]
                """.formatted(
                outcome,
                correct,
                optionId(0, 1),
                explanationJson,
                answeredCount,
                score,
                reward,
                completedJson
        ));
    }

    private void expectRawSubmit(String response) {
        server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("POST");
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/rest/v1/rpc/submit_quiz_answer");
                    assertThat(request.getURI().getQuery())
                            .isEqualTo("select=" + SupabaseQuizAnswerService.RPC_COLUMNS)
                            .doesNotContain("select=*");
                    assertThat(request.getURI().toString()).doesNotContain(SECRET);
                    assertThat(request.getHeaders().getFirst("apikey")).isEqualTo(SECRET);
                    assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
                })
                .andExpect(content().json("""
                        {
                          "p_session_id":"99999999-9999-9999-9999-999999999999",
                          "p_question_id":"00000000-0000-0000-0001-000000000001",
                          "p_selected_option_id":"00000000-0000-0000-0002-000000000001"
                        }
                        """))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString(SECRET))))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void expectCompletedResult(int correctCount) {
        expectSession(sessionJson(correctCount, correctCount >= 6, "2026-10-15T00:00:00Z"));
        expectAnswers(answerRows(10, correctCount));
        expectQuestions();
        expectOptions(correctCount);
    }

    private void expectSession(String response) {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/rest/v1/quiz_session");
                    assertThat(request.getURI().getQuery())
                            .contains("id=eq." + SESSION_ID)
                            .contains("select=" + SupabaseQuizAnswerService.SESSION_COLUMNS)
                            .contains("limit=1")
                            .doesNotContain("select=*");
                })
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void expectAnswers(String response) {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/rest/v1/quiz_answer");
                    assertThat(request.getURI().getQuery())
                            .contains("session_id=eq." + SESSION_ID)
                            .contains("select=" + SupabaseQuizAnswerService.ANSWER_COLUMNS)
                            .contains("limit=11")
                            .doesNotContain("select=*");
                })
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void expectQuestions() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/rest/v1/quiz_question");
                    assertThat(request.getURI().getQuery())
                            .contains("quiz_id=eq." + QUIZ_ID)
                            .contains("id=in.")
                            .contains("select=" + SupabaseQuizAnswerService.QUESTION_COLUMNS)
                            .contains("order=created_at.asc,id.asc")
                            .contains("limit=11")
                            .doesNotContain("select=*");
                })
                .andRespond(withSuccess(questionRows(), MediaType.APPLICATION_JSON));
    }

    private void expectOptions(int correctCount) {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/rest/v1/quiz_option");
                    assertThat(request.getURI().getQuery())
                            .contains("question_id=in.")
                            .contains("select=" + SupabaseQuizAnswerService.OPTION_COLUMNS)
                            .contains("limit=41")
                            .doesNotContain("select=*");
                })
                .andRespond(withSuccess(optionRows(correctCount), MediaType.APPLICATION_JSON));
    }

    private String sessionJson(int score, boolean reward, String completedAt) {
        String completedJson = completedAt == null ? "null" : "\"" + completedAt + "\"";
        return """
                [{
                  "id":"%s",
                  "quiz_id":"%s",
                  "score":%d,
                  "total_questions":10,
                  "reward_eligible":%s,
                  "completed_at":%s
                }]
                """.formatted(SESSION_ID, QUIZ_ID, score, reward, completedJson);
    }

    private String answerRows(int count, int correctCount) {
        return "[" + IntStream.range(0, count)
                .mapToObj(index -> """
                        {
                          "question_id":"%s",
                          "selected_option_id":"%s",
                          "is_correct":%s
                        }
                        """.formatted(questionId(index), optionId(index, 0), index < correctCount))
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private String questionRows() {
        return "[" + IntStream.range(0, 10)
                .mapToObj(index -> """
                        {
                          "id":"%s",
                          "quiz_id":"%s",
                          "question_text":"Question %d",
                          "explanation":%s,
                          "created_at":"2026-01-01T00:00:%02dZ"
                        }
                        """.formatted(
                        questionId(index),
                        QUIZ_ID,
                        index + 1,
                        index == 9 ? "null" : "\"Explanation " + (index + 1) + "\"",
                        index
                ))
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private String optionRows(int correctCount) {
        return "[" + IntStream.range(0, 10)
                .boxed()
                .flatMap(questionIndex -> IntStream.range(0, 4)
                        .mapToObj(optionIndex -> {
                            boolean correct = questionIndex < correctCount
                                    ? optionIndex == 0
                                    : optionIndex == 1;
                            return """
                                    {
                                      "id":"%s",
                                      "question_id":"%s",
                                      "option_text":"Q%d Option %d",
                                      "is_correct":%s
                                    }
                                    """.formatted(
                                    optionId(questionIndex, optionIndex),
                                    questionId(questionIndex),
                                    questionIndex + 1,
                                    optionIndex + 1,
                                    correct
                            );
                        }))
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private UUID questionId(int index) {
        return UUID.fromString("00000000-0000-0000-0001-%012d".formatted(index + 1));
    }

    private UUID optionId(int questionIndex, int optionIndex) {
        return UUID.fromString("00000000-0000-0000-0002-%012d"
                .formatted(questionIndex * 10L + optionIndex + 1));
    }
}
