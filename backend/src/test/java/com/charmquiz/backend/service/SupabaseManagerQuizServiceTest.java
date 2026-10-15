package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.ManagerQuizImportRequest;
import com.charmquiz.backend.dto.ManagerQuizQuestionImportRequest;
import com.charmquiz.backend.dto.ManagerQuizResponse;
import com.charmquiz.backend.exception.ManagerQuizNotFoundException;
import com.charmquiz.backend.exception.ManagerUpstreamException;
import com.charmquiz.backend.exception.ManagerValidationException;
import com.charmquiz.backend.model.QuizStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseManagerQuizServiceTest {

    private static final UUID QUIZ_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID QUESTION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String SECRET = "test-secret-value";

    private MockRestServiceServer server;
    private SupabaseManagerQuizService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://project.supabase.co/rest/v1")
                .defaultHeader("apikey", SECRET);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new SupabaseManagerQuizService(builder.build(), new ObjectMapper());
    }

    @Test
    void listsAllStatusesWithExplicitColumnsAndStableOrdering() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/rest/v1/quiz");
                    assertThat(request.getURI().getQuery())
                            .isEqualTo("select=" + SupabaseManagerQuizService.QUIZ_COLUMNS
                                    + "&order=created_at.desc,id.asc")
                            .doesNotContain("status=eq.ACTIVE")
                            .doesNotContain("select=*")
                            .doesNotContain(SECRET);
                    assertThat(request.getHeaders().getFirst("apikey")).isEqualTo(SECRET);
                    assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
                })
                .andRespond(withSuccess("[" + quizRow(QUIZ_ID, "INACTIVE") + "]",
                        MediaType.APPLICATION_JSON));
        expectQuestions();
        expectOptions(false);

        List<ManagerQuizResponse> result = service.getAllQuizzes();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo(QuizStatus.INACTIVE);
        assertThat(result.getFirst().questions().getFirst().options())
                .containsExactly("A", "B", "C", "D");
        assertThat(result.getFirst().questions().getFirst().correctIndex()).isEqualTo(2);
        server.verify();
    }

    @Test
    void detailUsesExactUuidAndReturnsNotFoundForEmptyResult() {
        server.expect(request -> assertThat(request.getURI().getQuery())
                        .isEqualTo("id=eq." + QUIZ_ID + "&select="
                                + SupabaseManagerQuizService.QUIZ_COLUMNS + "&limit=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.getQuiz(QUIZ_ID))
                .isInstanceOf(ManagerQuizNotFoundException.class);
        server.verify();
    }

    @Test
    void validateNormalizesWithoutCallingSupabase() {
        service.validateImport(validRequest());
        server.verify();

        ManagerQuizImportRequest invalid = new ManagerQuizImportRequest(
                " ", null, validRequest().questions());
        assertThatThrownBy(() -> service.validateImport(invalid))
                .isInstanceOf(ManagerValidationException.class);
    }

    @Test
    void importCallsAtomicRpcWithTrimmedPayloadThenReturnsInactiveQuiz() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/rest/v1/rpc/import_manager_quiz");
                    assertThat(request.getURI().getQuery())
                            .isEqualTo("select=" + SupabaseManagerQuizService.IMPORT_RESULT_COLUMNS)
                            .doesNotContain(SECRET);
                    assertThat(request.getHeaders().getFirst("apikey")).isEqualTo(SECRET);
                })
                .andExpect(content().string(containsString("\"p_title\":\"History quiz\"")))
                .andExpect(content().string(containsString("\"p_description\":null")))
                .andExpect(content().string(containsString("\"prompt\":\"Question 1\"")))
                .andExpect(content().string(containsString("\"correctIndex\":2")))
                .andRespond(withSuccess(
                        "[{\"outcome\":\"OK\",\"quiz_id\":\"" + QUIZ_ID + "\"}]",
                        MediaType.APPLICATION_JSON));
        expectDetail(QuizStatus.INACTIVE);

        ManagerQuizResponse result = service.importQuiz(validRequest());

        assertThat(result.id()).isEqualTo(QUIZ_ID.toString());
        assertThat(result.status()).isEqualTo(QuizStatus.INACTIVE);
        server.verify();
    }

    @Test
    void invalidRpcImportOutcomeIsValidationError() {
        server.expect(request -> {
                }).andRespond(withSuccess(
                "[{\"outcome\":\"INVALID_REQUEST\",\"quiz_id\":null}]",
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.importQuiz(validRequest()))
                .isInstanceOf(ManagerValidationException.class);
        server.verify();
    }

    @Test
    void statusUsesAtomicRpcAndMapsMissingQuiz() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/rest/v1/rpc/set_manager_quiz_status");
                    assertThat(request.getURI().getQuery())
                            .isEqualTo("select=" + SupabaseManagerQuizService.IMPORT_RESULT_COLUMNS);
                })
                .andExpect(content().string(containsString("\"p_quiz_id\":\"" + QUIZ_ID + "\"")))
                .andExpect(content().string(containsString("\"p_status\":\"ACTIVE\"")))
                .andRespond(withSuccess(
                        "[{\"outcome\":\"QUIZ_NOT_FOUND\",\"quiz_id\":null}]",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.updateStatus(QUIZ_ID, QuizStatus.ACTIVE))
                .isInstanceOf(ManagerQuizNotFoundException.class);
        server.verify();
    }

    @Test
    void successfulActivationReturnsRefetchedActiveQuiz() {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/rest/v1/rpc/set_manager_quiz_status"))
                .andRespond(withSuccess(
                        "[{\"outcome\":\"OK\",\"quiz_id\":\"" + QUIZ_ID + "\"}]",
                        MediaType.APPLICATION_JSON));
        expectDetail(QuizStatus.ACTIVE);

        ManagerQuizResponse result = service.updateStatus(QUIZ_ID, QuizStatus.ACTIVE);

        assertThat(result.status()).isEqualTo(QuizStatus.ACTIVE);
        server.verify();
    }

    @Test
    void rejectsInvalidOptionOrderingCorrectnessAndRequiredData() {
        expectQuizAndQuestion();
        expectOptions(true);
        assertThatThrownBy(() -> service.getAllQuizzes())
                .isInstanceOf(ManagerUpstreamException.class)
                .hasMessageContaining("multiple correct");
        server.verify();

        server.reset();
        server.expect(request -> {
                }).andRespond(withSuccess(
                "[{\"id\":\"" + QUIZ_ID + "\",\"title\":\" \","
                        + "\"description\":null,\"status\":\"ACTIVE\","
                        + "\"created_at\":\"2026-01-01T00:00:00Z\"}]",
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.getAllQuizzes())
                .isInstanceOf(ManagerUpstreamException.class);
        server.verify();
    }

    @Test
    void malformedAndUpstreamResponsesFailClosed() {
        server.expect(request -> {
                }).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.getAllQuizzes())
                .isInstanceOf(ManagerUpstreamException.class);
        server.verify();

        server.reset();
        server.expect(request -> {
                }).andRespond(withServerError());
        assertThatThrownBy(() -> service.getAllQuizzes())
                .isInstanceOf(ManagerUpstreamException.class);
        server.verify();
    }

    private void expectDetail(QuizStatus status) {
        server.expect(request -> assertThat(request.getURI().getQuery())
                        .contains("id=eq." + QUIZ_ID).contains("limit=1"))
                .andRespond(withSuccess("[" + quizRow(QUIZ_ID, status.name()) + "]",
                        MediaType.APPLICATION_JSON));
        expectQuestions();
        expectOptions(false);
    }

    private void expectQuizAndQuestion() {
        server.expect(request -> {
                }).andRespond(withSuccess("[" + quizRow(QUIZ_ID, "ACTIVE") + "]",
                MediaType.APPLICATION_JSON));
        expectQuestions();
    }

    private void expectQuestions() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/rest/v1/quiz_question");
                    assertThat(request.getURI().getQuery())
                            .contains("quiz_id=in.(" + QUIZ_ID + ")")
                            .contains("select=" + SupabaseManagerQuizService.QUESTION_COLUMNS)
                            .contains("order=created_at.asc,id.asc")
                            .doesNotContain("status=eq.ACTIVE")
                            .doesNotContain("select=*");
                })
                .andRespond(withSuccess("[{\"id\":\"" + QUESTION_ID + "\","
                                + "\"quiz_id\":\"" + QUIZ_ID + "\","
                                + "\"question_text\":\"Question 1\","
                                + "\"explanation\":\"Because\","
                                + "\"created_at\":\"2026-01-01T00:00:00Z\"}]",
                        MediaType.APPLICATION_JSON));
    }

    private void expectOptions(boolean twoCorrect) {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/rest/v1/quiz_option");
                    assertThat(request.getURI().getQuery())
                            .contains("question_id=in.(" + QUESTION_ID + ")")
                            .contains("select=" + SupabaseManagerQuizService.OPTION_COLUMNS)
                            .contains("order=question_id.asc,option_order.asc,id.asc")
                            .doesNotContain("select=*");
                })
                .andRespond(withSuccess(optionsJson(twoCorrect), MediaType.APPLICATION_JSON));
    }

    private String quizRow(UUID id, String status) {
        return "{\"id\":\"" + id + "\",\"title\":\" History quiz \","
                + "\"description\":\" Description \",\"status\":\"" + status + "\","
                + "\"created_at\":\"2026-01-01T00:00:00Z\"}";
    }

    private String optionsJson(boolean twoCorrect) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < 4; index++) {
            if (index > 0) {
                json.append(',');
            }
            boolean correct = index == 2 || (twoCorrect && index == 1);
            json.append("{\"id\":\"")
                    .append(String.format("20000000-0000-0000-0000-%012d", index + 1))
                    .append("\",\"question_id\":\"").append(QUESTION_ID)
                    .append("\",\"option_text\":\"").append((char) ('A' + index))
                    .append("\",\"is_correct\":").append(correct)
                    .append(",\"option_order\":").append(index).append('}');
        }
        return json.append(']').toString();
    }

    private ManagerQuizImportRequest validRequest() {
        List<ManagerQuizQuestionImportRequest> questions = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            questions.add(new ManagerQuizQuestionImportRequest(
                    " Question " + index + " ",
                    List.of(" A ", " B ", " C ", " D "),
                    2,
                    " "
            ));
        }
        return new ManagerQuizImportRequest(" History quiz ", " ", questions);
    }
}
