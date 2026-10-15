package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.QuizSessionResponse;
import com.charmquiz.backend.exception.QuizUnavailableException;
import com.charmquiz.backend.exception.QuizUpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseQuizServiceTest {

    private static final UUID QUIZ_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SESSION_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final String SECRET = "test-secret-value";

    private MockRestServiceServer server;
    private SupabaseQuizService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://project.supabase.co/rest/v1")
                .defaultHeader("apikey", SECRET);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new SupabaseQuizService(
                builder.build(), new ObjectMapper(), Collections::reverse);
    }

    @Test
    void startsValidatedSessionUsingOneBatchOptionQueryAndShufflesOptions()
            throws Exception {
        expectActiveQuiz();
        expectQuestions(questionRows(10, false, false));
        expectBatchOptions(allValidOptions());
        expectSessionInsert(SESSION_ID);

        QuizSessionResponse result = service.startSession();

        assertThat(UUID.fromString(result.sessionId())).isEqualTo(SESSION_ID);
        assertThat(result.questions()).hasSize(10);
        assertThat(result.questions()).extracting(question -> question.id())
                .doesNotHaveDuplicates();
        assertThat(result.questions()).allSatisfy(question -> {
            assertThat(question.prompt()).isNotBlank();
            assertThat(question.options()).hasSize(4);
            assertThat(question.options()).extracting(option -> option.id())
                    .doesNotHaveDuplicates();
        });
        assertThat(result.questions().getFirst().options())
                .extracting(option -> option.label())
                .containsExactly("Option 4", "Option 3", "Option 2", "Option 1");
        assertThat(new ObjectMapper().writeValueAsString(result))
                .doesNotContain("correct", "is_correct", "isCorrect");
        server.verify();
    }

    @Test
    void zeroOrMultipleActiveQuizzesFailClosed() {
        server.expect(request -> assertActiveQuizQuery(request.getURI().getQuery()))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(service::startSession)
                .isInstanceOf(QuizUnavailableException.class);
        server.verify();

        server.reset();
        server.expect(request -> assertActiveQuizQuery(request.getURI().getQuery()))
                .andRespond(withSuccess("""
                        [
                          {"id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"},
                          {"id":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"}
                        ]
                        """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(service::startSession)
                .isInstanceOf(QuizUnavailableException.class);
        server.verify();
    }

    @Test
    void nineOrElevenActiveQuestionsFailBeforeSessionInsert() {
        for (int count : List.of(9, 11)) {
            server.reset();
            expectActiveQuiz();
            expectQuestions(questionRows(count, false, false));

            assertThatThrownBy(service::startSession)
                    .isInstanceOf(QuizUnavailableException.class);
            server.verify();
        }
    }

    @Test
    void duplicateQuestionIdsAndBlankPromptsFailBeforeOptionLookupOrInsert() {
        expectActiveQuiz();
        expectQuestions(questionRows(10, true, false));
        assertThatThrownBy(service::startSession)
                .isInstanceOf(QuizUnavailableException.class);
        server.verify();

        server.reset();
        expectActiveQuiz();
        expectQuestions(questionRows(10, false, true));
        assertThatThrownBy(service::startSession)
                .isInstanceOf(QuizUnavailableException.class);
        server.verify();
    }

    @Test
    void threeOrFiveOptionsFailBeforeSessionInsert() {
        for (int count : List.of(3, 5)) {
            server.reset();
            expectActiveQuiz();
            expectQuestions(questionRows(10, false, false));
            expectBatchOptions(optionsWithFirstQuestion(
                    optionRows(0, count, false, false, false)));

            assertThatThrownBy(service::startSession)
                    .isInstanceOf(QuizUnavailableException.class);
            server.verify();
        }
    }

    @Test
    void unknownQuestionIdInBatchResponseFailsBeforeSessionInsert() {
        String options = allValidOptions();
        String withUnknownQuestion = options.substring(0, options.length() - 1)
                + "," + optionJson(
                        UUID.fromString("00000000-0000-0000-0003-000000000001"),
                        questionId(10),
                        "Unknown question option")
                + "]";

        assertInvalidOptions(withUnknownQuestion);
    }

    @Test
    void duplicateOptionIdsAndBlankOptionsFailBeforeSessionInsert() {
        assertInvalidOptions(optionsWithFirstQuestion(
                optionRows(0, 4, true, false, false)));
        assertInvalidOptions(optionsWithFirstQuestion(
                optionRows(0, 4, false, false, true)));
    }

    @Test
    void malformedAndTransportFailuresMapToUpstreamError() {
        server.expect(request -> {
                }).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(service::startSession)
                .isInstanceOf(QuizUpstreamException.class);
        server.verify();

        server.reset();
        server.expect(request -> {
                }).andRespond(withServerError());
        assertThatThrownBy(service::startSession)
                .isInstanceOf(QuizUpstreamException.class)
                .hasMessage("Supabase quiz request failed.");
        server.verify();
    }

    @Test
    void eachStartInsertsAndReturnsANewSession() {
        UUID secondSessionId =
                UUID.fromString("88888888-8888-8888-8888-888888888888");
        expectSuccessfulStart(SESSION_ID);
        expectSuccessfulStart(secondSessionId);

        QuizSessionResponse first = service.startSession();
        QuizSessionResponse second = service.startSession();

        assertThat(first.sessionId()).isNotEqualTo(second.sessionId());
        server.verify();
    }

    private void assertInvalidOptions(String optionsJson) {
        server.reset();
        expectActiveQuiz();
        expectQuestions(questionRows(10, false, false));
        expectBatchOptions(optionsJson);
        assertThatThrownBy(service::startSession)
                .isInstanceOf(QuizUnavailableException.class);
        server.verify();
    }

    private void expectSuccessfulStart(UUID sessionId) {
        expectActiveQuiz();
        expectQuestions(questionRows(10, false, false));
        expectBatchOptions(allValidOptions());
        expectSessionInsert(sessionId);
    }

    private void expectActiveQuiz() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/rest/v1/quiz");
                    assertActiveQuizQuery(request.getURI().getQuery());
                    assertThat(request.getURI().toString()).doesNotContain(SECRET);
                    assertThat(request.getHeaders().getFirst("apikey")).isEqualTo(SECRET);
                    assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
                })
                .andRespond(withSuccess("[{\"id\":\"" + QUIZ_ID + "\"}]",
                        MediaType.APPLICATION_JSON));
    }

    private void assertActiveQuizQuery(String query) {
        assertThat(query)
                .isEqualTo("status=eq.ACTIVE&select=id&limit=2")
                .doesNotContain("select=*");
    }

    private void expectQuestions(String rows) {
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/rest/v1/quiz_question");
                    assertThat(request.getURI().getQuery())
                            .contains("quiz_id=eq." + QUIZ_ID)
                            .contains("status=eq.ACTIVE")
                            .contains("select=" + SupabaseQuizService.QUESTION_COLUMNS)
                            .contains("order=created_at.asc,id.asc")
                            .contains("limit=11")
                            .doesNotContain("select=*");
                })
                .andRespond(withSuccess(rows, MediaType.APPLICATION_JSON));
    }

    private void expectBatchOptions(String rows) {
        server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("GET");
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/rest/v1/quiz_option");
                    assertThat(request.getURI().getQuery())
                            .contains("question_id=in.(" + questionIdFilter() + ")")
                            .contains("select=" + SupabaseQuizService.OPTION_COLUMNS)
                            .doesNotContain("is_correct")
                            .doesNotContain("get_quiz_options")
                            .doesNotContain("select=*");
                })
                .andRespond(withSuccess(rows, MediaType.APPLICATION_JSON));
    }

    private void expectSessionInsert(UUID sessionId) {
        server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("POST");
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/rest/v1/quiz_session");
                    assertThat(request.getURI().getQuery()).isEqualTo("select=id");
                    assertThat(request.getHeaders().getFirst("Prefer"))
                            .isEqualTo("return=representation");
                })
                .andExpect(content().json("""
                        {
                          "quiz_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                          "total_questions": 10,
                          "score": 0,
                          "reward_eligible": false
                        }
                        """))
                .andExpect(content().string(not(containsString(SECRET))))
                .andRespond(withSuccess("[{\"id\":\"" + sessionId + "\"}]",
                        MediaType.APPLICATION_JSON));
    }

    private String questionRows(int count, boolean duplicateLastId, boolean blankLastPrompt) {
        return "[" + IntStream.range(0, count)
                .mapToObj(index -> {
                    UUID id = duplicateLastId && index == count - 1
                            ? questionId(0)
                            : questionId(index);
                    String prompt = blankLastPrompt && index == count - 1
                            ? "   "
                            : "Question " + (index + 1);
                    return """
                            {
                              "id":"%s",
                              "question_text":"%s",
                              "created_at":"2026-01-01T00:00:%02dZ"
                            }
                            """.formatted(id, prompt, index);
                })
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private String validOptions(int questionIndex) {
        return "[" + IntStream.range(0, 4)
                .mapToObj(optionIndex -> optionJson(
                        optionId(questionIndex, optionIndex),
                        questionId(questionIndex),
                        "Option " + (optionIndex + 1)
                ))
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private String allValidOptions() {
        return "[" + IntStream.range(0, 10)
                .mapToObj(index -> withoutArrayBrackets(validOptions(index)))
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private String optionsWithFirstQuestion(String firstQuestionOptions) {
        String remaining = IntStream.range(1, 10)
                .mapToObj(index -> withoutArrayBrackets(validOptions(index)))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return "[" + withoutArrayBrackets(firstQuestionOptions)
                + "," + remaining + "]";
    }

    private String withoutArrayBrackets(String jsonArray) {
        return jsonArray.substring(1, jsonArray.length() - 1);
    }

    private String questionIdFilter() {
        return IntStream.range(0, 10)
                .mapToObj(index -> questionId(index).toString())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String optionRows(
            int questionIndex,
            int count,
            boolean duplicateLastId,
            boolean wrongQuestion,
            boolean blankLastText) {
        return "[" + IntStream.range(0, count)
                .mapToObj(optionIndex -> optionJson(
                        duplicateLastId && optionIndex == count - 1
                                ? optionId(questionIndex, 0)
                                : optionId(questionIndex, optionIndex),
                        wrongQuestion && optionIndex == count - 1
                                ? questionId(1)
                                : questionId(questionIndex),
                        blankLastText && optionIndex == count - 1
                                ? "   "
                                : "Option " + (optionIndex + 1)
                ))
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private String optionJson(UUID optionId, UUID questionId, String text) {
        return """
                {
                  "id":"%s",
                  "question_id":"%s",
                  "option_text":"%s"
                }
                """.formatted(optionId, questionId, text);
    }

    private UUID questionId(int index) {
        return UUID.fromString("00000000-0000-0000-0001-%012d".formatted(index + 1));
    }

    private UUID optionId(int questionIndex, int optionIndex) {
        return UUID.fromString("00000000-0000-0000-0002-%012d"
                .formatted(questionIndex * 10L + optionIndex + 1));
    }
}
